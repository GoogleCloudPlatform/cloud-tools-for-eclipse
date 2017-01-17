/*
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
 */

package com.google.cloud.tools.eclipse.appengine.libraries;

import com.google.cloud.tools.eclipse.appengine.libraries.model.Filter;
import com.google.cloud.tools.eclipse.appengine.libraries.model.Library;
import com.google.cloud.tools.eclipse.appengine.libraries.model.LibraryFactory;
import com.google.cloud.tools.eclipse.appengine.libraries.model.LibraryFactoryException;
import com.google.cloud.tools.eclipse.appengine.libraries.model.LibraryFile;
import com.google.cloud.tools.eclipse.appengine.libraries.persistence.LibraryClasspathContainerSerializer;
import com.google.cloud.tools.eclipse.appengine.libraries.repository.ILibraryRepositoryService;
import com.google.cloud.tools.eclipse.appengine.libraries.repository.LibraryRepositoryServiceException;
import com.google.cloud.tools.eclipse.appengine.libraries.repository.MavenCoordinatesHelper;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.artifact.Artifact;
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
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

public class AppEngineLibraryContainerResolver {
  //TODO duplicate of com.google.cloud.tools.eclipse.appengine.libraries.AppEngineLibraryContainerInitializer.LIBRARIES_EXTENSION_POINT
  public static final String LIBRARIES_EXTENSION_POINT = "com.google.cloud.tools.eclipse.appengine.libraries"; //$NON-NLS-1$

  private static final String CLASSPATH_ATTRIBUTE_SOURCE_URL =
      "com.google.cloud.tools.eclipse.appengine.libraries.sourceUrl";
  private static final Logger logger = Logger.getLogger(AppEngineLibraryContainerResolver.class.getName());

  private Map<String, Library> libraries;

  private ILibraryRepositoryService repositoryService;
  private LibraryClasspathContainerSerializer serializer = new LibraryClasspathContainerSerializer();
  private IJavaProject javaProject;

  public AppEngineLibraryContainerResolver(IJavaProject javaProject) {
    Preconditions.checkNotNull(javaProject, "javaProject is null");
    this.javaProject = javaProject;
    ServiceReference<ILibraryRepositoryService> serviceReference = FrameworkUtil.getBundle(this.getClass()).getBundleContext().getServiceReference(ILibraryRepositoryService.class);
    repositoryService = FrameworkUtil.getBundle(this.getClass()).getBundleContext().getService(serviceReference);
    //TODO release service
    //TODO make this a service
  }

  public IStatus resolveAll(IProgressMonitor monitor) {
    // TODO parse library definition in ILibraryConfigService (or similar) started when the plugin/bundle starts
    // https://github.com/GoogleCloudPlatform/google-cloud-eclipse/issues/856
    IStatus status = null;
    try {
      if (libraries == null) {
        // in tests libraries will be initialized via the test constructor, this would override mocks/stubs.
        IConfigurationElement[] configurationElements =
            RegistryFactory.getRegistry().getConfigurationElementsFor(LIBRARIES_EXTENSION_POINT);
        initializeLibraries(configurationElements, new LibraryFactory());
      }
      IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
      SubMonitor subMonitor = SubMonitor.convert(monitor,
                                                 Messages.TaskResolveLibraries,
                                                 getTotalwork(rawClasspath));
      for (IClasspathEntry classpathEntry : rawClasspath) {
        StatusUtil.merge(status, resolveContainer(classpathEntry.getPath(), subMonitor.newChild(1)));
      }
    } catch (CoreException ex) {
      return StatusUtil.error(this, Messages.TaskResolveLibrariesError, ex);
    }
    return status == null ? Status.OK_STATUS : status;
  }

  public IClasspathEntry[] resolveLibraryAttachSourcesSync(String libraryId) throws CoreException, LibraryRepositoryServiceException {
    if (libraries == null) {
      // in tests libraries will be initialized via the test constructor, this would override mocks/stubs.
      IConfigurationElement[] configurationElements =
          RegistryFactory.getRegistry().getConfigurationElementsFor(LIBRARIES_EXTENSION_POINT);
      initializeLibraries(configurationElements, new LibraryFactory());
    }
    Library library = libraries.get(libraryId);
    if (library != null) {
      IClasspathEntry[] resolvedEntries = new IClasspathEntry[library.getLibraryFiles().size()];
      int idx = 0;
      for (LibraryFile libraryFile : library.getLibraryFiles()) {
        resolvedEntries[idx++] = resolveLibraryFileAttachSourceSync(libraryFile);
      }
      return resolvedEntries;
    } else {
      throw new LibraryRepositoryServiceException("Invalid libraryId");
    }
  }

  public IStatus resolveContainer(IPath continerPath, IProgressMonitor monitor) {
    try {
      if (libraries == null) {
        // in tests libraries will be initialized via the test constructor, this would override mocks/stubs.
        IConfigurationElement[] configurationElements =
            RegistryFactory.getRegistry().getConfigurationElementsFor(LIBRARIES_EXTENSION_POINT);
        initializeLibraries(configurationElements, new LibraryFactory());
      }
    String libraryId = continerPath.segment(1);
    Library library = libraries.get(libraryId);
    if (library != null) {
      LibraryClasspathContainer container = resolveLibraryFiles(continerPath, library, monitor);
      JavaCore.setClasspathContainer(continerPath, new IJavaProject[] {javaProject},
          new IClasspathContainer[] {container}, null);
      serializer.saveContainer(javaProject, container);
    }
    return Status.OK_STATUS;
    } catch (LibraryRepositoryServiceException | CoreException | IOException ex) {
      return StatusUtil.error(this, "Could not resolve container path: " + continerPath, ex);
    }
  }
  
