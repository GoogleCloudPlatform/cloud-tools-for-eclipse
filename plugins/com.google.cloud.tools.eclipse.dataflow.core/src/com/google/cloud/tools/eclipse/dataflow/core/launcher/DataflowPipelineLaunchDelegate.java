/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.eclipse.dataflow.core.launcher;

import com.google.api.client.auth.oauth2.Credential;
import com.google.cloud.tools.eclipse.dataflow.core.DataflowCorePlugin;
import com.google.cloud.tools.eclipse.dataflow.core.launcher.options.PipelineOptionsHierarchy;
import com.google.cloud.tools.eclipse.dataflow.core.preferences.DataflowPreferences;
import com.google.cloud.tools.eclipse.dataflow.core.preferences.ProjectOrWorkspaceDataflowPreferences;
import com.google.cloud.tools.eclipse.dataflow.core.project.DataflowDependencyManager;
import com.google.cloud.tools.eclipse.dataflow.core.project.MajorVersion;
import com.google.cloud.tools.eclipse.login.CredentialHelper;
import com.google.cloud.tools.eclipse.login.IGoogleLoginService;
import com.google.cloud.tools.eclipse.usagetracker.AnalyticsEvents;
import com.google.cloud.tools.eclipse.usagetracker.AnalyticsPingManager;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate2;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.JavaLaunchDelegate;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

/**
 * The {@link ILaunchConfigurationDelegate} to launch a Google Cloud Dataflow Pipeline.
 *
 * <p>The JavaLaunchDelegate is responsible for most of the launching.
 */
public class DataflowPipelineLaunchDelegate implements ILaunchConfigurationDelegate2 {
  private static final String ARGUMENT_FORMAT_STR = "--%s=%s";

  @VisibleForTesting
  static final String DATAFLOW_LAUNCH_CONFIG_WORKING_COPY_PREFIX =
      "dataflow_tmp_config_working_copy-";
  @VisibleForTesting
  static final String RUNNER_COMMAND_LINE_STRING = "runner";
  @VisibleForTesting
  static final String GOOGLE_APPLICATION_CREDENTIALS_ENVIRONMENT_VARIABLE =
      "GOOGLE_APPLICATION_CREDENTIALS";

  private static final Joiner SPACE_JOINER = Joiner.on(" ").skipNulls();

  private final JavaLaunchDelegate delegate;
  private final PipelineOptionsHierarchyFactory optionsRetrieverFactory;
  private final IWorkspaceRoot workspaceRoot;
  private final DataflowDependencyManager dependencyManager;
  private final IGoogleLoginService loginService;

  public DataflowPipelineLaunchDelegate() {
    this(
        new JavaLaunchDelegate(),
        new ClasspathPipelineOptionsHierarchyFactory(),
        DataflowDependencyManager.create(),
        ResourcesPlugin.getWorkspace().getRoot(),
        getLoginService());
  }

  @VisibleForTesting
  DataflowPipelineLaunchDelegate(
      JavaLaunchDelegate javaLaunchDelegate,
      PipelineOptionsHierarchyFactory optionsHierarchyFactory,
      DataflowDependencyManager dependencyManager,
      IWorkspaceRoot workspaceRoot,
      IGoogleLoginService loginService) {
    delegate = javaLaunchDelegate;
    optionsRetrieverFactory = optionsHierarchyFactory;
    this.dependencyManager = dependencyManager;
    this.workspaceRoot = workspaceRoot;
    this.loginService = loginService;
  }

  @Override
  public void launch(
      ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
      throws CoreException {
    SubMonitor progress = SubMonitor.convert(monitor, 3);

    IProject project = getProject(configuration);
    MajorVersion majorVersion = dependencyManager.getProjectMajorVersion(project);

    PipelineOptionsHierarchy hierarchy;
    try {
      hierarchy = optionsRetrieverFactory.forProject(project, majorVersion, progress.split(1));
    } catch (PipelineOptionsRetrievalException e) {
      throw new CoreException(new Status(Status.ERROR, DataflowCorePlugin.PLUGIN_ID,
          "Could not retrieve Pipeline Options Hierarchy for project " + project.getName(), e));
    }

    PipelineLaunchConfiguration pipelineConfig =
        PipelineLaunchConfiguration.fromLaunchConfiguration(majorVersion, configuration);

    DataflowPreferences preferences = ProjectOrWorkspaceDataflowPreferences.forProject(project);
    if (!pipelineConfig.isValid(hierarchy, preferences)) {
      throw new IllegalArgumentException(
          "Provided Dataflow Pipeline Configuration is not valid: " + pipelineConfig.toString());
    }

    Map<String, String> effectiveArguments = getEffectiveArguments(pipelineConfig, preferences);

    List<String> argComponents =
        getArguments(configuration, pipelineConfig, hierarchy, effectiveArguments);

    ILaunchConfigurationWorkingCopy workingCopy =
        configuration.copy(DATAFLOW_LAUNCH_CONFIG_WORKING_COPY_PREFIX + configuration.getName());
    workingCopy.setAttribute(
        IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, SPACE_JOINER.join(argComponents));

    String accountEmail = effectiveArguments.get("accountEmail");
    setLoginCredential(workingCopy, accountEmail);

    PipelineRunner pipelineRunner = pipelineConfig.getRunner();
    AnalyticsPingManager.getInstance().sendPing(AnalyticsEvents.DATAFLOW_RUN,
        AnalyticsEvents.DATAFLOW_RUN_RUNNER, pipelineRunner.getRunnerName());

    delegate.launch(workingCopy, mode, launch, progress.split(1));
  }

  private IProject getProject(ILaunchConfiguration configuration) throws CoreException {
    String projectName =
        configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "");
    if (projectName.isEmpty()) {
      throw new CoreException(StatusUtil.error(this, "Cannot determine project"));
    }
    IProject project = workspaceRoot.getProject(projectName);
    if (!project.exists()) {
      String errorMessage = String.format("Project \"%s\" does not exist", projectName);
      throw new CoreException(StatusUtil.error(this, errorMessage));
    }
    return project;
  }

