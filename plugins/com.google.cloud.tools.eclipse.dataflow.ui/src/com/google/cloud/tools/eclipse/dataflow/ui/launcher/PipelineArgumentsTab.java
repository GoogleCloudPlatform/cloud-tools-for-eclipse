/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.eclipse.dataflow.ui.launcher;

import com.google.cloud.tools.eclipse.dataflow.core.launcher.ClasspathPipelineOptionsHierarchyFactory;
import com.google.cloud.tools.eclipse.dataflow.core.launcher.PipelineLaunchConfiguration;
import com.google.cloud.tools.eclipse.dataflow.core.launcher.PipelineLaunchConfiguration.MissingRequiredProperties;
import com.google.cloud.tools.eclipse.dataflow.core.launcher.PipelineOptionsHierarchyFactory;
import com.google.cloud.tools.eclipse.dataflow.core.launcher.PipelineOptionsRetrievalException;
import com.google.cloud.tools.eclipse.dataflow.core.launcher.PipelineRunner;
import com.google.cloud.tools.eclipse.dataflow.core.launcher.options.PipelineOptionsHierarchy;
import com.google.cloud.tools.eclipse.dataflow.core.launcher.options.PipelineOptionsProperty;
import com.google.cloud.tools.eclipse.dataflow.core.launcher.options.PipelineOptionsType;
import com.google.cloud.tools.eclipse.dataflow.core.preferences.DataflowPreferences;
import com.google.cloud.tools.eclipse.dataflow.core.preferences.ProjectOrWorkspaceDataflowPreferences;
import com.google.cloud.tools.eclipse.dataflow.core.project.DataflowDependencyManager;
import com.google.cloud.tools.eclipse.dataflow.core.project.MajorVersion;
import com.google.cloud.tools.eclipse.dataflow.ui.DataflowUiPlugin;
import com.google.cloud.tools.eclipse.dataflow.ui.Messages;
import com.google.cloud.tools.eclipse.dataflow.ui.page.MessageTarget;
import com.google.cloud.tools.eclipse.dataflow.ui.page.component.LabeledTextMapComponent;
import com.google.cloud.tools.eclipse.dataflow.ui.page.component.TextAndButtonComponent;
import com.google.cloud.tools.eclipse.dataflow.ui.page.component.TextAndButtonSelectionListener;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.events.IExpansionListener;

/**
 * A tab specifying arguments required to run a Dataflow Pipeline.
 * 
 * Computing the pipeline options hierarchy can be expensive, so we try to avoid doing so.
 * {@link #reload(ILaunchConfiguration)} is responsible for loading information derived from an
 * {@link ILaunchConfiguration}.
 */
public class PipelineArgumentsTab extends AbstractLaunchConfigurationTab {
  private static final Joiner MISSING_GROUP_MEMBER_JOINER = Joiner.on(", "); //$NON-NLS-1$

  private static final String ARGUMENTS_SEPARATOR = "="; //$NON-NLS-1$

  private final IWorkspaceRoot workspaceRoot;

  /**
   * When true, suppresses calls to {@link #updateLaunchConfigurationDialog()} to avoid frequent
   * updates during batch UI changes.
   */
  @VisibleForTesting
  boolean suppressDialogUpdates = false;

  @VisibleForTesting
  UpdateLaunchConfigurationDialogChangedListener dialogChangedListener =
      new UpdateLaunchConfigurationDialogChangedListener();

  private ScrolledComposite composite;
  private Composite internalComposite;

  @VisibleForTesting
  Map<PipelineRunner, Button> runnerButtons;
  private Group runnerGroup;

  @VisibleForTesting
  DefaultedPipelineOptionsComponent defaultOptionsComponent;

  @VisibleForTesting
  TextAndButtonComponent userOptionsSelector;
  private PipelineOptionsFormComponent pipelineOptionsForm;

  private final DataflowDependencyManager dependencyManager;
  private final PipelineOptionsHierarchyFactory pipelineOptionsHierarchyFactory =
      new ClasspathPipelineOptionsHierarchyFactory();

