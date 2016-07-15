package com.google.cloud.tools.eclipse.appengine.deploy;

import java.io.File;
import java.util.Collections;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;

import com.google.cloud.tools.appengine.api.deploy.DefaultDeployConfiguration;
import com.google.cloud.tools.appengine.cloudsdk.CloudSdk;
import com.google.cloud.tools.appengine.cloudsdk.CloudSdkAppEngineDeployment;
import com.google.common.annotations.VisibleForTesting;

public class AppEngineProjectDeployer {

  private static final String PLUGIN_ID = "com.google.cloud.tools.eclipse.appengine.localserver"; //$NON-NLS-1$
  private static final String APPENGINE_WEB_XML_PATH = "WEB-INF/appengine-web.xml"; //$NON-NLS-1$

  private AppEngineDeployInfo deployInfo;

  public AppEngineProjectDeployer() {
    deployInfo = new AppEngineDeployInfo();
  }

  @VisibleForTesting
  AppEngineProjectDeployer(AppEngineDeployInfo deployInfo) {
    this.deployInfo = deployInfo;
  }

  public void deploy(IPath stagingDirectory, CloudSdk cloudSdk, IProgressMonitor monitor) throws CoreException {
    SubMonitor progress = SubMonitor.convert(monitor, 1);
    progress.setTaskName(Messages.getString("task.name.deploy.project")); //$NON-NLS-1$
    try  {

      deployInfo.parse(new File(stagingDirectory.append(APPENGINE_WEB_XML_PATH).toOSString()));

      String projectId = deployInfo.getProjectId();
      if (projectId == null) {
        throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, Messages.getString("project.id.missing"))); //$NON-NLS-1$
      }

      String projectVersion = deployInfo.getProjectVersion();
      if (projectVersion == null) {
        throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, Messages.getString("project.version.missing"))); //$NON-NLS-1$
      }

      DefaultDeployConfiguration deployConfig = new DefaultDeployConfiguration();
      deployConfig.setDeployables(Collections.singletonList(stagingDirectory.append("app.yaml").toFile())); //$NON-NLS-1$
      deployConfig.setProject(projectId);
      deployConfig.setVersion(projectVersion + "1");
      deployConfig.setPromote(false);

      CloudSdkAppEngineDeployment deployment = new CloudSdkAppEngineDeployment(cloudSdk);
      deployment.deploy(deployConfig);
    } finally {
      progress.worked(1);
    }
  }
}
