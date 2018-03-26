/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.eclipse.appengine.facets;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jst.common.project.facet.core.JavaFacet;
import org.eclipse.jst.common.project.facet.core.JavaFacetInstallConfig;
import org.eclipse.jst.j2ee.project.facet.IJ2EEFacetInstallDataModelProperties;
import org.eclipse.jst.j2ee.project.facet.IJ2EEModuleFacetInstallDataModelProperties;
import org.eclipse.jst.j2ee.web.project.facet.IWebFacetInstallDataModelProperties;
import org.eclipse.jst.j2ee.web.project.facet.WebFacetInstallDataModelProvider;
import org.eclipse.jst.j2ee.web.project.facet.WebFacetUtils;
import org.eclipse.wst.common.componentcore.internal.builder.DependencyGraphImpl;
import org.eclipse.wst.common.componentcore.internal.builder.IDependencyGraph;
import org.eclipse.wst.common.frameworks.datamodel.DataModelFactory;
import org.eclipse.wst.common.frameworks.datamodel.IDataModel;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.IFacetedProjectBase;
import org.eclipse.wst.common.project.facet.core.IFacetedProjectWorkingCopy;
import org.eclipse.wst.common.project.facet.core.IProjectFacet;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;

/**
 * Utility class for processing facets.
 */
@SuppressWarnings("restriction") // For IDependencyGraph
public class FacetUtil {
  private static final Logger logger = Logger.getLogger(FacetUtil.class.getName());

  private IFacetedProject facetedProject;
  @VisibleForTesting
  Set<IFacetedProject.Action> facetInstallSet = new HashSet<>();

  public FacetUtil(IFacetedProject facetedProject) {
    Preconditions.checkNotNull(facetedProject, "facetedProject is null");
    this.facetedProject = facetedProject;
  }

  /**
   * Create a Java configuration for the provided project.
   */
  public static JavaFacetInstallConfig createJavaDataModel(IProject project) {
    JavaFacetInstallConfig javaConfig = new JavaFacetInstallConfig();
    List<IPath> sourcePaths = new ArrayList<>();

    // TODO: https://github.com/GoogleCloudPlatform/google-cloud-eclipse/issues/490
    if (project.getFolder("src/main/java").exists()) {
      sourcePaths.add(new Path("src/main/java"));
    }

    if (project.getFolder("src/test/java").exists()) {
      sourcePaths.add(new Path("src/test/java"));
    }

    javaConfig.setSourceFolders(sourcePaths);
    return javaConfig;
  }

  /**
   * Configures and adds an install action for {@code javaFacet} to the list of actions performed
   * when {@link FacetUtil#install(IProgressMonitor)} is called, if {@code javaFacet} does not
   * already exist in the configured project.
   *
   * @param javaFacet the java Facet to be installed
   */
  public FacetUtil addJavaFacetToBatch(IProjectFacetVersion javaFacet) {
    Preconditions.checkNotNull(javaFacet, "javaFacet is null");
    Preconditions.checkArgument(JavaFacet.FACET.getId().equals(javaFacet.getProjectFacet().getId()),
        javaFacet.toString() + " is not a Java facet");

    if (facetedProject.hasProjectFacet(JavaFacet.FACET)) {
      if (javaFacet.compareTo(facetedProject.getProjectFacetVersion(JavaFacet.FACET)) <= 0) {
        return this;
      }
      facetInstallSet.add(
          new IFacetedProject.Action(IFacetedProject.Action.Type.VERSION_CHANGE, javaFacet, null));
    } else {
      JavaFacetInstallConfig javaConfig = createJavaDataModel(facetedProject.getProject());
      facetInstallSet.add(
          new IFacetedProject.Action(IFacetedProject.Action.Type.INSTALL, javaFacet, javaConfig));
    }
    return this;
  }

  /**
   * Configures and adds an install action for {@code webFacet} to the list of actions performed
   * when {@link FacetUtil#install(IProgressMonitor)} is called, if {@code webFacet} does not
   * already exist in the configured project.
   *
   * @param webFacet the web Facet to be installed
   */
  public FacetUtil addWebFacetToBatch(IProjectFacetVersion webFacet) {
    Preconditions.checkNotNull(webFacet, "webFacet is null");
    Preconditions.checkArgument(
        WebFacetUtils.WEB_FACET.getId().equals(webFacet.getProjectFacet().getId()),
        webFacet.toString() + " is not a Web facet");

    if (facetedProject.hasProjectFacet(WebFacetUtils.WEB_FACET)) {
      if (webFacet.compareTo(facetedProject.getProjectFacetVersion(WebFacetUtils.WEB_FACET)) <= 0) {
        return this;
      }
      facetInstallSet.add(
          new IFacetedProject.Action(IFacetedProject.Action.Type.VERSION_CHANGE, webFacet, null));
    } else {
      // Determine the default location for the WEB-INF, if not already present
      String webAppDirectory = "src/main/webapp";
      if (overlapsWithJavaSourcePaths(facetedProject, Path.fromPortableString(webAppDirectory))) {
        // e.g., traditional Eclipse layout with just src/
        logger.info("Default webapp directory overlaps source directory; using WebContent");
        webAppDirectory = "WebContent"; // WTP's default
      }
      IPath webAppDirectoryFound = findMainWebAppDirectory(facetedProject.getProject());
      if (webAppDirectoryFound != null) {
        webAppDirectory = webAppDirectoryFound.toString();
      }

      IDataModel webModel = createWebFacetDataModel(webAppDirectory);
      facetInstallSet
          .add(new IFacetedProject.Action(IFacetedProject.Action.Type.INSTALL, webFacet, webModel));
    }
    return this;
  }