  private IProject project;
  private PipelineLaunchConfiguration launchConfiguration;

  /*
   * TODO: By default, this may include all PipelineOptions types, including custom user types that
   * are not present in the project that this PipelineArgumentsTab is trying to launch. This
   * hierarchy should be restricted to only showing options available from the current project, if
   * able.
   */
  private PipelineOptionsHierarchy hierarchy;

  /** Set to {@code true} when this tab has been shown, and reset upon a new config. */
  private boolean uiUpToDate = false;

  public PipelineArgumentsTab() {
    this(ResourcesPlugin.getWorkspace().getRoot(), DataflowDependencyManager.create());
  }

  @VisibleForTesting
  PipelineArgumentsTab(IWorkspaceRoot workspaceRoot, DataflowDependencyManager dependencyManager) {
    this.workspaceRoot = workspaceRoot;
    this.dependencyManager = dependencyManager;
    hierarchy = pipelineOptionsHierarchyFactory.global(new NullProgressMonitor());
  }

  @Override
  public void createControl(Composite parent) {
    composite = new ScrolledComposite(parent, SWT.V_SCROLL);
    composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    composite.setLayout(new GridLayout(1, false));

    internalComposite = new Composite(composite, SWT.NULL);

    GridData internalCompositeGridData = new GridData(SWT.FILL, SWT.FILL, true, true);
    internalComposite.setLayoutData(internalCompositeGridData);
    internalComposite.setLayout(new GridLayout(1, false));

    runnerGroup = new Group(internalComposite, SWT.NULL);
    runnerGroup.setText(Messages.getString("runner")); //$NON-NLS-1$
    runnerGroup.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
    runnerGroup.setLayout(new GridLayout(2, false));

    createDefaultOptionsComponent(internalComposite, new GridData(SWT.FILL, SWT.FILL, true, false));

    Composite inputsComposite = new Composite(internalComposite, SWT.NULL);
    inputsComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    inputsComposite.setLayout(new FillLayout(SWT.VERTICAL));

    Set<String> filterProperties =
        ImmutableSet.<String>builder()
            .addAll(DataflowPreferences.SUPPORTED_DEFAULT_PROPERTIES)
            .add("runner") //$NON-NLS-1$
            .build();

    Group runnerOptionsGroup = new Group(inputsComposite, SWT.NULL);
    runnerOptionsGroup.setText(Messages.getString("pipeline.options")); //$NON-NLS-1$
    runnerOptionsGroup.setLayout(new GridLayout());

    userOptionsSelector = new TextAndButtonComponent(
        runnerOptionsGroup,
        new GridData(SWT.FILL, SWT.BEGINNING, true, false), 
        Messages.getString("search")); //$NON-NLS-1$
    userOptionsSelector.addButtonSelectionListener(openPipelineOptionsSearchListener());

    pipelineOptionsForm =
        new PipelineOptionsFormComponent(runnerOptionsGroup, ARGUMENTS_SEPARATOR, filterProperties);
    pipelineOptionsForm.addModifyListener(dialogChangedListener);
    pipelineOptionsForm.addExpandListener(dialogChangedListener);

    composite.setContent(internalComposite);
    composite.setExpandHorizontal(true);
    composite.setExpandVertical(true);
    composite.setMinSize(inputsComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    composite.setShowFocusedControl(true);
    composite.pack(true);

    setControl(composite);
  }

  private TextAndButtonSelectionListener openPipelineOptionsSearchListener() {
    return new TextAndButtonSelectionListener() {
      @Override
      public void widgetSelected(SelectionEvent event) {
        Map<String, PipelineOptionsType> optionsTypes = hierarchy.getAllPipelineOptionsTypes();
        PipelineOptionsSelectionDialog dialog =
            new PipelineOptionsSelectionDialog(getShell(), optionsTypes);
        dialog.setBlockOnOpen(true);
        dialog.setInitialPattern("**"); //$NON-NLS-1$
        if (dialog.open() == Window.OK) {
          String userOptionsName = dialog.getFirstResult().toString();
          setTextValue(userOptionsName);
          launchConfiguration.setUserOptionsName(userOptionsName);
        }
        updatePipelineOptionsForm();
        updateLaunchConfigurationDialog();
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent event) {}
    };
  }

  private void populateRunners(MajorVersion majorVersion) {
    clearRunners();
    // TODO: Retrieve automatically instead of from a hardcoded map
    for (PipelineRunner runner : PipelineRunner.inMajorVersion(majorVersion)) {
      Button runnerButton = createRunnerButton(runnerGroup, runner);
      runnerButton.addSelectionListener(
          new UpdateLaunchConfigAndRequiredArgsSelectionListener(runner));
      runnerButtons.put(runner, runnerButton);
    }
  }

  private void clearRunners() {
    for (Control ctl : runnerGroup.getChildren()) {
      ctl.dispose();
    }
    runnerButtons = new HashMap<>();
  }

  private Button createRunnerButton(Group runnerSelectorGroup, PipelineRunner runner) {
    Button radio = new Button(runnerSelectorGroup, SWT.RADIO);
    radio.setText(runner.getRunnerName());
    radio.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
    Label description = new Label(runnerSelectorGroup, SWT.NULL);
    description.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
    description.setText(runner.getDescription());
    return radio;
  }

  private void createDefaultOptionsComponent(Composite composite, GridData layoutData) {
    MessageTarget target = new MessageTarget() {
      @Override
      public void setInfo(String message) {
        setMessage(message);
        getLaunchConfigurationDialog().updateMessage();
      }

      @Override
      public void setError(String message) {
        setErrorMessage(message);
        getLaunchConfigurationDialog().updateMessage();
      }

      @Override
      public void clear() {
        setErrorMessage(null);
        setMessage(null);
        getLaunchConfigurationDialog().updateMessage();
      }
    };

    defaultOptionsComponent =
        new DefaultedPipelineOptionsComponent(composite, layoutData, target, getPreferences());

    defaultOptionsComponent.addAccountSelectionListener(dialogChangedListener);
    defaultOptionsComponent.addButtonSelectionListener(dialogChangedListener);
    defaultOptionsComponent.addModifyListener(dialogChangedListener);
  }

  @Override
  public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {}

  @Override
  public void performApply(ILaunchConfigurationWorkingCopy configuration) {
    if (!uiUpToDate) {
      // uiUpToDate == false means initializeFrom() has not been called since
      // reload() was last called (on isValid() or initializeFrom()) and so
      // the UI elements are out of sync with this configuration.
      // Since isValid() must be true for performApply() to be called,
      // then we have no changes to apply.
      return;
    }
    PipelineRunner runner = getSelectedRunner();
    launchConfiguration.setRunner(runner);

    launchConfiguration.setUseDefaultLaunchOptions(defaultOptionsComponent.isUseDefaultOptions());

    Map<String, String> overallArgValues = new HashMap<>(launchConfiguration.getArgumentValues());
    if (!defaultOptionsComponent.isUseDefaultOptions()) {
      overallArgValues.putAll(defaultOptionsComponent.getValues());
    }
    overallArgValues.putAll(getNonDefaultOptions());
    launchConfiguration.setArgumentValues(overallArgValues);

    launchConfiguration.setUserOptionsName(userOptionsSelector.getText());

    launchConfiguration.toLaunchConfiguration(configuration);
  }

  @VisibleForTesting
  PipelineRunner getSelectedRunner() {
    for (Map.Entry<PipelineRunner, Button> runnerButton : runnerButtons.entrySet()) {
      if (runnerButton.getValue().getSelection()) {
        return runnerButton.getKey();
      }
    }
    throw new IllegalStateException("No runner selected, but a runner starts selected"); //$NON-NLS-1$
  }

  @Override
  public void initializeFrom(ILaunchConfiguration configuration) {
    reload(configuration);
    if (launchConfiguration == null) {
      // any errors are picked up and reported by isValid()
      clearRunners();
      defaultOptionsComponent.setEnabled(false);
      userOptionsSelector.setEnabled(false);
      pipelineOptionsForm.updateForm(null, null);
      return;
    }

    try {
      // the following requests update UI elements which normally triggers
      // updateLaunchConfigDialog(), which has a side-effect of calling
      // isValid(ILaunchConfiguration) and reloading from the ILaunchConfiguration
      suppressDialogUpdates = true;

      updateRunnerButtons(launchConfiguration);

      defaultOptionsComponent.setEnabled(true);
      defaultOptionsComponent.setUseDefaultValues(launchConfiguration.isUseDefaultLaunchOptions());
      defaultOptionsComponent.setPreferences(getPreferences());
      defaultOptionsComponent.setCustomValues(launchConfiguration.getArgumentValues());

      userOptionsSelector.setEnabled(true);
      String userOptionsName = launchConfiguration.getUserOptionsName();
      userOptionsSelector.setText(Strings.nullToEmpty(userOptionsName));
    } finally {
      suppressDialogUpdates = false;
    }
    updatePipelineOptionsForm();

    // updateLaunchConfigurationDialog() will call performApply() on the active tab
    // thus writing out the current UI state, like an updated runner
    uiUpToDate = true;
    updateLaunchConfigurationDialog();
  }

  /**
   * Reload any computed information only if the launch configuration has changed in some meaningful
   * way. This must be a fast check as this method is called from
   * {{@link #isValid(ILaunchConfiguration)}}, which is called frequently.
   * 
   * @return true if values were reloaded, or false if the configuration was up-to-date
   */
  @VisibleForTesting
  boolean reload(ILaunchConfiguration configuration) {
    try {
      // recompute the features of interest from the provided launch configuration
      IProject project = findProject(configuration);
      MajorVersion majorVersion = project == null || !project.isAccessible() ? null
          : dependencyManager.getProjectMajorVersion(project);
      PipelineLaunchConfiguration launchConfiguration = majorVersion == null ? null
          : PipelineLaunchConfiguration.fromLaunchConfiguration(majorVersion, configuration);
      if (Objects.equals(project, this.project)
          && Objects.equals(launchConfiguration, this.launchConfiguration)) {
        // our features of interest are the same
        return false;
      }
      this.project = project;
      this.launchConfiguration = launchConfiguration;
      updateHierarchy();
      uiUpToDate = false;
      return true;
    } catch (CoreException | InvocationTargetException | InterruptedException ex) {
      uiUpToDate = false;
      DataflowUiPlugin.logError(ex, "Error while initializing from existing configuration"); //$NON-NLS-1$
      project = null;
      launchConfiguration = null;
      return true; // values were changed
    }
  }

  /** Find the corresponding project or {@code null} if not found. */
  private final IProject findProject(ILaunchConfiguration configuration) {
    try {
      String eclipseProjectName =
          configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "");
    if (!Strings.isNullOrEmpty(eclipseProjectName)) {
        return workspaceRoot.getProject(eclipseProjectName);
      }
    } catch (CoreException ex) {
      DataflowUiPlugin.logWarning("Exception when determining project", ex); //$NON-NLS-1$
    }
    return null;
  }

