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
import com.google.cloud.tools.eclipse.appengine.deploy.flex.FlexExistingArtifactDeployPreferences;
import com.google.cloud.tools.eclipse.appengine.deploy.flex.FlexExistingDeployArtifactStagingDelegate;
import com.google.cloud.tools.eclipse.appengine.deploy.ui.DeployCommandHandler;
import com.google.cloud.tools.eclipse.appengine.deploy.ui.DeployPreferencesDialog;
import com.google.cloud.tools.eclipse.appengine.deploy.ui.Messages;
import com.google.cloud.tools.eclipse.googleapis.IGoogleApiFactory;
import com.google.cloud.tools.eclipse.login.IGoogleLoginService;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.swt.widgets.Shell;

public class FlexExistingArtifactDeployCommandHandler extends DeployCommandHandler {

  @Override
  protected DeployPreferencesDialog newDeployPreferencesDialog(Shell shell, IProject project,
      IGoogleLoginService loginService, IGoogleApiFactory googleApiFactory) {
    String title = Messages.getString("deploy.preferences.dialog.title.flexible");
    return new FlexExistingArtifactDeployPreferencesDialog(shell, title, loginService,
        googleApiFactory);
  }

  @Override
  protected StagingDelegate getStagingDelegate(IProject project) throws CoreException {
    String appYamlPath = new FlexExistingArtifactDeployPreferences().getAppYamlPath();
    IFile appYaml = resolveFileAgainstWorkspace(appYamlPath);
    IPath appEngineDirectory = appYaml.getParent().getLocation();

    String deployArtifactPath = new FlexExistingArtifactDeployPreferences().getDeployArtifactPath();
    IFile deployArtifact = resolveFileAgainstWorkspace(deployArtifactPath);

    return new FlexExistingDeployArtifactStagingDelegate(deployArtifact, appEngineDirectory);
  }

  private IFile resolveFileAgainstWorkspace(String path) throws CoreException {
    IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(path));
    if (!file.exists()) {
      throw new CoreException(StatusUtil.error(this, file + " does not exist."));
    }
    return file;
  }

}