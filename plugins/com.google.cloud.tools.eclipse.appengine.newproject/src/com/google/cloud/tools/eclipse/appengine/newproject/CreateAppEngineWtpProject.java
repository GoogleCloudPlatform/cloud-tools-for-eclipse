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

package com.google.cloud.tools.eclipse.appengine.newproject;

import com.google.cloud.tools.eclipse.appengine.facets.WebProjectUtil;
import com.google.cloud.tools.eclipse.appengine.libraries.BuildPath;
import com.google.cloud.tools.eclipse.appengine.libraries.model.Library;
import com.google.cloud.tools.eclipse.appengine.libraries.model.LibraryFile;
import com.google.cloud.tools.eclipse.appengine.libraries.model.MavenCoordinates;
import com.google.cloud.tools.eclipse.appengine.libraries.repository.ILibraryRepositoryService;
import com.google.cloud.tools.eclipse.util.ClasspathUtil;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.artifact.Artifact;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jst.j2ee.classpathdep.UpdateClasspathAttributeUtil;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.ResolverConfiguration;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.ide.undo.CreateProjectOperation;
import org.eclipse.wst.common.componentcore.internal.builder.DependencyGraphImpl;
import org.eclipse.wst.common.componentcore.internal.builder.IDependencyGraph;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;

/**
* Utility to make a new Eclipse App Engine project in the workspace.
*/
public abstract class CreateAppEngineWtpProject extends WorkspaceModifyOperation {

  private static final Logger logger = Logger.getLogger(CreateAppEngineWtpProject.class.getName());

  protected final ILibraryRepositoryService repositoryService;

  private final AppEngineProjectConfig config;
  private final IAdaptable uiInfoAdapter;
  private IFile mostImportant = null;

  @VisibleForTesting
  Job deployAssemblyEntryRemoveJob;

  public abstract void addAppEngineFacet(IFacetedProject newProject, IProgressMonitor monitor)
      throws CoreException;

  /**
   * Returns a user visible name for the resource operation that generates the files
   * for the App Engine WTP project.
   */
  public abstract String getDescription();

  /**
   * Returns the most important file created that the IDE will open in the editor.
   */
  public abstract IFile createAndConfigureProjectContent(IProject newProject,
      AppEngineProjectConfig config, IProgressMonitor monitor) throws CoreException;

  /**
   * @return the file in the project that should be opened in an editor when the wizard finishes;
   *     may be null
   */
  public IFile getMostImportant() {
    return mostImportant;
  }

  protected CreateAppEngineWtpProject(AppEngineProjectConfig config,
      IAdaptable uiInfoAdapter, ILibraryRepositoryService repositoryService) {
    if (config == null) {
      throw new NullPointerException("Null App Engine configuration"); //$NON-NLS-1$
    }
    this.config = config;
    this.uiInfoAdapter = uiInfoAdapter;
    this.repositoryService = repositoryService;
  }

  @Override
  public void execute(IProgressMonitor monitor) throws InvocationTargetException, CoreException {
    IWorkspace workspace = ResourcesPlugin.getWorkspace();
    IProject newProject = config.getProject();
    URI location = config.getEclipseProjectLocationUri();

    String name = newProject.getName();
    IProjectDescription description = workspace.newProjectDescription(name);
    description.setLocationURI(location);

    String operationLabel = getDescription();
    SubMonitor subMonitor = SubMonitor.convert(monitor, operationLabel, 120);
    CreateProjectOperation operation = new CreateProjectOperation(description, operationLabel);
    try {
      operation.execute(subMonitor.split(10), uiInfoAdapter);
      mostImportant = createAndConfigureProjectContent(newProject, config, subMonitor.split(80));
    } catch (ExecutionException ex) {
      throw new InvocationTargetException(ex);
    }

    IFacetedProject facetedProject = ProjectFacetsManager.create(
        newProject, true /* convertIfNecessary */, subMonitor.split(5));
    addAppEngineFacet(facetedProject, subMonitor.split(6));

    addAdditionalDependencies(newProject, config, subMonitor.split(20));

    fixTestSourceDirectorySettings(newProject, subMonitor.split(5));
  }

  protected void addAdditionalDependencies(IProject newProject, AppEngineProjectConfig config,
      IProgressMonitor monitor) throws CoreException {
    SubMonitor progress = SubMonitor.convert(monitor, 12);
    if (config.getUseMaven()) {
      enableMavenNature(newProject, progress.split(5));
      BuildPath.addMavenLibraries(newProject, config.getAppEngineLibraries(), progress.split(7));
    } else {
      addJunit4ToClasspath(newProject, progress.split(2));
      addJstl12ToClasspath(newProject, progress.split(2));
      IJavaProject javaProject = JavaCore.create(newProject);
      
      List<Library> libraries = config.getAppEngineLibraries();
      BuildPath.addNativeLibrary(javaProject, libraries, progress.split(8));
    }
  }

