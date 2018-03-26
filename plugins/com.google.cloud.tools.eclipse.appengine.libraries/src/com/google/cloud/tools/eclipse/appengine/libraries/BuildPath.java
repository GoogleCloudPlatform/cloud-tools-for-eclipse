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

import com.google.cloud.tools.eclipse.appengine.libraries.model.CloudLibraries;
import com.google.cloud.tools.eclipse.appengine.libraries.model.Library;
import com.google.cloud.tools.eclipse.appengine.libraries.model.LibraryFile;
import com.google.cloud.tools.eclipse.appengine.libraries.persistence.LibraryClasspathContainerSerializer;
import com.google.cloud.tools.eclipse.usagetracker.AnalyticsEvents;
import com.google.cloud.tools.eclipse.util.ClasspathUtil;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jst.j2ee.classpathdep.UpdateClasspathAttributeUtil;
import org.osgi.framework.FrameworkUtil;
import org.xml.sax.SAXException;

public class BuildPath {

  private static final Logger logger = Logger.getLogger(BuildPath.class.getName());
  
  /**
   * Add the selected libraries to the project's Maven's dependencies.
   */
  public static void addMavenLibraries(IProject project, List<Library> selected,
      IProgressMonitor monitor) throws CoreException {
    updateMavenLibraries(project, selected, Collections.<Library>emptyList(), monitor);
  }

  /**
   * Update the project's Maven dependencies to the selected libraries and remove any traces of
   * removed libraries.
   */
  public static void updateMavenLibraries(IProject project, Collection<Library> selectedLibraries,
      Collection<Library> removedLibraries, IProgressMonitor monitor) throws CoreException {
    Preconditions.checkNotNull(selectedLibraries);
    Preconditions.checkNotNull(removedLibraries);
    if (selectedLibraries.isEmpty() && removedLibraries.isEmpty()) {
      return; // nothing to do
    }
    AnalyticsLibraryPingHelper.sendLibrarySelectionPing(AnalyticsEvents.MAVEN_PROJECT,
        selectedLibraries);

    IFile pomFile = project.getFile("pom.xml"); //$NON-NLS-1$
    try {
      Pom pom = Pom.parse(pomFile);
      pom.updateDependencies(selectedLibraries, removedLibraries);
    } catch (SAXException | IOException ex) {
      IStatus status = StatusUtil.error(BuildPath.class, ex.getMessage(), ex);
      throw new CoreException(status);
    }
  }

  /** Return the libraries whose definitions are matched by the project's pom's dependencies. */
  public static Collection<Library> loadMavenLibraries(IJavaProject javaProject,
      Collection<Library> availableLibraries, IProgressMonitor monitor)
      throws CoreException {
    IProject project = javaProject.getProject();
    IFile pomFile = project.getFile("pom.xml"); //$NON-NLS-1$

    try {
      Pom pom = Pom.parse(pomFile);
      return pom.resolveLibraries(availableLibraries);
    } catch (SAXException | IOException ex) {
      IStatus status = StatusUtil.error(BuildPath.class, ex.getMessage(), ex);
      throw new CoreException(status);
    }
  }

  public static void addNativeLibrary(IJavaProject javaProject,
      List<Library> libraries, IProgressMonitor monitor) throws CoreException {
    if (libraries.isEmpty()) {
      return;
    }
    AnalyticsLibraryPingHelper.sendLibrarySelectionPing(AnalyticsEvents.NATIVE_PROJECT, libraries);

    SubMonitor subMonitor = SubMonitor.convert(monitor,
        Messages.getString("adding.app.engine.libraries"), //$NON-NLS-1$
        25);
    
    Library masterLibrary = collectLibraryFiles(javaProject, libraries, subMonitor.split(8));
    IClasspathEntry masterEntry = computeEntry(javaProject, masterLibrary, subMonitor.split(8));
    saveLibraryList(javaProject, libraries, subMonitor.split(1));

    if (masterEntry != null) {
      ClasspathUtil.addClasspathEntry(javaProject.getProject(), masterEntry,
          subMonitor.split(8));
    }
    runContainerResolverJob(javaProject);
  }

