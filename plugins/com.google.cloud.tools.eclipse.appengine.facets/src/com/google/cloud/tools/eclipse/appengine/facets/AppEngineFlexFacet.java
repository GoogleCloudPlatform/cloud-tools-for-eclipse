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

package com.google.cloud.tools.eclipse.appengine.facets;

import com.google.cloud.tools.eclipse.util.FacetedProjectHelper;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jst.common.project.facet.core.JavaFacet;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.IProjectFacet;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;

public class AppEngineFlexFacet {
  public static final String ID = "com.google.cloud.tools.eclipse.appengine.facets.flex";
  public static final String VERSION = "1";

  /**
   * Returns true if project has the App Engine Flex facet and false otherwise.
   *
   * @param project should not be null
   * @return true if project has the App Engine Flex facet and false otherwise
   */
  public static boolean hasAppEngineFacet(IFacetedProject project) {
    return FacetedProjectHelper.projectHasFacet(project, ID);
  }

  /**
   * Checks to see if <code>facetedProject</code> has the App Engine Flexible facet.
   * If not, it installs the App Engine Flexible facet.
   *
   * @param facetedProject the faceted project receiving the App Engine facet
   * @param installDependentFacets true if the facets required by the App Engine facet should be
   *        installed, false otherwise
   * @param monitor the progress monitor
   * @throws CoreException if anything goes wrong during install
   */
  public static void installAppEngineFacet(IFacetedProject facetedProject,
      boolean installDependentFacets, IProgressMonitor monitor) throws CoreException {
    Set<IFacetedProject.Action> facetInstallSet = new HashSet<>();

    IProjectFacet appEngineFacet = ProjectFacetsManager.getProjectFacet(AppEngineFlexFacet.ID);
    if (facetedProject.hasProjectFacet(appEngineFacet)) {
      return;
    }

    // Get the Java 8 facet
    if (installDependentFacets) {
      FacetUtil.addJavaFacetToBatch(JavaFacet.VERSION_1_8, facetedProject, facetInstallSet);
    }

    // Get App Engine facet version
    IProjectFacetVersion appEngineFacetVersion =
        appEngineFacet.getVersion(AppEngineFlexFacet.VERSION);
    facetInstallSet.add(new IFacetedProject.Action(
        IFacetedProject.Action.Type.INSTALL, appEngineFacetVersion, null /* config */));
    FacetUtil.addFacetSetToProject(facetedProject, facetInstallSet, monitor);
  }
}