  private void fixTestSourceDirectorySettings(IProject newProject, IProgressMonitor monitor)
      throws CoreException {
    // 1. Fix the output folder of "src/test/java".
    IPath testSourcePath = newProject.getFolder("src/test/java").getFullPath();

    IJavaProject javaProject = JavaCore.create(newProject);
    IClasspathEntry[] entries = javaProject.getRawClasspath();
    for (int i = 0; i < entries.length; i++) {
      IClasspathEntry entry = entries[i];
      if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE
          && entry.getPath().equals(testSourcePath)
          && entry.getOutputLocation() == null) {  // Default output location?
        IPath oldOutputPath = javaProject.getOutputLocation();
        IPath newOutputPath = oldOutputPath.removeLastSegments(1).append("test-classes");

        entries[i] = JavaCore.newSourceEntry(testSourcePath, ClasspathEntry.INCLUDE_ALL,
            ClasspathEntry.EXCLUDE_NONE, newOutputPath);
        javaProject.setRawClasspath(entries, monitor);
        break;
      }
    }

    // 2. Remove "src/test/java" from the Web Deployment Assembly sources.
    deployAssemblyEntryRemoveJob =
        new DeployAssemblyEntryRemoveJob(newProject, new Path("src/test/java"));
    deployAssemblyEntryRemoveJob.setSystem(true);
    deployAssemblyEntryRemoveJob.schedule();
  }

  private static void enableMavenNature(IProject newProject, IProgressMonitor monitor)
      throws CoreException {
    SubMonitor subMonitor = SubMonitor.convert(monitor, 30);

    // Workaround deadlock bug described in Eclipse bug (https://bugs.eclipse.org/511793).
    try {
      IDependencyGraph.INSTANCE.preUpdate();
      try {
        Job.getJobManager().join(DependencyGraphImpl.GRAPH_UPDATE_JOB_FAMILY,
            subMonitor.split(8));
      } catch (OperationCanceledException | InterruptedException ex) {
        logger.log(Level.WARNING, "Exception waiting for WTP Graph Update job", ex);
      }

      ResolverConfiguration resolverConfiguration = new ResolverConfiguration();
      MavenPlugin.getProjectConfigurationManager().enableMavenNature(newProject,
          resolverConfiguration, subMonitor.split(20));
    } finally {
      IDependencyGraph.INSTANCE.postUpdate();
    }

    // M2E will cleverly set "target/<artifact ID>-<version>/WEB-INF/classes" as a new Java output
    // folder; delete the default old folder.
    newProject.getFolder("build").delete(true /* force */, subMonitor.split(2));
  }

  private static void addJunit4ToClasspath(IProject newProject, IProgressMonitor monitor)
      throws CoreException {
    IClasspathAttribute nonDependencyAttribute =
        UpdateClasspathAttributeUtil.createNonDependencyAttribute();
    IClasspathEntry junit4Container = JavaCore.newContainerEntry(
        JUnitCore.JUNIT4_CONTAINER_PATH,
        new IAccessRule[0],
        new IClasspathAttribute[] {nonDependencyAttribute},
        false);
    ClasspathUtil.addClasspathEntry(newProject, junit4Container, monitor);
  }

  private void addJstl12ToClasspath(IProject newProject, IProgressMonitor monitor)
      throws CoreException {
    SubMonitor subMonitor = SubMonitor.convert(monitor, 15);

    // locate WEB-INF/lib
    IFolder libFolder =
        WebProjectUtil.createFolderInWebInf(newProject, new Path("lib"), subMonitor.split(5)); //$NON-NLS-1$
    MavenCoordinates jstl = new MavenCoordinates.Builder()
        .setGroupId("jstl") //$NON-NLS-1$
        .setArtifactId("jstl") //$NON-NLS-1$
        .setVersion("1.2").build(); //$NON-NLS-1$
    installArtifact(jstl, libFolder, subMonitor.split(10));
  }

  /**
   * Download and install the given dependency in the provided folder. Returns the resulting file,
   * or {@code null} if it was unable to install the file.
   */
  protected IFile installArtifact(MavenCoordinates dependency, IFolder destination,
      IProgressMonitor monitor) {
    SubMonitor progress = SubMonitor.convert(monitor, 10);
    LibraryFile libraryFile = new LibraryFile(dependency);
    File artifactFile = null;
    try {
      Artifact artifact = repositoryService.resolveArtifact(libraryFile, progress.split(5));
      artifactFile = artifact.getFile();
      IFile destinationFile = destination.getFile(artifactFile.getName());
      destinationFile.create(Files.newInputStream(artifactFile.toPath()), true,
          progress.split(5));
      return destinationFile;
    } catch (CoreException ex) {
      logger.log(Level.WARNING, "Error downloading " + //$NON-NLS-1$
          libraryFile.getMavenCoordinates().toString() + " from maven", ex); //$NON-NLS-1$
    } catch (IOException ex) {
      logger.log(Level.WARNING, "Error copying over " + artifactFile.toString() + " to " + //$NON-NLS-1$ //$NON-NLS-2$
          destination.getFullPath().toPortableString(), ex);
    }
    return null;
  }


}