  /**
   * Adds all jars and dependencies from <code>libraries</code> to the master library.
   * 
   * @return the master library
   */
  public static Library collectLibraryFiles(IJavaProject javaProject, List<Library> libraries,
      IProgressMonitor monitor) {
    
    SubMonitor subMonitor = SubMonitor.convert(monitor,
        Messages.getString("calculating.dependencies"), //$NON-NLS-1$
        10 + libraries.size());
    
    SortedSet<LibraryFile> masterFiles = new TreeSet<>();
    List<String> dependentIds = new ArrayList<>();
    for (Library library : libraries) {
      dependentIds.add(library.getId());
      masterFiles.addAll(library.getAllDependencies());
      subMonitor.worked(1);
    }

    // need to get old master library entries first if they exist
    try {
      LibraryClasspathContainerSerializer serializer = new LibraryClasspathContainerSerializer();
      List<String> previouslyAddedLibraries = serializer.loadLibraryIds(javaProject);
      for (String id : previouslyAddedLibraries) {
        Library library = CloudLibraries.getLibrary(id);
        // null happens mostly in tests but could also be null
        // if someone edited the serialized data behind Eclipse's back
        if (library != null && !dependentIds.contains(id)) { 
          dependentIds.add(library.getId());
          masterFiles.addAll(library.getAllDependencies());
          subMonitor.worked(1);
        }
      }
    } catch (IOException | CoreException ex) {
      logger.log(Level.WARNING, "Error loading previous libraries", ex); //$NON-NLS-1$
    }
    
    Library masterLibrary = new Library(CloudLibraries.MASTER_CONTAINER_ID);
    masterLibrary.setName(Messages.getString("google.cloud.platform.libraries")); //$NON-NLS-1$
    masterLibrary.setLibraryDependencies(dependentIds);
    subMonitor.worked(1);
    
    List<LibraryFile> resolved = Library.resolveDuplicates(new ArrayList<>(masterFiles));
    subMonitor.worked(8);
    
    masterLibrary.setLibraryFiles(resolved);
    
    masterLibrary.setResolved();
    subMonitor.worked(1);

    return masterLibrary;
  }

  /**
   * Returns the entry of {@code library} to be added to the classpath of {@code javaProject}, if
   * the project does not already have it. (Note that this does not add it to the classpath.)
   * Returns {@code null} otherwise.
   */
  public static IClasspathEntry computeEntry(IJavaProject javaProject, Library library,
      IProgressMonitor monitor) throws CoreException {
    SubMonitor subMonitor = SubMonitor.convert(monitor, Messages.getString("computing.entries"), 1); //$NON-NLS-1$
    
    IClasspathEntry libraryContainer = makeClasspathEntry(library);
    subMonitor.worked(1);

    IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
    boolean alreadyExists = Arrays.asList(rawClasspath).contains(libraryContainer);
    return alreadyExists ? null : libraryContainer;
  }
  
  private static IClasspathEntry makeClasspathEntry(Library library) throws CoreException {
    IClasspathAttribute[] classpathAttributes = new IClasspathAttribute[1];
    if (library.isExport()) {
      boolean isWebApp = true;
      classpathAttributes[0] = UpdateClasspathAttributeUtil.createDependencyAttribute(isWebApp);
    } else {
      classpathAttributes[0] = UpdateClasspathAttributeUtil.createNonDependencyAttribute();
    }
    // in practice, this method will only be called for the master library
    IPath containerPath = new Path(LibraryClasspathContainer.CONTAINER_PATH_PREFIX)
        .append(library.getId());
    return JavaCore.newContainerEntry(containerPath, new IAccessRule[0], classpathAttributes,
        false);
  }