  @VisibleForTesting
  void setLoginCredential(ILaunchConfigurationWorkingCopy workingCopy, String accountEmail)
      throws CoreException {
    Preconditions.checkNotNull(accountEmail,
        "account email missing in launch configuration or preferences");

    try {
      if (accountEmail.isEmpty()) {
        String message = "No Google account selected for this launch.";
        throw new CoreException(new Status(Status.ERROR, DataflowCorePlugin.PLUGIN_ID, message));
      }

      Credential credential = loginService.getCredential(accountEmail);
      if (credential == null) {
        String message = "The Google account saved for this lanuch is not logged in.";
        throw new CoreException(new Status(Status.ERROR, DataflowCorePlugin.PLUGIN_ID, message));
      }

      // Dataflow SDK doesn't yet support reading credentials from an arbitrary JSON, so we use the
      // workaround of setting the "GOOGLE_APPLICATION_CREDENTIALS" environment variable.
      Map<String, String> variableMap = workingCopy.getAttribute(
          ILaunchManager.ATTR_ENVIRONMENT_VARIABLES, new HashMap<String, String>());
      if (variableMap.containsKey(GOOGLE_APPLICATION_CREDENTIALS_ENVIRONMENT_VARIABLE)) {
        String message = "You cannot define the environment variable GOOGLE_APPLICATION_CREDENTIALS"
            + " when launching Dataflow pipelines from Cloud Tools for Eclipse.";
        throw new CoreException(new Status(Status.ERROR, DataflowCorePlugin.PLUGIN_ID, message));
      }

      Path jsonCredential = Files.createTempFile("google-ct4e-" + workingCopy.getName(), ".json");
      CredentialHelper.toJsonFile(credential, jsonCredential);
      jsonCredential.toFile().deleteOnExit();

      variableMap.put(GOOGLE_APPLICATION_CREDENTIALS_ENVIRONMENT_VARIABLE,
          jsonCredential.toAbsolutePath().toString());
      workingCopy.setAttribute(ILaunchManager.ATTR_ENVIRONMENT_VARIABLES, variableMap);
    } catch (IOException ex) {
      throw new CoreException(
          new Status(Status.ERROR, DataflowCorePlugin.PLUGIN_ID, ex.getMessage(), ex));
    }
  }

  private static IGoogleLoginService getLoginService() {
    BundleContext bundleContext =
        FrameworkUtil.getBundle(DataflowPipelineLaunchDelegate.class).getBundleContext();
    return bundleContext.getService(bundleContext.getServiceReference(IGoogleLoginService.class));
  }

  private List<String> getArguments(
      ILaunchConfiguration configuration,
      PipelineLaunchConfiguration pipelineConfig,
      PipelineOptionsHierarchy optionsHierarchy,
      Map<String, String> effectiveArguments)
      throws CoreException {
    List<String> argComponents = new ArrayList<>();

    PipelineRunner runner = pipelineConfig.getRunner();
    argComponents.add(String.format(ARGUMENT_FORMAT_STR, RUNNER_COMMAND_LINE_STRING,
        runner.getRunnerName()));

    Set<String> pipelineArgs;
    if (!Strings.isNullOrEmpty(pipelineConfig.getUserOptionsName())) {
      pipelineArgs = optionsHierarchy.getPropertyNames(
          runner.getOptionsClass(), pipelineConfig.getUserOptionsName());
    } else {
      pipelineArgs =
          optionsHierarchy.getPropertyNames(runner.getOptionsClass());
    }

    for (Map.Entry<String, String> argValueEntry : effectiveArguments.entrySet()) {
      if (!Strings.isNullOrEmpty(argValueEntry.getValue())
          && pipelineArgs.contains(argValueEntry.getKey())) {
        argComponents.add(
            String.format(ARGUMENT_FORMAT_STR, argValueEntry.getKey(), argValueEntry.getValue()));
      }
    }

    argComponents.add(
        configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, ""));
    return argComponents;
  }

  /**
   * Returns effective argument values of {@link PipelineLaunchConfiguration} that take into
   * account the project-specific or workspace-wide default values when needed.
   */
  private Map<String, String> getEffectiveArguments(
      PipelineLaunchConfiguration pipelineConfig, DataflowPreferences preferences) {
    Map<String, String> argumentValues = new HashMap<>(pipelineConfig.getArgumentValues());
    if (pipelineConfig.isUseDefaultLaunchOptions()) {
      argumentValues.putAll(preferences.asDefaultPropertyMap());
    }
    return argumentValues;
  }

  @Override
  public ILaunch getLaunch(ILaunchConfiguration configuration, String mode) throws CoreException {
    return delegate.getLaunch(configuration, mode);
  }

  @Override
  public boolean buildForLaunch(ILaunchConfiguration configuration, String mode,
      IProgressMonitor monitor) throws CoreException {
    return delegate.buildForLaunch(configuration, mode, monitor);
  }

  @Override
  public boolean finalLaunchCheck(ILaunchConfiguration configuration, String mode,
      IProgressMonitor monitor) throws CoreException {
    return delegate.finalLaunchCheck(configuration, mode, monitor);
  }

  @Override
  public boolean preLaunchCheck(ILaunchConfiguration configuration, String mode,
      IProgressMonitor monitor) throws CoreException {
    return delegate.preLaunchCheck(configuration, mode, monitor);
  }
}