  @VisibleForTesting
  void updateRunnerButtons(PipelineLaunchConfiguration configuration) {
    Preconditions.checkNotNull(configuration);
    MajorVersion majorVersion = configuration.getMajorVersion();
    populateRunners(majorVersion);
    for (Button button : runnerButtons.values()) {
      button.setSelection(false);
    }

    PipelineRunner runner = configuration.getRunner();
    if (!runner.getSupportedVersions().contains(majorVersion)) {
      // updates the selected button since it has an invalid runner
      runner = PipelineLaunchConfiguration.defaultRunner(majorVersion);
      configuration.setRunner(runner);
      DataflowUiPlugin.logInfo("Changed pipeline runner to '%s'", runner.getRunnerName());
    }
    Button runnerButton = runnerButtons.get(runner);
    Preconditions.checkNotNull(runnerButton,
        "runners for %s should always include %s", majorVersion, runner); //$NON-NLS-1$
    runnerButton.setSelection(true);
    runnerGroup.getParent().redraw();
  }

  /**
   * Synchronously updates the project hierarchy.
   */
  private void updateHierarchy() throws InvocationTargetException, InterruptedException {
    // blocking call (regardless of "fork"), returning only after the inner runnable completes
    getLaunchConfigurationDialog().run(true /*fork*/, true /*cancelable*/,
        new IRunnableWithProgress() {
          @Override
          public void run(IProgressMonitor monitor)
              throws InvocationTargetException, InterruptedException {
            SubMonitor subMonitor = SubMonitor.convert(monitor,
                Messages.getString("loading.pipeline.options.hierarchy"), 100);
            hierarchy = getPipelineOptionsHierarchy(subMonitor.split(100));
          }
        });
  }