  private LibraryClasspathContainer resolveLibraryFiles(final IPath containerPath,
                                                        Library library,
                                                        final IProgressMonitor monitor)
                                                            throws LibraryRepositoryServiceException, CoreException {
    List<LibraryFile> libraryFiles = library.getLibraryFiles();
    SubMonitor subMonitor = SubMonitor.convert(monitor, libraryFiles.size());
    subMonitor.subTask(NLS.bind(Messages.TaskResolveArtifacts, getLibraryDescription(library)));
    SubMonitor child = subMonitor.newChild(libraryFiles.size());

    IClasspathEntry[] entries = new IClasspathEntry[libraryFiles.size()];
    int idx = 0;
    for (final LibraryFile libraryFile : libraryFiles) {
      IClasspathEntry newLibraryEntry = resolveLibraryFileAttachSourceAsync(containerPath, monitor, libraryFile);
      entries[idx++] = newLibraryEntry;
      child.worked(1);
    }
    monitor.done();
    LibraryClasspathContainer container = new LibraryClasspathContainer(containerPath,
        getLibraryDescription(library),
        entries);
    return container;
  }

  private IClasspathEntry resolveLibraryFileAttachSourceAsync(IPath containerPath, IProgressMonitor monitor,
      LibraryFile libraryFile) throws CoreException, LibraryRepositoryServiceException {
    return resolveLibraryFile(containerPath, monitor, libraryFile, true);
  }

  private IClasspathEntry resolveLibraryFileAttachSourceSync(final LibraryFile libraryFile) throws CoreException, LibraryRepositoryServiceException {
    return resolveLibraryFile(null, null, libraryFile, false);
  }

  private IClasspathEntry resolveLibraryFile(final IPath containerPath,
      final IProgressMonitor monitor, final LibraryFile libraryFile, boolean sourceAsync)
      throws CoreException, LibraryRepositoryServiceException {
    final Artifact artifact = repositoryService.resolveArtifact(libraryFile, monitor);
    IPath libraryPath = new Path(artifact.getFile().getAbsolutePath());
    IPath sourceAttachmentPath = null;
    if (sourceAsync) {
      Job job = new SourceAttacherJob(javaProject, containerPath, libraryPath, new Callable<IPath>() {
  
        @Override
        public IPath call() throws Exception {
          return repositoryService.resolveSourceArtifact(libraryFile, artifact.getVersion(), monitor);
        }
      });
      job.schedule();
    } else {
      sourceAttachmentPath = repositoryService.resolveSourceArtifact(libraryFile, artifact.getVersion(), monitor);
    }
    final IClasspathEntry newLibraryEntry = JavaCore.newLibraryEntry(libraryPath,
        sourceAttachmentPath,
        null /*  sourceAttachmentRootPath */,
        getAccessRules(libraryFile.getFilters()),
        getClasspathAttributes(libraryFile, artifact),
        true /* isExported */);
    return newLibraryEntry;
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
      int accessRuleKind = filter.isExclude() ? IAccessRule.K_NON_ACCESSIBLE : IAccessRule.K_ACCESSIBLE;
      accessRules[idx++] = JavaCore.newAccessRule(new Path(filter.getPattern()), accessRuleKind);
    }
    return accessRules;
  }

  private static IClasspathAttribute[] getClasspathAttributes(LibraryFile libraryFile, Artifact artifact)
      throws LibraryRepositoryServiceException {
    try {
      List<IClasspathAttribute> attributes =
          MavenCoordinatesHelper.createClasspathAttributes(libraryFile.getMavenCoordinates(),
              artifact.getVersion());
      if (libraryFile.isExport()) {
        attributes.add(UpdateClasspathAttributeUtil.createDependencyAttribute(true /* isWebApp */));
      } else {
        attributes.add(UpdateClasspathAttributeUtil.createNonDependencyAttribute());
      }
      if (libraryFile.getSourceUri() != null) {
        addUriAttribute(attributes, CLASSPATH_ATTRIBUTE_SOURCE_URL, libraryFile.getSourceUri());
      }
      if (libraryFile.getJavadocUri() != null) {
        addUriAttribute(attributes, IClasspathAttribute.JAVADOC_LOCATION_ATTRIBUTE_NAME,
            libraryFile.getJavadocUri());
      }
      return attributes.toArray(new IClasspathAttribute[0]);
    } catch (CoreException ex) {
      throw new LibraryRepositoryServiceException("Could not create classpath attributes", ex);
    }
  }

  private static void addUriAttribute(List<IClasspathAttribute> attributes, String attributeName, URI uri) {
    try {
      attributes.add(JavaCore.newClasspathAttribute(attributeName, uri.toURL().toString()));
    } catch (MalformedURLException | IllegalArgumentException ex) {
      // disregard invalid URL
    }
  }
}
