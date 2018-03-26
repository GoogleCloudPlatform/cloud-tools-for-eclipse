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

package com.google.cloud.tools.eclipse.appengine.localserver.server;

import com.google.cloud.tools.appengine.api.devserver.DefaultRunConfiguration;
import com.google.cloud.tools.appengine.api.devserver.RunConfiguration;
import com.google.cloud.tools.appengine.cloudsdk.CloudSdk;
import com.google.cloud.tools.appengine.cloudsdk.CloudSdkNotFoundException;
import com.google.cloud.tools.appengine.cloudsdk.CloudSdkOutOfDateException;
import com.google.cloud.tools.eclipse.appengine.localserver.Activator;
import com.google.cloud.tools.eclipse.appengine.localserver.Messages;
import com.google.cloud.tools.eclipse.appengine.localserver.PreferencesInitializer;
import com.google.cloud.tools.eclipse.appengine.localserver.ui.LocalAppEngineConsole;
import com.google.cloud.tools.eclipse.appengine.localserver.ui.StaleResourcesStatusHandler;
import com.google.cloud.tools.eclipse.sdk.CloudSdkManager;
import com.google.cloud.tools.eclipse.ui.util.MessageConsoleUtilities;
import com.google.cloud.tools.eclipse.ui.util.WorkbenchUtil;
import com.google.cloud.tools.eclipse.usagetracker.AnalyticsEvents;
import com.google.cloud.tools.eclipse.usagetracker.AnalyticsPingManager;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.net.InetAddresses;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.ILaunchesListener2;
import org.eclipse.debug.core.IStatusHandler;
import org.eclipse.debug.core.model.DebugElement;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.preferences.IDebugPreferenceConstants;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.console.ConsoleColorProvider;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IVMConnector;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.SocketUtil;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IOConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.statushandlers.StatusManager;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.IServer;
import org.eclipse.wst.server.core.IServerListener;
import org.eclipse.wst.server.core.ServerCore;
import org.eclipse.wst.server.core.ServerEvent;
import org.eclipse.wst.server.core.ServerUtil;