  private DataflowPreferences getPreferences() {
    if (project != null && project.isAccessible()) {
      return ProjectOrWorkspaceDataflowPreferences.forProject(project);
    } else {
      return ProjectOrWorkspaceDataflowPreferences.forWorkspace();
    }
  }

  private PipelineOptionsHierarchy getPipelineOptionsHierarchy(IProgressMonitor monitor) {
    if (launchConfiguration != null) {
      Verify.verify(project != null && project.isAccessible());
      try {
        return pipelineOptionsHierarchyFactory.forProject(project,
            launchConfiguration.getMajorVersion(), monitor);
      } catch (PipelineOptionsRetrievalException e) {
        DataflowUiPlugin.logWarning(
            "Couldn't retrieve Pipeline Options Hierarchy for project %s", project); //$NON-NLS-1$
        return pipelineOptionsHierarchyFactory.global(monitor);
      }
    }
    return pipelineOptionsHierarchyFactory.global(monitor);
  }

  @Override
  public String getName() {
    return Messages.getString("pipeline.arguments"); //$NON-NLS-1$
  }

  private void updatePipelineOptionsForm() {
    try {
      // This is merely a reference holder; atomicity not required.
      AtomicReference<Map<PipelineOptionsType, Set<PipelineOptionsProperty>>>
          optionsHierarchy = new AtomicReference<>();
      // blocking call (regardless of "fork"), returning only after the inner runnable completes
      getLaunchConfigurationDialog().run(true /*fork*/, true /*cancelable*/,
          new IRunnableWithProgress() {
        @Override
        public void run(IProgressMonitor monitor)
            throws InvocationTargetException, InterruptedException {
          monitor.beginTask(Messages.getString("updating.pipeline.options"), //$NON-NLS-1$
              IProgressMonitor.UNKNOWN);
          optionsHierarchy.set(launchConfiguration.getOptionsHierarchy(hierarchy));
        }
      });

      BusyIndicator.showWhile(composite.getDisplay(), () -> {
        try {
          suppressDialogUpdates = true;
          pipelineOptionsForm.updateForm(launchConfiguration, optionsHierarchy.get());
        } finally {
          suppressDialogUpdates = false;
        }
      });
    } catch (InvocationTargetException | InterruptedException ex) {
      DataflowUiPlugin.logError(ex, "Exception occurred while updating available Pipeline Options");
    }
  }

