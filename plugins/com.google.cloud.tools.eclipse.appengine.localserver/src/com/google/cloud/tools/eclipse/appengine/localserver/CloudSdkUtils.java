/*******************************************************************************
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/
package com.google.cloud.tools.eclipse.appengine.localserver;

import java.util.List;

import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.IProjectFacet;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;
import org.eclipse.wst.server.core.IRuntime;
import org.eclipse.wst.server.core.internal.facets.FacetUtil;

import com.google.cloud.tools.eclipse.util.MavenUtils;

/**
 * Utility methods for Google Cloud SDK.
 */
@SuppressWarnings("restriction") // For FacetUtil
public class CloudSdkUtils {
  public static final String APPENGINE_GROUP_ID = "com.google.appengine";
  public static final String CLOUD_SDK_FACET_ID = "com.google.cloud.tools.eclipse.facet";
  public static final String CLOUD_SDK_RUNTIME_ID = "com.google.cloud.tools.eclipse.runtime.id1";
  public static final String GCLOUD_MAVEN_PLUGIN_ARTIFACT_ID = "gcloud-maven-plugin";

  /**
   * Returns true if the given project is a maven project with the
   * gcloud-maven-plugin as one of its dependencies. Returns false otherwise.
   *
   * @param project the project to be queried
   * @return true if {@code project} is a maven project with the
   *         gcloud-maven-plugin and false otherwise
   * @throws CoreException if getting instance of MavenProject from
   *           {@code project} fails
   */
  public static boolean hasGcloudMavenPlugin(IProject project) throws CoreException {
    if (!MavenUtils.hasMavenNature(project)) {
      return false;
    }

    IMavenProjectFacade facade = MavenPlugin.getMavenProjectRegistry().getProject(project);
    Model pom = facade.getMavenProject(null).getModel();
    return hasGcloudMavenPlugin(pom);
  }

  /**
   * Returns true if the specified pom has the gcloud-maven-plugin. Returns
   * false otherwise.
   *
   * @param pom the maven project object model
   * @return true if the specified pom has the gcloud-maven-plugin and false
   *         otherwise
   */
  public static boolean hasGcloudMavenPlugin(Model pom) {
    List<Plugin> plugins = pom.getBuild().getPlugins();
    for (Plugin plugin : plugins) {
      if (APPENGINE_GROUP_ID.equals(plugin.getGroupId())
          && GCLOUD_MAVEN_PLUGIN_ARTIFACT_ID.equals(plugin.getArtifactId())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if {@code project} has the Cloud SDK facet and false
   * otherwise.
   *
   * @param project an Eclipse project
   * @return true if {@code project} has the Cloud SDK facet and false
   *         otherwise
   * @throws CoreException if error occurs when creating a
   *           {@link IFacetedProject} associated with {@code project}
   */
  public static boolean hasCloudSdkFacet(IProject project) throws CoreException {
    IFacetedProject facetedProject = ProjectFacetsManager.create(project);
    if (facetedProject == null) {
      return false;
    }
    IProjectFacet facetOfInterest = ProjectFacetsManager.getProjectFacet(CloudSdkUtils.CLOUD_SDK_FACET_ID);
    return facetedProject.hasProjectFacet(facetOfInterest);
  }

  /**
   * Returns the primary facet runtime for {@code project} or null if none has
   * been set.
   *
   * @param project an Eclipse project
   * @return the primary facet runtime for {@code project} or null if none has
   *         been set
   * @throws CoreException if error occurs when creating a
   *           {@link IFacetedProject} associated with {@code project}
   */
  public static IRuntime getPrimaryRuntime(IProject project) throws CoreException {
    IFacetedProject facetedProject = ProjectFacetsManager.create(project);
    org.eclipse.wst.common.project.facet.core.runtime.IRuntime primaryRuntime = facetedProject.getPrimaryRuntime();
    if (primaryRuntime == null) {
      return null;
    }

    return FacetUtil.getRuntime(primaryRuntime);
  }
}
