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

package com.google.cloud.tools.eclipse.appengine.deploy.ui.flexible;

import com.google.cloud.tools.eclipse.appengine.deploy.StagingDelegate;
import com.google.cloud.tools.eclipse.appengine.deploy.flex.FlexDeployPreferences;
import com.google.cloud.tools.eclipse.appengine.deploy.flex.FlexJarMavenProjectStagingDelegate;
import com.google.cloud.tools.eclipse.appengine.deploy.flex.FlexStagingDelegate;
import com.google.cloud.tools.eclipse.appengine.deploy.ui.DeployCommandHandler;
import com.google.cloud.tools.eclipse.appengine.deploy.ui.DeployPreferencesDialog;
import com.google.cloud.tools.eclipse.appengine.deploy.ui.Messages;
import com.google.cloud.tools.eclipse.appengine.facets.AppEngineFlexJarFacet;
import com.google.cloud.tools.eclipse.appengine.facets.AppEngineFlexWarFacet;
import com.google.cloud.tools.eclipse.googleapis.IGoogleApiFactory;
import com.google.cloud.tools.eclipse.login.IGoogleLoginService;
import com.google.cloud.tools.eclipse.util.MavenUtils;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import com.google.common.base.Preconditions;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;

public class FlexDeployCommandHandler extends DeployCommandHandler {

  @Override
  protected DeployPreferencesDialog newDeployPreferencesDialog(Shell shell, IProject project,
      IGoogleLoginService loginService, IGoogleApiFactory googleApiFactory) {
    String title = Messages.getString("deploy.preferences.dialog.title.flexible");
    return new FlexDeployPreferencesDialog(shell, title, project, loginService, googleApiFactory);
  }

  @Override
  protected StagingDelegate getStagingDelegate(IProject project) throws CoreException {
    IFacetedProject facetedProject = ProjectFacetsManager.create(project);
    Preconditions.checkNotNull(facetedProject);

    String appYamlPath = new FlexDeployPreferences(project).getAppYamlPath();
    IFile appYaml = project.getFile(appYamlPath);
    if (!appYaml.exists()) {
      throw new CoreException(StatusUtil.error(this, appYaml + " does not exist."));
    }
    IPath appEngineDirectory = appYaml.getParent().getLocation();

    if (AppEngineFlexWarFacet.hasFacet(facetedProject)) {
      return new FlexStagingDelegate(appEngineDirectory);
    } else if (AppEngineFlexJarFacet.hasFacet(facetedProject)) {
      if (MavenUtils.hasMavenNature(project)) {
        return new FlexJarMavenProjectStagingDelegate(appEngineDirectory);
      } else {
        throw new IllegalStateException("BUG: command enabled for non-Maven flex projects");
      }
    } else {
      throw new IllegalStateException("BUG: command enabled for non-flex projects");
    }
  }

}
