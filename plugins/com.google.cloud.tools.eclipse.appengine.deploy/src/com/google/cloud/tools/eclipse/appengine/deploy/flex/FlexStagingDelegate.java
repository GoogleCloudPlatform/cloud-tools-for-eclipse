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

package com.google.cloud.tools.eclipse.appengine.deploy.flex;

import com.google.cloud.tools.appengine.api.AppEngineException;
import com.google.cloud.tools.eclipse.appengine.deploy.CloudSdkStagingHelper;
import com.google.cloud.tools.eclipse.appengine.deploy.Messages;
import com.google.cloud.tools.eclipse.appengine.deploy.StagingDelegate;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.ui.console.MessageConsoleStream;

/**
 * Stages an App Engine flexible app, copying an app (a WAR or a runnable JAR file) and {@code
 * app.yaml} to the given staging directory.
 *
 * See the Javadoc of {@link StagingDelegate} for more details.
 *
 * @see StagingDelegate
 */
abstract class FlexStagingDelegate implements StagingDelegate {

  private final IPath appEngineDirectory;

  public FlexStagingDelegate(IPath appEngineDirectory) {
    this.appEngineDirectory = appEngineDirectory;
  }

  @Override
  public IStatus stage(IPath stagingDirectory, IPath safeWorkDirectory,
      MessageConsoleStream stdoutOutputStream, MessageConsoleStream stderrOutputStream,
      IProgressMonitor monitor) {
    SubMonitor subMonitor = SubMonitor.convert(monitor, 100);

    boolean result = stagingDirectory.toFile().mkdirs();
    if (!result) {
      return StatusUtil.error(this, "Could not create staging directory " + stagingDirectory);
    }

    try {
      IPath deployArtifact = getDeployArtifact(safeWorkDirectory, subMonitor.split(40));
      CloudSdkStagingHelper.stageFlexible(appEngineDirectory, deployArtifact, stagingDirectory,
          subMonitor.split(60));
      return Status.OK_STATUS;
    } catch (AppEngineException | CoreException ex) {
      return StatusUtil.error(this, Messages.getString("deploy.job.staging.failed"), ex);
    } finally {
      subMonitor.done();
    }
  }

  protected abstract IPath getDeployArtifact(IPath safeWorkDirectory, IProgressMonitor monitor)
      throws CoreException;

  @Override
  public IPath getOptionalConfigurationFilesDirectory() {
    return appEngineDirectory;
  }

  @Override
  public void interrupt() {
    // It's enough to leave it to the normal cancellation flow through monitor.
  }

}