public class LocalAppEngineServerLaunchConfigurationDelegate
    extends AbstractJavaLaunchConfigurationDelegate {

  static final boolean DEV_APPSERVER2 = false;

  private static final Logger logger =
      Logger.getLogger(LocalAppEngineServerLaunchConfigurationDelegate.class.getName());

  public static final String[] SUPPORTED_LAUNCH_MODES =
      {ILaunchManager.RUN_MODE, ILaunchManager.DEBUG_MODE};

  private static final String DEBUGGER_HOST = "localhost"; //$NON-NLS-1$

  private static int ifNull(Integer value, int nullValue) {
    return value != null ? value : nullValue;
  }

  private static IStatus validateCloudSdk(IProgressMonitor monitor) {
    // ensure we have a Cloud SDK; no-op if not configured to use managed sdk
    IStatus status = CloudSdkManager.getInstance().installManagedSdk(null, monitor);
    if (!status.isOK()) {
      return status;
    }
    try {
      CloudSdk cloudSdk = new CloudSdk.Builder().build();
      cloudSdk.validateCloudSdk();
      return Status.OK_STATUS;
    } catch (CloudSdkNotFoundException ex) {
      return StatusUtil.error(
          LocalAppEngineServerLaunchConfigurationDelegate.class,
          Messages.getString("cloudsdk.not.configured"), // $NON-NLS-1$
          ex);
    } catch (CloudSdkOutOfDateException ex) {
      return StatusUtil.error(
          LocalAppEngineServerLaunchConfigurationDelegate.class,
          Messages.getString("cloudsdk.out.of.date"), // $NON-NLS-1$
          ex);
    }
  }

  @Override
  public ILaunch getLaunch(ILaunchConfiguration configuration, String mode) throws CoreException {
    IServer server = ServerUtil.getServer(configuration);
    DefaultRunConfiguration runConfig = generateServerRunConfiguration(configuration, server, mode);
    ILaunch[] launches = getLaunchManager().getLaunches();
    checkConflictingLaunches(configuration.getType(), mode, runConfig, launches);
    return super.getLaunch(configuration, mode);
  }

  @Override
  public boolean finalLaunchCheck(ILaunchConfiguration configuration, String mode,
      IProgressMonitor monitor) throws CoreException {
    SubMonitor progress = SubMonitor.convert(monitor, 50);
    if (!super.finalLaunchCheck(configuration, mode, progress.split(10))) {
      return false;
    }
    IStatus status = validateCloudSdk(progress.split(20));
    if (!status.isOK()) {
      // Throwing a CoreException will result in the ILaunch hanging around in
      // an invalid state
      StatusManager.getManager().handle(status, StatusManager.SHOW | StatusManager.LOG);
      return false;
    }

    // If we're auto-publishing before launch, check if there may be stale
    // resources not yet published. See
    // https://github.com/GoogleCloudPlatform/google-cloud-eclipse/issues/1832
    if (ServerCore.isAutoPublishing() && ResourcesPlugin.getWorkspace().isAutoBuilding()) {
      // Must wait for any current autobuild to complete so resource changes are triggered
      // and WTP will kick off ResourceChangeJobs. Note that there may be builds
      // pending that are unrelated to our resource changes, so simply checking
      // <code>JobManager.find(FAMILY_AUTO_BUILD).length > 0</code> produces too many
      // false positives.
      try {
        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, progress.split(20));
      } catch (InterruptedException ex) {
        /* ignore */
      }
      IServer server = ServerUtil.getServer(configuration);
      if (server.shouldPublish() || hasPendingChangesToPublish()) {
        IStatusHandler prompter = DebugPlugin.getDefault().getStatusHandler(promptStatus);
        if (prompter != null) {
          Object continueLaunch = prompter
              .handleStatus(StaleResourcesStatusHandler.CONTINUE_LAUNCH_REQUEST, configuration);
          if (!(Boolean) continueLaunch) {
            // cancel the launch so Server.StartJob won't raise an error dialog, since the
            // server won't have been started
            monitor.setCanceled(true);
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * Check if there are pending changes to be published: this is a nasty condition that can occur if
   * the user saves changes as part of launching the server.
   */
  private boolean hasPendingChangesToPublish() {
    Job[] serverJobs = Job.getJobManager().find(ServerUtil.SERVER_JOB_FAMILY);
    Job currentJob = Job.getJobManager().currentJob();
    for (Job job : serverJobs) {
      // Launching from Server#start() means this will be running within a
      // Server.StartJob. All other jobs should be ResourceChangeJob or
      // PublishJob, both of which indicate unpublished changes.
      if (job != currentJob) {
        return true;
      }
    }
    return false;
  }

  @VisibleForTesting
  void checkConflictingLaunches(ILaunchConfigurationType launchConfigType, String mode,
      DefaultRunConfiguration runConfig, ILaunch[] launches) throws CoreException {

    for (ILaunch launch : launches) {
      if (launch.isTerminated()
          || launch.getLaunchConfiguration() == null
          || launch.getLaunchConfiguration().getType() != launchConfigType) {
        continue;
      }
      IServer otherServer = ServerUtil.getServer(launch.getLaunchConfiguration());
      DefaultRunConfiguration otherRunConfig =
          generateServerRunConfiguration(launch.getLaunchConfiguration(), otherServer, mode);
      IStatus conflicts = checkConflicts(runConfig, otherRunConfig,
          new MultiStatus(Activator.PLUGIN_ID, 0,
              Messages.getString("conflicts.with.running.server", otherServer.getName()), //$NON-NLS-1$
              null));
      if (!conflicts.isOK()) {
        throw new CoreException(StatusUtil.filter(conflicts));
      }
    }
  }

  /**
   * Create a CloudSdk RunConfiguration corresponding to the launch configuration and server
   * defaults. Details are pulled from {@link ILaunchConfiguration#getAttributes() launch
   * attributes} and {@link IServer server settings and attributes}.
   */
  @VisibleForTesting
  DefaultRunConfiguration generateServerRunConfiguration(ILaunchConfiguration configuration,
      IServer server, String mode) throws CoreException {

    DefaultRunConfiguration devServerRunConfiguration = new DefaultRunConfiguration();
    // Iterate through our different configurable parameters
    // TODO: storage-related paths, incl storage_path and the {blob,data,*search*,logs} paths

    // TODO: allow setting host from launch config
    if (server.getHost() != null) {
      devServerRunConfiguration.setHost(server.getHost());
    }

    int serverPort = getPortAttribute(LocalAppEngineServerBehaviour.SERVER_PORT_ATTRIBUTE_NAME,
        LocalAppEngineServerBehaviour.DEFAULT_SERVER_PORT, configuration, server);
    if (serverPort >= 0) {
      devServerRunConfiguration.setPort(serverPort);
    }


    // only restart server on on-disk changes detected when in RUN mode
    devServerRunConfiguration.setAutomaticRestart(ILaunchManager.RUN_MODE.equals(mode));

    if (DEV_APPSERVER2) {
      if (ILaunchManager.DEBUG_MODE.equals(mode)) {
        // default to 1 instance to simplify debugging
        devServerRunConfiguration.setMaxModuleInstances(1);
      }

      String adminHost = getAttribute(LocalAppEngineServerBehaviour.ADMIN_HOST_ATTRIBUTE_NAME,
          LocalAppEngineServerBehaviour.DEFAULT_ADMIN_HOST, configuration, server);
      if (!Strings.isNullOrEmpty(adminHost)) {
        devServerRunConfiguration.setAdminHost(adminHost);
      }

      int adminPort = getPortAttribute(LocalAppEngineServerBehaviour.ADMIN_PORT_ATTRIBUTE_NAME,
          -1, configuration, server);
      if (adminPort >= 0) {
        devServerRunConfiguration.setAdminPort(adminPort);
      } else {
        // adminPort = -1 perform failover if default port is busy
        devServerRunConfiguration.setAdminPort(LocalAppEngineServerBehaviour.DEFAULT_ADMIN_PORT);
        // adminHost == null is ok as that resolves to null == INADDR_ANY
        InetAddress addr = resolveAddress(devServerRunConfiguration.getAdminHost());
        if (org.eclipse.wst.server.core.util.SocketUtil.isPortInUse(addr,
            devServerRunConfiguration.getAdminPort())) {
          logger.log(Level.INFO, "default admin port " + devServerRunConfiguration.getAdminPort() //$NON-NLS-1$
              + " in use. Picking an unused port."); //$NON-NLS-1$
          devServerRunConfiguration.setAdminPort(0);
        }
      }
    }

    // TODO: apiPort?
    // vmArguments is exactly as supplied by the user in the dialog box
    String vmArgumentString = getVMArguments(configuration);
    List<String> vmArguments = Arrays.asList(DebugPlugin.parseArguments(vmArgumentString));
    if (!vmArguments.isEmpty()) {
      devServerRunConfiguration.setJvmFlags(vmArguments);
    }
    // programArguments is exactly as supplied by the user in the dialog box
    String programArgumentString = getProgramArguments(configuration);
    List<String> programArguments =
        Arrays.asList(DebugPlugin.parseArguments(programArgumentString));
    if (!programArguments.isEmpty()) {
      devServerRunConfiguration.setAdditionalArguments(programArguments);
    }

    boolean environmentAppend =
        configuration.getAttribute(ILaunchManager.ATTR_APPEND_ENVIRONMENT_VARIABLES, true);
    if (!environmentAppend) {
      // not externalized as this may change due to
      // https://github.com/GoogleCloudPlatform/appengine-plugins-core/issues/446
      throw new CoreException(
          StatusUtil.error(this, "'Environment > Replace environment' not yet supported")); //$NON-NLS-1$
    }
    // Could use getEnvironment(), but it does `append` processing and joins as key-value pairs
    Map<String, String> environment = configuration.getAttribute(
        ILaunchManager.ATTR_ENVIRONMENT_VARIABLES, Collections.<String, String>emptyMap());
    Map<String, String> expanded = new HashMap<>();
    IStringVariableManager variableEngine = VariablesPlugin.getDefault().getStringVariableManager();
    for (Map.Entry<String, String> entry : environment.entrySet()) {
      // expand any variable references
      expanded.put(entry.getKey(), variableEngine.performStringSubstitution(entry.getValue()));
    }
    devServerRunConfiguration.setEnvironment(expanded);

    return devServerRunConfiguration;
  }

  /**
   * Retrieve and resolve a string attribute. If not specified, returns {@code defaultValue}.
   */
  private static String getAttribute(String attributeName, String defaultValue,
      ILaunchConfiguration configuration, IServer server) {
    try {
      if (configuration.hasAttribute(attributeName)) {
        String result = configuration.getAttribute(attributeName, ""); //$NON-NLS-1$
        if (result != null) {
          return result;
        }
      }
    } catch (CoreException ex) {
      logger.log(Level.WARNING, "Unable to retrieve " + attributeName, ex); //$NON-NLS-1$
    }
    return server.getAttribute(attributeName, defaultValue);
  }

  /**
   * Resolve a host or IP address to an IP address.
   *
   * @return an {@link InetAddress}, or {@code null} if unable to be resolved (equivalent to
   *         {@code INADDR_ANY})
   */
  static InetAddress resolveAddress(String ipOrHost) {
    if (!Strings.isNullOrEmpty(ipOrHost)) {
      if (InetAddresses.isInetAddress(ipOrHost)) {
        return InetAddresses.forString(ipOrHost);
      }
      try {
        InetAddress[] addresses = InetAddress.getAllByName(ipOrHost);
        return addresses[0];
      } catch (UnknownHostException ex) {
        logger.info("Unable to resolve '" + ipOrHost + "' to an address"); //$NON-NLS-1$ //$NON-NLS-2$
      }
    }
    return null;
  }

  /**
   * Pull out a port from the specified attribute on the given {@link ILaunchConfiguration} or
   * {@link IServer} instance.
   *
   * @param defaultPort the port if no port attributes are found
   * @return the port, or {@code defaultPort} if no port was found
   */
  @VisibleForTesting
  static int getPortAttribute(String attributeName, int defaultPort,
      ILaunchConfiguration configuration, IServer server) {
    int port = -1;
    try {
      port = configuration.getAttribute(attributeName, -1);
    } catch (CoreException ex) {
      logger.log(Level.WARNING, "Unable to retrieve " + attributeName, ex); //$NON-NLS-1$
    }
    if (port < 0) {
      port = server.getAttribute(attributeName, defaultPort);
    }
    return port;
  }

  /**
   * Check for known conflicting settings.
   */
  @VisibleForTesting
  static IStatus checkConflicts(RunConfiguration ours, RunConfiguration theirs,
      MultiStatus status) {
    Class<?> clazz = LocalAppEngineServerLaunchConfigurationDelegate.class;
    // use {0,number,#} to avoid localized port numbers
    if (equalPorts(ours.getPort(), theirs.getPort(),
        LocalAppEngineServerBehaviour.DEFAULT_SERVER_PORT)) {
      status.add(StatusUtil.error(clazz,
          Messages.getString("server.port", //$NON-NLS-1$
              ifNull(ours.getPort(), LocalAppEngineServerBehaviour.DEFAULT_SERVER_PORT))));
    }
    if (equalPorts(ours.getApiPort(), theirs.getApiPort(), 0)) {
      // ours.getAdminPort() will never be null with a 0 default
      Preconditions.checkNotNull(ours.getApiPort());
      status.add(StatusUtil.error(clazz, Messages.getString("api.port", ours.getAdminPort()))); //$NON-NLS-1$
    }

    return status;
  }

  /** Compare whether two port specs map to the same port. */
  @VisibleForTesting
  static boolean equalPorts(Integer ours, Integer theirs, int defaultValue) {
    if (ours == null) {
      ours = defaultValue;
    }
    if (theirs == null) {
      theirs = defaultValue;
    }
    if (ours == 0 || theirs == 0) {
      return false;
    }
    return ours.equals(theirs);
  }

  @Override
  public void launch(ILaunchConfiguration configuration, String mode, final ILaunch launch,
      IProgressMonitor monitor) throws CoreException {
    AnalyticsPingManager.getInstance().sendPing(AnalyticsEvents.APP_ENGINE_LOCAL_SERVER,
        AnalyticsEvents.APP_ENGINE_LOCAL_SERVER_MODE, mode);

    IServer server = ServerUtil.getServer(configuration);
    if (server == null) {
      String message = Messages.getString("devappserver.not.available"); //$NON-NLS-1$
      Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, message);
      throw new CoreException(status);
    } else if (server.getServerState() != IServer.STATE_STOPPED) {
      String message = Messages.getString("server.already.in.operation"); //$NON-NLS-1$
      Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, message);
      throw new CoreException(status);
    }
    IModule[] modules = server.getModules();
    if (modules == null || modules.length == 0) {
      return;
    }

    LocalAppEngineServerBehaviour serverBehaviour = (LocalAppEngineServerBehaviour) server
        .loadAdapter(LocalAppEngineServerBehaviour.class, null);

    setDefaultSourceLocator(launch, configuration);

    List<File> runnables = new ArrayList<>();
    for (IModule module : modules) {
      IPath deployPath = serverBehaviour.getModuleDeployDirectory(module);
      runnables.add(deployPath.toFile());
    }

    // configure the console for output
    IPreferenceStore store = DebugUIPlugin.getDefault().getPreferenceStore();
    ConsoleColorProvider colorProvider = new ConsoleColorProvider();
    LocalAppEngineConsole console = MessageConsoleUtilities.findOrCreateConsole(
        configuration.getName(), new LocalAppEngineConsole.Factory(serverBehaviour));
    console.clearConsole();
    console.activate();
    MessageConsoleStream outputStream = console.newMessageStream();
    outputStream.setColor(colorProvider.getColor(IDebugUIConstants.ID_STANDARD_OUTPUT_STREAM));
    outputStream
        .setActivateOnWrite(store.getBoolean(IDebugPreferenceConstants.CONSOLE_OPEN_ON_OUT));
    MessageConsoleStream errorStream = console.newMessageStream();
    errorStream.setColor(colorProvider.getColor(IDebugUIConstants.ID_STANDARD_ERROR_STREAM));
    errorStream.setActivateOnWrite(store.getBoolean(IDebugPreferenceConstants.CONSOLE_OPEN_ON_ERR));

    // A launch must have at least one debug target or process, or it becomes a zombie
    CloudSdkDebugTarget target = new CloudSdkDebugTarget(launch, serverBehaviour, console);
    launch.addDebugTarget(target);
    target.engage();

    // todo: programArguments is currently ignored
    if (!Strings.isNullOrEmpty(getProgramArguments(configuration))) {
      logger.warning("App Engine Local Server currently ignores program arguments"); //$NON-NLS-1$
    }
    try {
      DefaultRunConfiguration devServerRunConfiguration =
          generateServerRunConfiguration(configuration, server, mode);
      devServerRunConfiguration.setServices(runnables);
      if (ILaunchManager.DEBUG_MODE.equals(mode)) {
        int debugPort = getDebugPort();
        setupDebugTarget(devServerRunConfiguration, launch, debugPort, monitor);
      }

      IJavaProject javaProject = JavaCore.create(modules[0].getProject());
      IVMInstall vmInstall = JavaRuntime.getVMInstall(javaProject);

      Path javaHome = vmInstall.getInstallLocation().toPath();
      serverBehaviour.startDevServer(mode, devServerRunConfiguration, javaHome,
          outputStream, errorStream);
    } catch (CoreException ex) {
      launch.terminate();
      throw ex;
    }
  }

  /**
   * Listen for the server to enter STARTED and open a web browser on the server's main page
   */
  protected void openBrowserPage(final IServer server) {
    if (!shouldOpenStartPage()) {
      return;
    }
    final String pageLocation = determinePageLocation(server);
    if (pageLocation == null) {
      return;
    }
    WorkbenchUtil.openInBrowserInUiThread(pageLocation, server.getId(), server.getName(),
        server.getName());
  }

  private void setupDebugTarget(DefaultRunConfiguration devServerRunConfiguration, ILaunch launch,
      int debugPort, IProgressMonitor monitor) throws CoreException {
    if (debugPort <= 0 || debugPort > 65535) {
      throw new IllegalArgumentException("Debug port is set to " + debugPort //$NON-NLS-1$
          + ", should be between 1-65535"); //$NON-NLS-1$
    }
    List<String> jvmFlags = new ArrayList<>();
    if (devServerRunConfiguration.getJvmFlags() != null) {
      jvmFlags.addAll(devServerRunConfiguration.getJvmFlags());
    }
    jvmFlags.add("-Xdebug"); //$NON-NLS-1$
    jvmFlags.add("-Xrunjdwp:transport=dt_socket,server=n,suspend=y,quiet=y,address=" + debugPort); //$NON-NLS-1$
    devServerRunConfiguration.setJvmFlags(jvmFlags);

    // The 4.7 listen connector supports a connectionLimit
    IVMConnector connector =
        JavaRuntime.getVMConnector(IJavaLaunchConfigurationConstants.ID_SOCKET_LISTEN_VM_CONNECTOR);
    if (connector == null || !connector.getArgumentOrder().contains("connectionLimit")) { //$NON-NLS-1$
      // Attempt to retrieve our socketListenerMultipleConnector
      connector = JavaRuntime.getVMConnector(
          "com.google.cloud.tools.eclipse.jdt.launching.socketListenerMultipleConnector"); //$NON-NLS-1$
    }
    if (connector == null) {
      abort("Cannot find Socket Listening connector", null, 0); //$NON-NLS-1$
      return; // keep JDT null analysis happy
    }

    // Set JVM debugger connection parameters
    @SuppressWarnings("deprecation")
    int timeout = JavaRuntime.getPreferences().getInt(JavaRuntime.PREF_CONNECT_TIMEOUT);
    Map<String, String> connectionParameters = new HashMap<>();
    connectionParameters.put("hostname", DEBUGGER_HOST); //$NON-NLS-1$
    connectionParameters.put("port", Integer.toString(debugPort)); //$NON-NLS-1$
    connectionParameters.put("timeout", Integer.toString(timeout)); //$NON-NLS-1$
    connectionParameters.put("connectionLimit", "0"); //$NON-NLS-1$ //$NON-NLS-2$
    connector.connect(connectionParameters, monitor, launch);
  }

  private int getDebugPort() throws CoreException {
    int port = SocketUtil.findFreePort();
    if (port == -1) {
      abort("Cannot find free port for remote debugger", null, IStatus.ERROR); //$NON-NLS-1$
    }
    return port;
  }

  /**
   * @return true if we should open a browser on the start page on successful launch
   */
  private boolean shouldOpenStartPage() {
    return Platform.getPreferencesService().getBoolean(Activator.PLUGIN_ID,
        PreferencesInitializer.LAUNCH_BROWSER, true, null);
  }

  @VisibleForTesting
  static String determinePageLocation(IServer server) {
    LocalAppEngineServerBehaviour serverBehaviour = (LocalAppEngineServerBehaviour)
        server.loadAdapter(LocalAppEngineServerBehaviour.class, null /* monitor */);

    return "http://" + server.getHost() + ":" + serverBehaviour.getServerPort(); //$NON-NLS-1$ //$NON-NLS-2$
  }

  @Override
  protected IProject[] getBuildOrder(ILaunchConfiguration configuration, String mode)
      throws CoreException {
    IProject[] projects = getReferencedProjects(configuration);
    return computeBuildOrder(projects);
  }

  @Override
  protected IProject[] getProjectsForProblemSearch(ILaunchConfiguration configuration, String mode)
      throws CoreException {
    IProject[] projects = getReferencedProjects(configuration);
    return computeReferencedBuildOrder(projects);
  }

  private static IProject[] getReferencedProjects(ILaunchConfiguration configuration)
      throws CoreException {
    IServer thisServer = ServerUtil.getServer(configuration);
    IModule[] modules = ModuleUtils.getAllModules(thisServer);
    Set<IProject> projects = new HashSet<>();
    for (IModule module : modules) {
      IProject project = module.getProject();
      if (project != null) {
        projects.add(project);
      }
    }
    return projects.toArray(new IProject[projects.size()]);
  }

  /**
   * Monitors the server and launch state. Ensures listeners are properly removed on server stop and
   * launch termination. It is necessary in part as there may be several launches per
   * LaunchConfigurationDelegate.
   * <ul>
   * <li>On server start, open a browser page</li>
   * <li>On launch-termination, stop the server.</li>
   * <li>On server-stop, terminate the launch</li>
   * </ul>
   */
  private class CloudSdkDebugTarget extends DebugElement implements IDebugTarget {
    private final ILaunch launch;
    private final LocalAppEngineServerBehaviour serverBehaviour;
    private final IServer server;
    private final IOConsole console;

    // Fire a {@link DebugEvent#TERMINATED} event when the server is stopped
    private IServerListener serverEventsListener = new IServerListener() {
      @Override
      public void serverChanged(ServerEvent event) {
        Preconditions.checkState(server == event.getServer());
        switch (event.getState()) {
          case IServer.STATE_STARTED:
            openBrowserPage(server);
            fireChangeEvent(DebugEvent.STATE);
            return;

          case IServer.STATE_STOPPED:
            server.removeServerListener(serverEventsListener);
            fireTerminateEvent();
            try {
              logger.fine("Server stopped; terminating launch"); //$NON-NLS-1$
              launch.terminate();
            } catch (DebugException ex) {
              logger.log(Level.WARNING, "Unable to terminate launch", ex); //$NON-NLS-1$
            }
            return;

          default:
            fireChangeEvent(DebugEvent.STATE);
            return;
        }
      }
    };

    private ILaunchesListener2 launchesListener = new ILaunchesListener2() {
      @Override
      public void launchesTerminated(ILaunch[] launches) {
        for (ILaunch terminated : launches) {
          if (terminated == launch) {
            if (server.getServerState() == IServer.STATE_STARTED) {
              logger.fine("Launch terminated; stopping server"); //$NON-NLS-1$
              server.stop(false);
            }
            return;
          }
        }
      }

      @Override
      public void launchesAdded(ILaunch[] launches) {}

      @Override
      public void launchesChanged(ILaunch[] launches) {}

      @Override
      public void launchesRemoved(ILaunch[] launches) {
        for (ILaunch removed : launches) {
          if (removed == launch) {
            getLaunchManager().removeLaunchListener(launchesListener);
            removeConsole();
          }
        }
      }
    };

    private CloudSdkDebugTarget(ILaunch launch, LocalAppEngineServerBehaviour serverBehaviour,
        IOConsole console) {
      super(null);
      this.launch = launch;
      this.serverBehaviour = serverBehaviour;
      server = serverBehaviour.getServer();
      this.console = console;
    }

    private void removeConsole() {
      ConsolePlugin plugin = ConsolePlugin.getDefault();
      IConsoleManager manager = plugin.getConsoleManager();
      manager.removeConsoles(new IConsole[] {console});
      console.destroy();
    }

    /** Add required listeners */
    private void engage() {
      getLaunchManager().addLaunchListener(launchesListener);
      server.addServerListener(serverEventsListener);
    }

    @Override
    public String getName() throws DebugException {
      return serverBehaviour.getDescription();
    }

    /**
     * Returns an identifier that maps to our
     * {@link com.google.cloud.tools.eclipse.appengine.localserver.ui.CloudSdkDebugTargetPresentation
     * presentation} via the {@code org.eclipse.debug.ui.debugModelPresentations} extension point.
     */
    @Override
    public String getModelIdentifier() {
      return "com.google.cloud.tools.eclipse.appengine.localserver.cloudSdkDebugTarget"; //$NON-NLS-1$
    }

    @Override
    public IDebugTarget getDebugTarget() {
      return this;
    }

    @Override
    public ILaunch getLaunch() {
      return launch;
    }

    @Override
    public boolean canTerminate() {
      return true;
    }

    @Override
    public boolean isTerminated() {
      int state = server.getServerState();
      return state == IServer.STATE_STOPPED;
    }

    @Override
    public void terminate() throws DebugException {
      int state = server.getServerState();
      if (state != IServer.STATE_STOPPED) {
        serverBehaviour.stop(state == IServer.STATE_STOPPING);
      }
    }

    @Override
    public boolean supportsBreakpoint(IBreakpoint breakpoint) {
      return false;
    }

    @Override
    public void breakpointAdded(IBreakpoint breakpoint) {}

    @Override
    public void breakpointRemoved(IBreakpoint breakpoint, IMarkerDelta delta) {}

    @Override
    public void breakpointChanged(IBreakpoint breakpoint, IMarkerDelta delta) {}

    @Override
    public boolean canResume() {
      return false;
    }

    @Override
    public void resume() throws DebugException {}

    @Override
    public boolean canSuspend() {
      return false;
    }

    @Override
    public boolean isSuspended() {
      return false;
    }

    @Override
    public void suspend() throws DebugException {}

    @Override
    public boolean canDisconnect() {
      return false;
    }

    @Override
    public boolean isDisconnected() {
      return false;
    }

    @Override
    public void disconnect() throws DebugException {}

    @Override
    public boolean supportsStorageRetrieval() {
      return false;
    }

    @Override
    public IMemoryBlock getMemoryBlock(long startAddress, long length) throws DebugException {
      return null;
    }

    @Override
    public IProcess getProcess() {
      return null;
    }

    @Override
    public boolean hasThreads() throws DebugException {
      return false;
    }

    @Override
    public IThread[] getThreads() throws DebugException {
      return new IThread[0];
    }
  }
}
