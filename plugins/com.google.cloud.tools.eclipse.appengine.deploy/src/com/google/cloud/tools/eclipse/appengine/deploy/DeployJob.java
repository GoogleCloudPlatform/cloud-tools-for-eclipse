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

package com.google.cloud.tools.eclipse.appengine.deploy;

import com.google.api.client.auth.oauth2.Credential;
import com.google.cloud.tools.eclipse.login.CredentialHelper;
import com.google.cloud.tools.eclipse.sdk.CloudSdkManager;
import com.google.cloud.tools.eclipse.ui.util.WorkbenchUtil;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.ui.console.MessageConsoleStream;

/**
 * Executes a job that deploys a project to App Engine standard or flexible environment.
 * <p>
 * Deploy steps:
 * <ol>
 *  <li>prepare deploy artifact (WAR or exploded WAR)</li>
 *  <li>stage project for deploy</li>
 *  <li>deploy staged project</li>
 *  <li>launch the deployed app in browser</li>
 * </ol>
 * It uses a work directory where it will create, e.g., a JSON user credential file, a WAR, a
 * directory to put exploded WAR contents, a directory to put staging results, etc.
 */
public class DeployJob extends WorkspaceJob {

  private static final String STAGING_DIRECTORY_NAME = "staging";
  private static final String SAFE_STAGING_WORK_DIRECTORY_NAME = "staging-work";
  private static final String CREDENTIAL_FILENAME = "gcloud-credentials.json";
  private static final String DEFAULT_SERVICE = "default";

  private final Credential credential;
  private final IPath workDirectory;
  private final MessageConsoleStream stdoutOutputStream;
  private final MessageConsoleStream stderrOutputStream;
  private final DeployPreferences deployPreferences;
  private final StagingDelegate stager;
  private final AppEngineProjectDeployer deployer = new AppEngineProjectDeployer();

  /**
   * @param workDirectory temporary work directory the job can safely use (e.g., for creating and
   *     copying various files to stage and deploy)
   */
  public DeployJob(DeployPreferences deployPreferences, Credential credential, IPath workDirectory,
      MessageConsoleStream stdoutOutputStream, MessageConsoleStream stderrOutputStream,
      StagingDelegate stager) {
    super(Messages.getString("deploy.job.name")); //$NON-NLS-1$
    Preconditions.checkNotNull(deployPreferences.getProjectId());
    Preconditions.checkArgument(!deployPreferences.getProjectId().isEmpty());
    this.deployPreferences = deployPreferences;
    this.credential = credential;
    this.workDirectory = workDirectory;
    this.stdoutOutputStream = stdoutOutputStream;
    this.stderrOutputStream = stderrOutputStream;
    this.stager = stager;
  }

  @Override
  public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
    SubMonitor progress = SubMonitor.convert(monitor, 120);

    try {
      progress.subTask("Checking for Google Cloud SDK");
      IStatus installStatus =
          CloudSdkManager.getInstance().installManagedSdk(stdoutOutputStream, progress.split(20));
      if (installStatus != Status.OK_STATUS) {
        return StatusUtil.error(
            this,
            "Deploy failed: cannot install Google Cloud SDK",
            new CoreException(installStatus));
      }

      progress.subTask("Saving credential");
      Path credentialFile = workDirectory.append(CREDENTIAL_FILENAME).toFile().toPath();

      IStatus saveStatus = saveCredential(credentialFile);
      if (saveStatus != Status.OK_STATUS) {
        return saveStatus;
      }

      progress.subTask("Staging project files");
      IPath stagingDirectory = workDirectory.append(STAGING_DIRECTORY_NAME);
      IStatus stagingStatus = stageProject(stagingDirectory, progress.split(30));
      if (stagingStatus != Status.OK_STATUS) {
        return stagingStatus;
      } else if (monitor.isCanceled()) {
        return Status.CANCEL_STATUS;
      }

      progress.subTask("Deploying staged project");
      IStatus deployStatus = deployProject(credentialFile, stagingDirectory, progress.split(70));
      if (deployStatus != Status.OK_STATUS) {
        return deployStatus;
      } else if (monitor.isCanceled()) {
        return Status.CANCEL_STATUS;
      }

      return openAppInBrowser();
    } finally {
      progress.done();
    }
  }

  @Override
  protected void canceling() {
    stager.interrupt();
    deployer.interrupt();
    super.canceling();
  }

  private IStatus saveCredential(Path destination) {
    try {
      CredentialHelper.toJsonFile(credential, destination);
      return Status.OK_STATUS;
    } catch (IOException ex) {
      return StatusUtil.error(this, Messages.getString("save.credential.failed"), ex);
    }
  }

  private IStatus stageProject(IPath stagingDirectory, IProgressMonitor monitor) {
    SubMonitor progress = SubMonitor.convert(monitor, 100);

    try {
      getJobManager().beginRule(stager.getSchedulingRule(), progress.split(1));
      IPath safeWorkDirectory = workDirectory.append(SAFE_STAGING_WORK_DIRECTORY_NAME);
      return stager.stage(stagingDirectory, safeWorkDirectory,
          stdoutOutputStream, stderrOutputStream, progress.split(99));
    } catch (IllegalArgumentException ex) {
      return StatusUtil.error(this, Messages.getString("deploy.job.staging.failed"), ex);
    } finally {
      getJobManager().endRule(stager.getSchedulingRule());
    }
  }

  private IStatus deployProject(Path credentialFile, IPath stagingDirectory, IProgressMonitor monitor) {
    IPath optionalConfigurationFilesDirectory = null;
    if (deployPreferences.isIncludeOptionalConfigurationFiles()) {
      optionalConfigurationFilesDirectory = stager.getOptionalConfigurationFilesDirectory();
    }

    return deployer.deploy(stagingDirectory, credentialFile, deployPreferences,
        optionalConfigurationFilesDirectory, stdoutOutputStream, monitor);
  }

  private IStatus openAppInBrowser() {
    try {
      String rawDeployOutput = deployer.getJsonDeployResult();
      AppEngineDeployOutput structuredOutput = AppEngineDeployOutput.parse(rawDeployOutput);

      boolean promoted = deployPreferences.isAutoPromote();
      String appLocation = getDeployedAppUrl(promoted, structuredOutput);
      String project = deployPreferences.getProjectId();
      String browserTitle = Messages.getString("browser.launch.title", project);
      WorkbenchUtil.openInBrowserInUiThread(appLocation, null, browserTitle, browserTitle);
      return Status.OK_STATUS;
    } catch (IndexOutOfBoundsException | JsonParseException ex)  {
      return StatusUtil.error(this, Messages.getString("browser.launch.failed"), ex);
    }
  }

  @VisibleForTesting
  static String getDeployedAppUrl(boolean promoted, AppEngineDeployOutput deployOutput) {
    String version = deployOutput.getVersion();
    String service = deployOutput.getService();
    String projectId = deployOutput.getProject();
    boolean usingDefaultService = DEFAULT_SERVICE.equals(service);

    String domain = ".appspot.com";
    int colon = projectId.indexOf(':');
    if (colon >= 0) {
      domain = ".googleplex.com";
      projectId = projectId.substring(colon + 1);
    }

    if (promoted && usingDefaultService) {
      return "https://" + projectId + domain;
    } else if (promoted && !usingDefaultService) {
      return "https://" + service +  "-dot-"+  projectId + domain;
    } else if (!promoted && usingDefaultService) {
      return "https://" + version + "-dot-" + projectId + domain;
    } else {
      return "https://" + version + "-dot-" + service +  "-dot-"+  projectId + domain;
    }
  }

}
