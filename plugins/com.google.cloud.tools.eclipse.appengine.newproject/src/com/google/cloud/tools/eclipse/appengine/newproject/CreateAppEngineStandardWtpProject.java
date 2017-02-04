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

import com.google.cloud.tools.eclipse.appengine.facets.AppEngineStandardFacet;
import com.google.cloud.tools.eclipse.appengine.libraries.LibraryClasspathContainerResolverJob;
import com.google.cloud.tools.eclipse.appengine.libraries.model.Library;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
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
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jst.j2ee.classpathdep.UpdateClasspathAttributeUtil;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.ide.undo.CreateProjectOperation;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;
import org.osgi.framework.FrameworkUtil;

/**
* Utility to make a new Eclipse project with the App Engine Standard facets in the workspace.
*/
class CreateAppEngineStandardWtpProject extends WorkspaceModifyOperation {

  private final AppEngineStandardProjectConfig config;
  private final IAdaptable uiInfoAdapter;
  private IFile mostImportant = null;

  /**
   * @return the file in the project that should be opened in an editor when the wizard finishes;
   *     may be null
   */
  IFile getMostImportant() {
    return mostImportant;
  }

  CreateAppEngineStandardWtpProject(AppEngineStandardProjectConfig config,
      IAdaptable uiInfoAdapter) {
    if (config == null) {
      throw new NullPointerException("Null App Engine configuration"); //$NON-NLS-1$
    }
    this.config = config;
    this.uiInfoAdapter = uiInfoAdapter;
  }

  @Override
  public void execute(IProgressMonitor monitor) throws InvocationTargetException, CoreException {
    IWorkspace workspace = ResourcesPlugin.getWorkspace();
    IProject newProject = config.getProject();
    URI location = config.getEclipseProjectLocationUri();

    String name = newProject.getName();
    IProjectDescription description = workspace.newProjectDescription(name);
    description.setLocationURI(location);
    SubMonitor subMonitor = SubMonitor.convert(monitor,
        Messages.getString("creating.app.engine.standard.project"), 100); //$NON-NLS-1$
    CreateProjectOperation operation = new CreateProjectOperation(
        description, Messages.getString("creating.new.app.engine.standard.project")); //$NON-NLS-1$
    try {
      operation.execute(subMonitor.newChild(10), uiInfoAdapter);
      mostImportant = CodeTemplates.materialize(newProject, config, subMonitor.newChild(80));
    } catch (ExecutionException ex) {
      throw new InvocationTargetException(ex);
    }

    IFacetedProject facetedProject = ProjectFacetsManager.create(
        newProject, true, subMonitor.newChild(2));
    AppEngineStandardFacet.installAppEngineFacet(
        facetedProject, true /* installDependentFacets */, subMonitor.newChild(2));

    addAppEngineLibrariesToBuildPath(newProject, config.getAppEngineLibraries(),
        subMonitor.newChild(2));

    addJunit4ToClasspath(subMonitor.newChild(2), newProject);
  }

  private static void addAppEngineLibrariesToBuildPath(IProject newProject,
                                                List<Library> libraries,
                                                IProgressMonitor monitor) throws CoreException {
    if (libraries.isEmpty()) {
      return;
    }
    SubMonitor subMonitor = SubMonitor.convert(monitor,
        Messages.getString("adding.app.engine.libraries"), libraries.size()); //$NON-NLS-1$
    IJavaProject javaProject = JavaCore.create(newProject);
    IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
    IClasspathEntry[] newRawClasspath =
        Arrays.copyOf(rawClasspath, rawClasspath.length + libraries.size());
    for (int i = 0; i < libraries.size(); i++) {
      Library library = libraries.get(i);
      IClasspathAttribute[] classpathAttributes;
      if (library.isExport()) {
        classpathAttributes = new IClasspathAttribute[] {
            UpdateClasspathAttributeUtil.createDependencyAttribute(true /* isWebApp */)};
      } else {
        classpathAttributes =
            new IClasspathAttribute[] {UpdateClasspathAttributeUtil.createNonDependencyAttribute()};
      }

      IClasspathEntry libraryContainer = JavaCore.newContainerEntry(library.getContainerPath(),
                                                                    new IAccessRule[0],
                                                                    classpathAttributes,
                                                                    false);
      newRawClasspath[rawClasspath.length + i] = libraryContainer;
      subMonitor.worked(1);
    }
    javaProject.setRawClasspath(newRawClasspath, monitor);

    runContainerResolverJob(javaProject);
  }

  private static void runContainerResolverJob(IJavaProject javaProject) {
    IEclipseContext context = EclipseContextFactory.getServiceContext(
        FrameworkUtil.getBundle(CreateAppEngineStandardWtpProject.class).getBundleContext());
    final IEclipseContext childContext =
        context.createChild(LibraryClasspathContainerResolverJob.class.getName());
    childContext.set(IJavaProject.class, javaProject);
    Job job = ContextInjectionFactory.make(LibraryClasspathContainerResolverJob.class, childContext);
    job.addJobChangeListener(new JobChangeAdapter() {
      @Override
      public void done(IJobChangeEvent event) {
        childContext.dispose();
      }
    });
    job.schedule();
  }

  private static void addJunit4ToClasspath(IProgressMonitor monitor, final IProject newProject)
      throws CoreException {
    IJavaProject javaProject = JavaCore.create(newProject);
    IClasspathAttribute nonDependencyAttribute =
        UpdateClasspathAttributeUtil.createNonDependencyAttribute();
    IClasspathEntry junit4Container = JavaCore.newContainerEntry(
        JUnitCore.JUNIT4_CONTAINER_PATH,
        new IAccessRule[0],
        new IClasspathAttribute[] {nonDependencyAttribute},
        false);
    IClasspathEntry[] rawClasspath = javaProject.getRawClasspath();
    IClasspathEntry[] newRawClasspath = Arrays.copyOf(rawClasspath, rawClasspath.length + 1);
    newRawClasspath[newRawClasspath.length - 1] = junit4Container;
    javaProject.setRawClasspath(newRawClasspath, monitor);
  }

}