  private Map<String, String> getNonDefaultOptions() {
    Map<String, String> argumentValues = new HashMap<>();
    for (LabeledTextMapComponent optionsComponent : pipelineOptionsForm.getComponents()) {
      argumentValues.putAll(optionsComponent.getTextValues());
    }
    return argumentValues;
  }

  private boolean validatePage() {
    if (launchConfiguration == null) {
      setErrorMessage("Project is not configured for Dataflow");
      return false;
    } else if (!launchConfiguration.getRunner().getSupportedVersions()
        .contains(launchConfiguration.getMajorVersion())) {
      setErrorMessage(
          "Incompatible pipeline runner: " + launchConfiguration.getRunner().getRunnerName());
      return false;
    }

    MissingRequiredProperties validationFailures =
        launchConfiguration.getMissingRequiredProperties(hierarchy, getPreferences());

    setErrorMessage(null);
    return validateRequiredProperties(validationFailures)
        && validateRequiredGroups(validationFailures);
  }

  private boolean validateRequiredGroups(MissingRequiredProperties validationFailures) {
    Map.Entry<String, Set<PipelineOptionsProperty>> missingGroupEntry =
        Iterables.getFirst(validationFailures.getMissingGroups().entrySet(), null);
    if (missingGroupEntry != null) {
      StringBuilder errorBuilder = new StringBuilder("Missing value for group "); //$NON-NLS-1$
      errorBuilder.append(missingGroupEntry.getKey());
      errorBuilder.append(". Properties satisfying group requirement are "); //$NON-NLS-1$
      Set<String> groupMembers = new HashSet<>();
      for (PipelineOptionsProperty missingProperty : missingGroupEntry.getValue()) {
        groupMembers.add(missingProperty.getName());
      }
      errorBuilder.append(MISSING_GROUP_MEMBER_JOINER.join(groupMembers));
      errorBuilder.append("."); //$NON-NLS-1$
      setErrorMessage(errorBuilder.toString());
      return false;
    }
    return true;
  }

