/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.eclipse.appengine.newproject.flex;

import com.google.cloud.tools.eclipse.appengine.facets.AppEngineFlexWarFacet;
import com.google.cloud.tools.eclipse.appengine.libraries.model.MavenCoordinates;
import com.google.cloud.tools.eclipse.appengine.libraries.repository.ILibraryRepositoryService;
import com.google.cloud.tools.eclipse.appengine.newproject.AppEngineProjectConfig;
import com.google.cloud.tools.eclipse.appengine.newproject.CodeTemplates;
import com.google.cloud.tools.eclipse.appengine.newproject.CreateAppEngineWtpProject;
import com.google.cloud.tools.eclipse.appengine.newproject.Messages;
import com.google.cloud.tools.eclipse.util.ClasspathUtil;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jst.j2ee.classpathdep.UpdateClasspathAttributeUtil;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;

/**
 * Utility to create a new App Engine Flexible Eclipse project.
 */
public class CreateAppEngineFlexWtpProject extends CreateAppEngineWtpProject {

  private static final List<MavenCoordinates> SERVLET_DEPENDENCIES;

  static {
    // servlet-api and jsp-api are marked as not being included
    MavenCoordinates servletApi = new MavenCoordinates.Builder()
        .setGroupId("javax.servlet") //$NON-NLS-1$
        .setArtifactId("javax.servlet-api") //$NON-NLS-1$
        .setVersion("3.1.0") //$NON-NLS-1$
        .build();
    MavenCoordinates jsp = new MavenCoordinates.Builder().setGroupId("javax.servlet.jsp") //$NON-NLS-1$
        .setArtifactId("javax.servlet.jsp-api") //$NON-NLS-1$
        .setVersion("2.3.1") //$NON-NLS-1$
        .build();
    SERVLET_DEPENDENCIES = ImmutableList.of(servletApi, jsp);
  }

  CreateAppEngineFlexWtpProject(AppEngineProjectConfig config, IAdaptable uiInfoAdapter,
      ILibraryRepositoryService repositoryService) {
    super(config, uiInfoAdapter, repositoryService);
  }

  @Override
  public void addAppEngineFacet(IFacetedProject newProject, IProgressMonitor monitor)
      throws CoreException {
    SubMonitor subMonitor = SubMonitor.convert(monitor,
        Messages.getString("add.appengine.flex.war.facet"), 100);

    AppEngineFlexWarFacet.installAppEngineFacet(
        newProject, true /* installDependentFacets */, subMonitor.split(100));
  }

  @Override
  public String getDescription() {
    return Messages.getString("creating.app.engine.flex.project"); //$NON-NLS-1$
  }

  @Override
  public IFile createAndConfigureProjectContent(IProject newProject, AppEngineProjectConfig config,
      IProgressMonitor monitor) throws CoreException {
    IFile mostImportantFile =  CodeTemplates.materializeAppEngineFlexFiles(newProject, config,
        monitor);
    return mostImportantFile;
  }

  @Override
  protected void addAdditionalDependencies(IProject newProject, AppEngineProjectConfig config,
      IProgressMonitor monitor) throws CoreException {
    super.addAdditionalDependencies(newProject, config, monitor);

    if (config.getUseMaven()) {
      return;
    }

    SubMonitor subMonitor = SubMonitor.convert(monitor, 100);

    // Create a lib folder
    IFolder libFolder = newProject.getFolder("lib"); //$NON-NLS-1$
    if (!libFolder.exists()) {
      libFolder.create(true, true, subMonitor.split(10));
    }

    // Download the dependencies from maven
    subMonitor.setWorkRemaining(SERVLET_DEPENDENCIES.size() + 10);
    for (MavenCoordinates dependency : SERVLET_DEPENDENCIES) {
      installArtifact(dependency, libFolder, subMonitor.split(1));
    }

    addDependenciesToClasspath(newProject, libFolder, subMonitor.split(10));
  }

  private static void addDependenciesToClasspath(IProject project, IFolder folder,
      IProgressMonitor monitor)  throws CoreException {
    List<IClasspathEntry> newEntries = new ArrayList<>();

    IClasspathAttribute[] nonDependencyAttribute =
        new IClasspathAttribute[] {UpdateClasspathAttributeUtil.createNonDependencyAttribute()};

    // Add all the jars under lib folder to the classpath
    File libFolder = folder.getLocation().toFile();
    for (File file : libFolder.listFiles()) {
      IPath path = Path.fromOSString(file.toPath().toString());
      newEntries.add(JavaCore.newLibraryEntry(path, null, null, new IAccessRule[0],
          nonDependencyAttribute, false /* isExported */));
    }

    ClasspathUtil.addClasspathEntries(project, newEntries, monitor);
  }

}
