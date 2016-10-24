/*******************************************************************************
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package com.google.cloud.tools.eclipse.appengine.libraries;

import com.google.cloud.tools.eclipse.appengine.libraries.model.Filter;
import com.google.cloud.tools.eclipse.appengine.libraries.model.Library;
import com.google.cloud.tools.eclipse.appengine.libraries.model.LibraryFactory;
import com.google.cloud.tools.eclipse.appengine.libraries.model.LibraryFactoryException;
import com.google.cloud.tools.eclipse.appengine.libraries.model.LibraryFile;
import com.google.cloud.tools.eclipse.appengine.libraries.persistence.LibraryClasspathContainerSerializer;
import com.google.cloud.tools.eclipse.appengine.libraries.repository.ILibraryRepositoryService;
import com.google.cloud.tools.eclipse.appengine.libraries.repository.LibraryRepositoryServiceException;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.RegistryFactory;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jst.j2ee.classpathdep.UpdateClasspathAttributeUtil;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

public class AppEngineLibraryContainerResolverJob extends Job {
  //TODO duplicate of com.google.cloud.tools.eclipse.appengine.libraries.AppEngineLibraryContainerInitializer.LIBRARIES_EXTENSION_POINT
  public static final String LIBRARIES_EXTENSION_POINT = "com.google.cloud.tools.eclipse.appengine.libraries"; //$NON-NLS-1$

  private static final Logger logger = Logger.getLogger(AppEngineLibraryContainerResolverJob.class.getName());

  private Map<String, Library> libraries;
  private final IJavaProject javaProject;
  private LibraryClasspathContainerSerializer serializer;
  private ServiceReference<ILibraryRepositoryService> serviceReference = null;

  private ILibraryRepositoryService repositoryService;

  public AppEngineLibraryContainerResolverJob(String name, IJavaProject javaProject) {
    this(name, javaProject, new LibraryClasspathContainerSerializer());
  }

  @VisibleForTesting
  AppEngineLibraryContainerResolverJob(String name, IJavaProject javaProject,
                                       LibraryClasspathContainerSerializer serializer) {
    super(name);
    Preconditions.checkNotNull(javaProject, "javaProject is null"); //$NON-NLS-1$
    Preconditions.checkNotNull(serializer);
    this.javaProject = javaProject;
    this.serializer = serializer;
    setUser(true);
    setRule(javaProject.getSchedulingRule());
  }

  @Override
  protected IStatus run(IProgressMonitor monitor) {
    // TODO parse library definition in ILibraryConfigService (or similar) started when the plugin/bundle starts
    // https://github.com/GoogleCloudPlatform/google-cloud-eclipse/issues/856
    try {
      if (libraries == null) {
        // in tests libraries will be initialized via the test constructor, this would override mocks/stubs.
        IConfigurationElement[] configurationElements =
            RegistryFactory.getRegistry().getConfigurationElementsFor(LIBRARIES_EXTENSION_POINT);
        initializeLibraries(configurationElements, new LibraryFactory());
      }
      serviceReference = lookupRepositoryServiceReference();
      repositoryService = getBundleContext().getService(serviceReference);

      IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
      SubMonitor subMonitor = SubMonitor.convert(monitor,
                                                 Messages.TaskResolveLibraries,
                                                 getTotalwork(rawClasspath));
      for (int i = 0; i < rawClasspath.length; i++) {
        IClasspathEntry classpathEntry = rawClasspath[i];
        String libraryId = classpathEntry.getPath().segment(1);
        Library library = libraries.get(libraryId);
        if (library != null) {
          LibraryClasspathContainer container = resolveLibraryFiles(classpathEntry,
                                                                    library, subMonitor.newChild(1));
          JavaCore.setClasspathContainer(classpathEntry.getPath(), new IJavaProject[] {javaProject},
                                         new IClasspathContainer[] {container}, null);
          serializer.saveContainer(javaProject, container);
        }
      }
    } catch (LibraryRepositoryServiceException | CoreException | IOException ex) {
      return StatusUtil.error(this, Messages.TaskResolveLibrariesError, ex);
    } finally {
      releaseRepositoryService();
    }
    return Status.OK_STATUS;
  }

  private LibraryClasspathContainer resolveLibraryFiles(IClasspathEntry classpathEntry,
                                                        Library library,
                                                        IProgressMonitor monitor) 
                                                            throws CoreException, LibraryRepositoryServiceException {
    List<LibraryFile> libraryFiles = library.getLibraryFiles();
    SubMonitor subMonitor = SubMonitor.convert(monitor, libraryFiles.size());
    subMonitor.subTask(NLS.bind(Messages.TaskResolveArtifacts, getLibraryDescription(library)));
    SubMonitor child = subMonitor.newChild(libraryFiles.size());

    IClasspathEntry[] entries = new IClasspathEntry[libraryFiles.size()];
    int idx = 0;
    for (LibraryFile libraryFile : libraryFiles) {
      IClasspathAttribute[] libraryFileClasspathAttributes = getClasspathAttributes(libraryFile);
      entries[idx++] =
          JavaCore.newLibraryEntry(repositoryService.getJarLocation(libraryFile.getMavenCoordinates()),
                                   getSourceLocation(libraryFile),
                                   null,
                                   getAccessRules(libraryFile.getFilters()),
                                   libraryFileClasspathAttributes,
                                   true);
      child.worked(1);
    }
    monitor.done();
    LibraryClasspathContainer container = new LibraryClasspathContainer(classpathEntry.getPath(),
                                                                        getLibraryDescription(library),
                                                                        entries);
    return container;
  }

  private static int getTotalwork(IClasspathEntry[] rawClasspath) {
    int sum = 0;
    for (IClasspathEntry element : rawClasspath) {
      if (isLibraryClasspathEntry(element.getPath())) {
        ++sum;
      }
    }
    return sum;
  }

  private static boolean isLibraryClasspathEntry(IPath path) {
    return path != null && path.segmentCount() == 2 && Library.CONTAINER_PATH_PREFIX.equals(path.segment(0));
  }

  private static String getLibraryDescription(Library library) {
    if (!Strings.isNullOrEmpty(library.getName())) {
      return library.getName();
    } else {
      return library.getId();
    }
  }

  private static IClasspathAttribute[] getClasspathAttributes(LibraryFile libraryFile) throws CoreException {
    IClasspathAttribute[] libraryFileClasspathAttributes;
    if (libraryFile.isExport()) {
      libraryFileClasspathAttributes =
          new IClasspathAttribute[] { UpdateClasspathAttributeUtil.createDependencyAttribute(true /* isWebApp */) };
    } else {
      libraryFileClasspathAttributes =
          new IClasspathAttribute[] { UpdateClasspathAttributeUtil.createNonDependencyAttribute() };
    }
    return libraryFileClasspathAttributes;
  }

  private IPath getSourceLocation(LibraryFile libraryFile) {
    if (libraryFile.getSourceUri() == null) {
      return repositoryService.getSourceJarLocation(libraryFile.getMavenCoordinates());
    } else {
      // download the file and return path to it
      // TODO https://github.com/GoogleCloudPlatform/google-cloud-eclipse/issues/800
      return new Path("/downloaded/source/file"); //$NON-NLS-1$
    }
  }

  private void initializeLibraries(IConfigurationElement[] configurationElements, LibraryFactory libraryFactory) {
    libraries = new HashMap<>(configurationElements.length);
    for (IConfigurationElement configurationElement : configurationElements) {
      try {
        Library library = libraryFactory.create(configurationElement);
        libraries.put(library.getId(), library);
      } catch (LibraryFactoryException exception) {
        logger.log(Level.SEVERE, "Failed to initialize libraries", exception); //$NON-NLS-1$
      }
    }
  }

  private static IAccessRule[] getAccessRules(List<Filter> filters) {
    IAccessRule[] accessRules = new IAccessRule[filters.size()];
    int idx = 0;
    for (Filter filter : filters) {
      if (filter.isExclude()) {
        IAccessRule accessRule = JavaCore.newAccessRule(new Path(filter.getPattern()), IAccessRule.K_NON_ACCESSIBLE);
        accessRules[idx++] = accessRule;
      } else {
        IAccessRule accessRule = JavaCore.newAccessRule(new Path(filter.getPattern()), IAccessRule.K_ACCESSIBLE);
        accessRules[idx++] = accessRule;
      }
    }
    return accessRules;
  }

  private ServiceReference<ILibraryRepositoryService> lookupRepositoryServiceReference() {
    BundleContext bundleContext = getBundleContext();
    ServiceReference<ILibraryRepositoryService> serviceReference =
        bundleContext.getServiceReference(ILibraryRepositoryService.class);
    return serviceReference;
  }

  private void releaseRepositoryService() {
    repositoryService = null;
    getBundleContext().ungetService(serviceReference);
  }

  private BundleContext getBundleContext() {
    BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
    if (bundleContext == null) {
      throw new IllegalStateException(Messages.BundleContextNotFound); 
    } else {
      return bundleContext;
    }
  }
}