  private boolean validateRequiredProperties(MissingRequiredProperties validationFailures) {
    PipelineOptionsProperty missingProperty =
        Iterables.getFirst(validationFailures.getMissingProperties(), null);
    if (missingProperty != null) {
      setErrorMessage(Messages.getString("missing.required.property", missingProperty.getName())); //$NON-NLS-1$
      return false;
    }
    return true;
  }

  @Override
  public boolean isValid(ILaunchConfiguration configuration) {
    reload(configuration);
    return validatePage();
  }

  @Override
  protected void updateLaunchConfigurationDialog() {
    composite.setMinSize(internalComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    composite.pack();
    super.updateLaunchConfigurationDialog();
  }

  /**
   * When the Runner selection is changed, update the underlying launch configuration, update the
   * PipelineOptionsForm to show all available inputs, and re-render the tab.
   */
  private class UpdateLaunchConfigAndRequiredArgsSelectionListener extends SelectionAdapter {
    private final PipelineRunner runner;

    public UpdateLaunchConfigAndRequiredArgsSelectionListener(PipelineRunner runner) {
      this.runner = runner;
    }

    @Override
    public void widgetSelected(SelectionEvent event) {
      Preconditions.checkArgument(event.getSource() instanceof Button, "add listener to buttons");

      Button button = (Button) event.getSource();
      if (button.getSelection()) {
        launchConfiguration.setRunner(runner);
        updatePipelineOptionsForm();
        updateLaunchConfigurationDialog();
      }
    }
  }

  /**
   * When 1) the default options button is selected; or 2) a launch configuration property changes;
   * or 3) account selection changes, then ensure 1) the validation state is reflected in the
   * arguments tab; and 2) the min size of the {@code ScrolledComposite} is updated to fit the
   * entire form.
   */
  @VisibleForTesting
  class UpdateLaunchConfigurationDialogChangedListener
      extends SelectionAdapter implements ModifyListener, IExpansionListener, Runnable {

    @Override
    public void widgetSelected(SelectionEvent event) {
      run();
    }

    @Override
    public void modifyText(ModifyEvent event) {
      run();
    }

    @Override
    public void expansionStateChanging(ExpansionEvent event) {  // ignored
    }

    @Override
    public void expansionStateChanged(ExpansionEvent event) {
      run();
    }

    @Override
    public void run() {
      if (!suppressDialogUpdates) {
        updateLaunchConfigurationDialog();
      }
    }
  }
}
