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

package com.google.cloud.tools.eclipse.appengine.deploy.standard;

import com.google.cloud.tools.appengine.api.AppEngineException;
import com.google.cloud.tools.eclipse.appengine.deploy.CloudSdkStagingHelper;
import com.google.cloud.tools.eclipse.appengine.deploy.Messages;
import com.google.cloud.tools.eclipse.appengine.deploy.StagingDelegate;
import com.google.cloud.tools.eclipse.appengine.deploy.WarPublisher;
import com.google.cloud.tools.eclipse.appengine.deploy.util.CloudSdkProcessWrapper;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.nio.file.Path;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.ui.console.MessageConsoleStream;

public class StandardStagingDelegate implements StagingDelegate {

  private final IProject project;
  private final Path javaHome;
  private final CloudSdkProcessWrapper cloudSdkWrapper;

  private IPath optionalConfigurationFilesDirectory;

  public StandardStagingDelegate(IProject project, Path javaHome) {
    this(project, javaHome, new CloudSdkProcessWrapper());
  }

  @VisibleForTesting
  StandardStagingDelegate(IProject project, Path javaHome, CloudSdkProcessWrapper cloudSdkWrapper) {
    this.project = Preconditions.checkNotNull(project);
    this.javaHome = javaHome;
    this.cloudSdkWrapper = cloudSdkWrapper;
  }

  @Override
  public IStatus stage(IPath stagingDirectory, IPath safeWorkDirectory,
      MessageConsoleStream stdoutOutputStream, MessageConsoleStream stderrOutputStream,
      IProgressMonitor monitor) {
    SubMonitor subMonitor = SubMonitor.convert(monitor, 100);

    try {
      cloudSdkWrapper.setUpStandardStagingCloudSdk(
          javaHome, stdoutOutputStream, stderrOutputStream);

      IPath explodedWar = safeWorkDirectory.append("exploded-war");
      IPath tempDirectory = safeWorkDirectory.append("temp");
      WarPublisher.publishExploded(project, explodedWar, tempDirectory, subMonitor.split(40));
      CloudSdkStagingHelper.stageStandard(explodedWar, stagingDirectory,
          cloudSdkWrapper.getCloudSdk(), subMonitor.split(60));

      optionalConfigurationFilesDirectory =
          stagingDirectory.append(CloudSdkStagingHelper.STANDARD_STAGING_GENERATED_FILES_DIRECTORY);
      return cloudSdkWrapper.getExitStatus();
    } catch (AppEngineException ex) {
      return StatusUtil.error(this, Messages.getString("deploy.job.staging.failed"), ex);
    } catch (CoreException ex) {
      return StatusUtil.error(this, Messages.getString("war.publishing.failed"), ex);
    } finally {
      subMonitor.done();
    }
  }

  @Override
  public IPath getOptionalConfigurationFilesDirectory() {
    return optionalConfigurationFilesDirectory;
  }

  @Override
  public void interrupt() {
    cloudSdkWrapper.interrupt();
  }

  @Override
  public ISchedulingRule getSchedulingRule() {
    return project;
  }
}