  public static void runContainerResolverJob(IJavaProject javaProject) {
    IEclipseContext context = EclipseContextFactory.getServiceContext(
        FrameworkUtil.getBundle(BuildPath.class).getBundleContext());
    final IEclipseContext childContext =
        context.createChild(LibraryClasspathContainerResolverJob.class.getName());
    childContext.set(IJavaProject.class, javaProject);
    Job job =
        ContextInjectionFactory.make(LibraryClasspathContainerResolverJob.class, childContext);
    job.addJobChangeListener(new JobChangeAdapter() {
      @Override
      public void done(IJobChangeEvent event) {
        childContext.dispose();
      }
    });
    job.schedule();
  }

  /**
   * Load the list of library dependencies saved for this project.
   */
  public static List<Library> loadLibraryList(IJavaProject project, IProgressMonitor monitor)
      throws CoreException {
    SubMonitor progress = SubMonitor.convert(monitor, 10);
    LibraryClasspathContainerSerializer serializer = new LibraryClasspathContainerSerializer();
    List<String> savedLibraryIds;
    try {
      savedLibraryIds = serializer.loadLibraryIds(project);
      progress.worked(3);
    } catch (IOException ex) {
      throw new CoreException(
          StatusUtil.error(BuildPath.class, "Error retrieving project library list", ex)); //$NON-NLS-1$
    }
    List<Library> selectedLibraries = new ArrayList<>();
    progress.setWorkRemaining(savedLibraryIds.size());
    for (String libraryId : savedLibraryIds) {
      Library library = CloudLibraries.getLibrary(libraryId);
      if (library != null) {
        selectedLibraries.add(library);
      }
      progress.worked(1);
    }
    return selectedLibraries;
  }

  /**
   * Save the list of library dependencies for this project.
   */
  public static void saveLibraryList(IJavaProject project, List<Library> libraries,
      IProgressMonitor monitor) throws CoreException {
    SubMonitor progress = SubMonitor.convert(monitor, libraries.size() + 10);
    LibraryClasspathContainerSerializer serializer = new LibraryClasspathContainerSerializer();
    List<String> libraryIds = new ArrayList<>();
    for (Library library : libraries) {
      libraryIds.add(library.getId());
      progress.worked(1);
    }
    try {
      serializer.saveLibraryIds(project, libraryIds);
      progress.worked(5);
      // in practice, we only ever use the master-container
      IPath containerPath = new Path(LibraryClasspathContainer.CONTAINER_PATH_PREFIX)
          .append(CloudLibraries.MASTER_CONTAINER_ID);
      serializer.resetContainer(project, containerPath);
      progress.worked(5);
    } catch (IOException ex) {
      throw new CoreException(
          StatusUtil.error(BuildPath.class, "Error saving project library list", ex)); //$NON-NLS-1$
    }
  }

  /**
   * Check that the library list is still required (i.e., the classpath container may have been
   * removed).
   */
  public static void checkLibraryList(IJavaProject javaProject, IProgressMonitor monitor) {
    SubMonitor progress = SubMonitor.convert(monitor, 5);
    LibraryClasspathContainerSerializer serializer = new LibraryClasspathContainerSerializer();
    if (!serializer.hasLibraryIds(javaProject)) {
      // nothing to do since no library list file found
      return;
    }
    progress.worked(1);

    try {
      // Check if we can find our master library container
      IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
      progress.worked(2);
      IPath containerPath = new Path(LibraryClasspathContainer.CONTAINER_PATH_PREFIX)
          .append(CloudLibraries.MASTER_CONTAINER_ID);
      for (IClasspathEntry entry : rawClasspath) {
        if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER
            && containerPath.equals(entry.getPath())) {
          // container found, so library list should stay
          return;
        }
      }
      progress.worked(1);

      serializer.removeLibraryIds(javaProject);
      serializer.resetContainer(javaProject, containerPath);
      progress.worked(1);
    } catch (JavaModelException ex) {
      logger.log(Level.WARNING,
          "Unable to check classpath containers for: " + javaProject.getProject().getName());
      return;
    } catch (CoreException ex) {
      logger.log(Level.SEVERE, "Unable to remove library ids", ex);
    }
  }

}
