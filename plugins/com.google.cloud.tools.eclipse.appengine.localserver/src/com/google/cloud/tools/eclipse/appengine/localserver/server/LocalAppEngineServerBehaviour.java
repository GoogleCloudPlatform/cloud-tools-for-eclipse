package com.google.cloud.tools.eclipse.appengine.localserver.server;

import com.google.cloud.tools.appengine.api.AppEngineException;
import com.google.cloud.tools.appengine.api.devserver.AppEngineDevServer;
import com.google.cloud.tools.appengine.api.devserver.DefaultRunConfiguration;
import com.google.cloud.tools.appengine.api.devserver.DefaultStopConfiguration;
import com.google.cloud.tools.appengine.cloudsdk.CloudSdk;
import com.google.cloud.tools.appengine.cloudsdk.CloudSdkAppEngineDevServer;
import com.google.cloud.tools.appengine.cloudsdk.process.ProcessExitListener;
import com.google.cloud.tools.appengine.cloudsdk.process.ProcessStartListener;
import com.google.cloud.tools.eclipse.appengine.localserver.Activator;
import com.google.cloud.tools.eclipse.sdk.ui.MessageConsoleWriterOutputLineListener;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.IServer;
import org.eclipse.wst.server.core.model.IModuleResource;
import org.eclipse.wst.server.core.model.IModuleResourceDelta;
import org.eclipse.wst.server.core.model.ServerBehaviourDelegate;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link ServerBehaviourDelegate} for App Engine Server executed via the Java App Management
 * Client Library.
 */
public class LocalAppEngineServerBehaviour extends ServerBehaviourDelegate {
  private LocalAppEngineStartListener localAppEngineStartListener;
  private LocalAppEngineExitListener localAppEngineExitListener;
  private AppEngineDevServer devServer;

  public LocalAppEngineServerBehaviour () {
    localAppEngineStartListener = new LocalAppEngineStartListener();
    localAppEngineExitListener = new LocalAppEngineExitListener();
  }

  @Override
  public void stop(boolean force) {
    setServerState(IServer.STATE_STOPPING);
    terminate();
    setServerState(IServer.STATE_STOPPED);    
  }

  /**
   * Convenience method allowing access to protected method in superclass.
   */
  @Override
  protected IModuleResourceDelta[] getPublishedResourceDelta(IModule[] module) {
    return super.getPublishedResourceDelta(module);
  }

  /**
   * Convenience method allowing access to protected method in superclass.
   */
  @Override
  protected IModuleResource[] getResources(IModule[] module) {
    return super.getResources(module);
  }

  @Override
  public IStatus canStop() {
    int serverState = getServer().getServerState();
    if ((serverState != IServer.STATE_STOPPING) && (serverState != IServer.STATE_STOPPED)) {
      return Status.OK_STATUS;
    } else {
      return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Stop in progress");
    }
  }

  /**
   * Returns runtime base directory. Uses temp directory.
   */
  public IPath getRuntimeBaseDirectory() {
    return getTempDirectory(false);
  }

  /**
   * @return the directory at which module will be published.
   */
  public IPath getModuleDeployDirectory(IModule module) {
    return getRuntimeBaseDirectory().append(module.getName());
  }

  /**
   * Convenience accessor to protected member in superclass.
   */
  public final void setModulePublishState2(IModule[] module, int state) {
    setModulePublishState(module, state);
  }

  /**
   * Starts the development server.
   *
   * @param runnables the path to directories that contain configuration files like appengine-web.xml
   * @param stream the stream to send development server process output to
   */
  void startDevServer(List<File> runnables, MessageConsoleStream stream) {
    setServerState(IServer.STATE_STARTING);

    // Create dev app server instance
    initializeDevServer(stream);

    // Create run configuration
    DefaultRunConfiguration devServerRunConfiguration = new DefaultRunConfiguration();
    devServerRunConfiguration.setAppYamls(runnables);

    // FIXME: workaround bug when running on a Java8 JVM
    // https://github.com/GoogleCloudPlatform/gcloud-eclipse-tools/issues/181
    devServerRunConfiguration.setJvmFlags(Arrays.asList("-Dappengine.user.timezone=UTC"));

    // Run server
    try {
      devServer.run(devServerRunConfiguration);
    } catch (AppEngineException ex) {
      Activator.logError("Error starting server: " + ex.getMessage());
      stop(true);
    }
  }

  /**
   * Starts the development server in debug mode.
   *
   * @param runnables the path to directories that contain configuration files like appengine-web.xml
   * @param stream the stream to send development server process output to
   * @param debugPort the port to attach a debugger to if launch is in debug mode
   */
  void startDebugDevServer(List<File> runnables, MessageConsoleStream stream, int debugPort) {
    setServerState(IServer.STATE_STARTING);

    // Create dev app server instance
    initializeDevServer(stream);

    // Create run configuration
    DefaultRunConfiguration devServerRunConfiguration = new DefaultRunConfiguration();
    devServerRunConfiguration.setAppYamls(runnables);

    List<String> jvmFlags = new ArrayList<String>();
    // FIXME: workaround bug when running on a Java8 JVM
    // https://github.com/GoogleCloudPlatform/gcloud-eclipse-tools/issues/181
    jvmFlags.add("-Dappengine.user.timezone=UTC");

    if (debugPort <= 0 || debugPort > 65535) {
      throw new IllegalArgumentException("Debug port is set to " + debugPort
                                      + ", should be between 1-65535");
    }
    jvmFlags.add("-Xdebug");
    jvmFlags.add("-Xrunjdwp:transport=dt_socket,server=n,suspend=y,quiet=y,address=" + debugPort);
    devServerRunConfiguration.setJvmFlags(jvmFlags);

    // Run server
    try {
      devServer.run(devServerRunConfiguration);
    } catch (AppEngineException ex) {
      Activator.logError("Error starting server: " + ex.getMessage());
      stop(true);
    }
  }

  private void initializeDevServer(MessageConsoleStream stream) {
    MessageConsoleWriterOutputLineListener outputListener =
        new MessageConsoleWriterOutputLineListener(stream);

    CloudSdk cloudSdk = new CloudSdk.Builder()
        .addStdOutLineListener(outputListener)
        .addStdErrLineListener(outputListener)
        .startListener(localAppEngineStartListener)
        .exitListener(localAppEngineExitListener)
        .async(true)
        .build();

    devServer = new CloudSdkAppEngineDevServer(cloudSdk);
  }

  private void terminate() {
    if (devServer != null) {
      // TODO: when available configure the host and port specified in the server
      DefaultStopConfiguration stopConfig = new DefaultStopConfiguration();
      try {
        devServer.stop(stopConfig);
      } catch (AppEngineException ex) {
        // TODO what do we need to do here
        Activator.logError("Error terminating server: " + ex.getMessage());
      }
      devServer = null;
    }
  }

  /**
   * A {@link ProcessExitListener} for the App Engine server.
   */
  public class LocalAppEngineExitListener implements ProcessExitListener {
    @Override
    public void onExit(int exitCode) {
      stop(true);
    }
  }

  /**
   * A {@link ProcessStarteListener} for the App Engine server.
   */
  public class LocalAppEngineStartListener implements ProcessStartListener {
    @Override
    public void onStart(Process process) {
      setServerState(IServer.STATE_STARTED);
    }
  }
}
