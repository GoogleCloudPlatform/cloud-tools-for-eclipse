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

import com.google.cloud.tools.eclipse.appengine.facets.AppEngineFlexFacet;
import com.google.cloud.tools.eclipse.appengine.facets.FacetUtil;
import com.google.cloud.tools.eclipse.appengine.libraries.model.LibraryFile;
import com.google.cloud.tools.eclipse.appengine.libraries.model.MavenCoordinates;
import com.google.cloud.tools.eclipse.appengine.libraries.repository.M2RepositoryService;
import com.google.cloud.tools.eclipse.appengine.newproject.AppEngineProjectConfig;
import com.google.cloud.tools.eclipse.appengine.newproject.CodeTemplates;
import com.google.cloud.tools.eclipse.appengine.newproject.CreateAppEngineWtpProject;
import com.google.cloud.tools.eclipse.appengine.newproject.Messages;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.artifact.Artifact;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jst.common.project.facet.core.JavaFacet;
import org.eclipse.jst.j2ee.web.project.facet.WebFacetUtils;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;

/**
 * Utility to make a new App Engine Flexible Eclipse project.
 */
public class CreateAppEngineFlexWtpProject extends CreateAppEngineWtpProject {
  private static final Logger logger = Logger.getLogger(CreateAppEngineFlexWtpProject.class.getName());
  private static final Map<String, String> PROJECT_DEPENDENCIES;
  static {
    Map<String, String> projectDependencies = new HashMap<String, String>();
    projectDependencies.put("javax.servlet", "servlet-api");
    PROJECT_DEPENDENCIES = Collections.unmodifiableMap(projectDependencies);
  }

  CreateAppEngineFlexWtpProject(AppEngineProjectConfig config, IAdaptable uiInfoAdapter) {
    super(config, uiInfoAdapter);
  }

  @Override
  public void addAppEngineFacet(IProject newProject, IProgressMonitor monitor) throws CoreException {
    SubMonitor subMonitor = SubMonitor.convert(monitor,
        Messages.getString("add.appengine.flex.facet"), 100);

    IFacetedProject facetedProject = ProjectFacetsManager.create(
        newProject, true /* convertIfNecessary */, subMonitor.newChild(50));
    AppEngineFlexFacet.installAppEngineFacet(
        facetedProject, true /* installDependentFacets */, subMonitor.newChild(50));
  }

  @Override
  public String getDescription() {
    return Messages.getString("creating.app.engine.flex.project"); //$NON-NLS-1$
  }

  @Override
  public IFile createAndConfigureProjectContent(IProject newProject, AppEngineProjectConfig config,
      IProgressMonitor monitor) throws CoreException {
    SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
    IFile mostImportantFile =  CodeTemplates.materializeAppEngineFlexFiles(newProject, config,
        subMonitor.newChild(30));
    configureFacets(newProject, subMonitor.newChild(20));
    addDependenciesToProject(newProject, subMonitor.newChild(50));
    return mostImportantFile;
  }

  /**
   * Add Java 8 and Dynamic Web Module facet
   */
  private void configureFacets(IProject project, IProgressMonitor monitor) throws CoreException {
    SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
    IFacetedProject facetedProject = ProjectFacetsManager.create(
        project, true /* convertIfNecessary */, subMonitor.newChild(50));
    Set<IFacetedProject.Action> facetInstallSet = new HashSet<>();
    FacetUtil.addJavaFacetToBatch(JavaFacet.VERSION_1_8, facetedProject, facetInstallSet);
    FacetUtil.addWebFacetToBatch(WebFacetUtils.WEB_30, facetedProject, facetInstallSet);
    FacetUtil.addFacetSetToProject(facetedProject, facetInstallSet, subMonitor.newChild(50));
  }

  private void addDependenciesToProject(IProject project, IProgressMonitor monitor)
      throws CoreException {
    SubMonitor subMonitor = SubMonitor.convert(monitor, 100);

    // Create a lib folder
    IFolder libFolder = project.getFolder("lib");
    if (!libFolder.exists()) {
      libFolder.create(true, true, subMonitor.newChild(10));
    }

    // Download the dependencies from maven
    M2RepositoryService repoService = new M2RepositoryService();
    repoService.activate();
    int ticks = 50 / PROJECT_DEPENDENCIES.size();
    for (Map.Entry<String, String> dependency : PROJECT_DEPENDENCIES.entrySet()) {
      LibraryFile libraryFile = new LibraryFile(new MavenCoordinates(dependency.getKey(),
          dependency.getValue()));
      Artifact artifact = null;
      try {
        artifact = repoService.resolveArtifact(libraryFile, subMonitor.newChild(ticks));
      } catch (CoreException ex) {
        logger.log(Level.WARNING, "Error downloading " +
      libraryFile.getMavenCoordinates().toString() + " from maven", ex);
        continue;
      }

      // Copy dependency from local maven repo into lib folder
      if (artifact != null) {
        File artifactFile = artifact.getFile();
        IFile destFile = libFolder.getFile(artifactFile.getName());
        try {
          destFile.create(new FileInputStream(artifactFile), true, subMonitor.newChild(30));
        } catch (FileNotFoundException ex) {
          logger.log(Level.WARNING, "Error copying over " + artifactFile.toString() + " to " +
        libFolder.getFullPath().toPortableString(), ex);
          continue;
        }
      }
    }

    addDependenciesToClasspath(project, libFolder.getLocation().toString(), subMonitor.newChild(10));
  }

  private void addDependenciesToClasspath(IProject project, String libraryPath,
      IProgressMonitor monitor)  throws CoreException {
    IJavaProject javaProject = JavaCore.create(project);
    IClasspathEntry[] entries = javaProject.getRawClasspath();
    List<IClasspathEntry> newEntries = new ArrayList<IClasspathEntry>();
    newEntries.addAll(Arrays.asList(entries));

    // Add all the jars under lib folder to the classpath
    File libFolder = new File(libraryPath);

    for(File file : libFolder.listFiles()) {
      IPath path = Path.fromOSString(file.toPath().toString());
      newEntries.add(JavaCore.newLibraryEntry(path, null, null));
    }

    javaProject.setRawClasspath(newEntries.toArray(new IClasspathEntry[0]), monitor);
  }

}