  /**
   * Create a Dynamic Web Facet configuration. The {@code webappFolder} is the location that holds
   * the {@code WEB-INF/web.xml}.
   */
  public static IDataModel createWebFacetDataModel(IContainer webAppFolder) {
    return createWebFacetDataModel(webAppFolder.getProjectRelativePath().toString());
  }

  /**
   * Create a Dynamic Web Facet configuration.
   */
  public static IDataModel createWebFacetDataModel(String webAppFolder) {
    IDataModel webModel = DataModelFactory.createDataModel(new WebFacetInstallDataModelProvider());
    webModel.setBooleanProperty(IJ2EEModuleFacetInstallDataModelProperties.INSTALL_EAR_LIBRARY,
        false);
    webModel.setBooleanProperty(IJ2EEModuleFacetInstallDataModelProperties.ADD_TO_EAR, false);
    webModel.setBooleanProperty(IJ2EEFacetInstallDataModelProperties.GENERATE_DD, true);
    webModel.setStringProperty(IWebFacetInstallDataModelProperties.CONFIG_FOLDER, webAppFolder);
    return webModel;
  }


  /**
   * Return true if the given project is a Java project and has a source path that overlaps with the
   * given path.
   */
  public static boolean overlapsWithJavaSourcePaths(IFacetedProject facetedProject,
      IPath relativePath) {
    List<IPath> sourcePaths = WebProjectUtil.getJavaSourcePaths(facetedProject.getProject());
    for (IPath sourcePath : sourcePaths) {
      if (sourcePath.isPrefixOf(relativePath)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Adds an install action for {@code facet} with its {@code config} to the list of actions
   * performed when {@link FacetUtil#install(IProgressMonitor)} is called, if {@code facet} does not
   * already exist in the configured project.
   *
   * @param facet the facet to be installed
   * @param config the configuration object or null
   */
  public FacetUtil addFacetToBatch(IProjectFacetVersion facet, Object config) {
    Preconditions.checkNotNull(facet, "facet is null");

    if (facetedProject.hasProjectFacet(facet)) {
      return this;
    }

    facetInstallSet.add(new IFacetedProject.Action(
        IFacetedProject.Action.Type.INSTALL, facet, config));
    return this;
  }

  /**
   * Modifies the set of project facets in the configured project by performing the series of
   * configured facets actions.
   *
   * @param monitor a progress monitor, or null if progress reporting and cancellation are not desired
   * @throws CoreException if anything goes wrong while applying facet actions
   */
  public void install(IProgressMonitor monitor) throws CoreException {
    SubMonitor subMonitor = SubMonitor.convert(monitor, 100);

    // Workaround deadlock bug described in Eclipse bug (https://bugs.eclipse.org/511793).
    // There are graph update jobs triggered by the completion of the CreateProjectOperation
    // above (from resource notifications) and from other resource changes from modifying the
    // project facets. So we force the dependency graph to defer updates
    try {
      IDependencyGraph.INSTANCE.preUpdate();
      try {
        Job.getJobManager().join(DependencyGraphImpl.GRAPH_UPDATE_JOB_FAMILY,
            subMonitor.split(10));
      } catch (OperationCanceledException | InterruptedException ex) {
        logger.log(Level.WARNING, "Exception waiting for WTP Graph Update job", ex);
      }

      facetedProject.modify(facetInstallSet, subMonitor.split(90));
    } finally {
      IDependencyGraph.INSTANCE.postUpdate();
    }
  }

  /**
   * Attempts to find a main web application directory, by the following logic:
   *
   * 1. If there is no {@code WEB-INF} folder in the {@code project}, returns {@code null}.
   * 2. Otherwise, if there is at least one {@code WEB-INF} folder that contains {@code web.xml},
   *     returns the parent directory of one of such {@code WEB-INF} folders.
   * 3. Otherwise, returns the parent directory of an arbitrary {@code WEB-INF}.
   *
   * @return path of the main web application directory, relative to {@code project}, if found;
   *     otherwise, {@code null}
   */
  @VisibleForTesting
  static IPath findMainWebAppDirectory(IProject project) {
    List<IFolder> webInfFolders = findAllWebInfFolders(project);
    if (webInfFolders.isEmpty()) {
      return null;
    }

    for (IFolder webInf : webInfFolders) {
      if (webInf.getFile("web.xml").exists()) {
        return webInf.getParent().getProjectRelativePath();
      }
    }
    return webInfFolders.get(0).getParent().getProjectRelativePath();
  }

  /**
   * Returns a list of WEB-INF folders in {@code container}.
   */
  @VisibleForTesting
  static List<IFolder> findAllWebInfFolders(IContainer container) {
    final List<IFolder> webInfFolders = new ArrayList<>();

    try {
      IResourceVisitor webInfCollector = new IResourceVisitor() {
        @Override
        public boolean visit(IResource resource) throws CoreException {
          if (resource.getType() == IResource.FOLDER && "WEB-INF".equals(resource.getName())) {
            webInfFolders.add((IFolder) resource);
            return false;  // No need to visit sub-directories.
          }
          return true;
        }
      };
      container.accept(webInfCollector);
    } catch (CoreException ex) {
      // Our attempt to find folders failed, but don't error out.
    }
    return webInfFolders;
  }


  /**
   * Return the highest {@link IProjectFacetVersion version of a facet} that is supported with the
   * other installed {@link IProjectFacetVersion facet versions} on a project other than those
   * specified to be ignored. Somewhat like
   * {@link IFacetedProjectWorkingCopy#getHighestAvailableVersion(IProjectFacet)} when all installed
   * facet versions are treated as fixed.
   */
  public static IProjectFacetVersion getHighestSatisfyingVersion(IFacetedProjectBase facetedProject,
      IProjectFacet facet, Collection<IProjectFacet> toIgnore) {
    // ensure we always ignore the facet itself
    toIgnore = new HashSet<>(toIgnore == null ? Collections.<IProjectFacet>emptySet() : toIgnore);
    toIgnore.add(facet);
    Collection<IProjectFacetVersion> installedFacetVersions =
        filterVersions(facetedProject.getProjectFacets(), toIgnore);

    IProjectFacetVersion highestFacetVersion = null;
    for (IProjectFacetVersion facetVersion : facet.getVersions()) {
      if (!conflictsWith(installedFacetVersions, facetVersion)
          && (highestFacetVersion == null || highestFacetVersion.compareTo(facetVersion) < 0)) {
        highestFacetVersion = facetVersion;
      }
    }

    return highestFacetVersion;
  }

  /**
   * Return the highest {@link IProjectFacetVersion version of a facet} that is supported with the
   * other installed {@link IProjectFacetVersion facet versions} on a project. Somewhat like
   * {@link IFacetedProjectWorkingCopy#getHighestAvailableVersion(IProjectFacet)} when all installed
   * facet versions are treated as fixed.
   */
  public static IProjectFacetVersion getHighestSatisfyingVersion(
      IFacetedProjectWorkingCopy workingCopy, IProjectFacet facet) {
    return getHighestSatisfyingVersion(workingCopy, facet, null);
  }

  /**
   * Return {@code true} if the provided facet version conflicts with any of the installed facet
   * versions (ignoring the prospect, of course).
   */
  private static boolean conflictsWith(Collection<IProjectFacetVersion> installedFacetVersions,
      IProjectFacetVersion prospect) {
    for (IProjectFacetVersion installed : installedFacetVersions) {
      if (prospect.conflictsWith(installed)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Return {@code true} if the provided facet version cannot be installed in the faceted project
   * due to conflicts with existing installed facet versions.
   */
  public static boolean conflictsWith(IFacetedProjectBase facetedProject,
      IProjectFacetVersion facetVersion) {
    return conflictsWith(facetedProject.getProjectFacets(), facetVersion);
  }

  /**
   * Return {@code true} if the provided facet version cannot be installed in the faceted project
   * due to conflicts with existing installed facet versions, ignoring those in {@code toIgnore}.
   */
  public static boolean conflictsWith(IFacetedProjectBase facetedProject,
      IProjectFacetVersion facetVersion, Collection<IProjectFacet> toIgnore) {
    Collection<IProjectFacetVersion> projectFacets =
        filterVersions(facetedProject.getProjectFacets(), toIgnore);
    return conflictsWith(projectFacets, facetVersion);
  }

  /** Filter the set of facet versions of those to be ignored. */
  private static Collection<IProjectFacetVersion> filterVersions(
      Collection<IProjectFacetVersion> facetVersions,
      Collection<IProjectFacet> toIgnore) {
    if (toIgnore == null || toIgnore.isEmpty()) {
      return facetVersions;
    }
    Set<IProjectFacetVersion> filtered = new HashSet<>(facetVersions);
    for (Iterator<IProjectFacetVersion> iter = filtered.iterator(); iter.hasNext();) {
      IProjectFacetVersion installedFacetVersion = iter.next();
      for(IProjectFacet ignoreType : toIgnore) {
        if (ignoreType.equals(installedFacetVersion.getProjectFacet())) {
          iter.remove();
          break;
        }
      }
    }
    return filtered;
  }
}
