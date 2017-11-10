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

package com.google.cloud.tools.eclipse.appengine.localserver.ui;

import com.google.api.client.auth.oauth2.Credential;
import com.google.cloud.tools.eclipse.appengine.localserver.Messages;
import com.google.cloud.tools.eclipse.googleapis.IGoogleApiFactory;
import com.google.cloud.tools.eclipse.login.IGoogleLoginService;
import com.google.cloud.tools.eclipse.login.ui.AccountSelector;
import com.google.cloud.tools.eclipse.projectselector.ProjectRepository;
import com.google.cloud.tools.eclipse.projectselector.ProjectRepositoryException;
import com.google.cloud.tools.eclipse.projectselector.ProjectSelector;
import com.google.cloud.tools.eclipse.projectselector.model.GcpProject;
import com.google.cloud.tools.eclipse.ui.util.images.SharedImages;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.debug.ui.EnvironmentTab;
import org.eclipse.jdt.internal.ui.viewsupport.ImageDisposer;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

public class GcpLocalRunTab extends AbstractLaunchConfigurationTab {

  private static final Logger logger = Logger.getLogger(GcpLocalRunTab.class.getName());

  private static final String ATTRIBUTE_ACCOUNT_EMAIL =
      "com.google.cloud.tools.eclipse.gcpEmulation.accountEmail"; //$NON-NLS-1$

  private static final String PROJECT_ID_ENVIRONMENT_VARIABLE =
      "GOOGLE_CLOUD_PROJECT"; //$NON-NLS-1$
  private static final String SERVICE_KEY_ENVIRONMENT_VARIABLE =
      "GOOGLE_APPLICATION_CREDENTIALS"; //$NON-NLS-1$

  private final EnvironmentTab environmentTab;
  private final IGoogleLoginService loginService;
  private final ProjectRepository projectRepository;

  private AccountSelector accountSelector;
  private ProjectSelector projectSelector;
  private Text serviceKeyInput;

  private Image gcpIcon;

  // We set up intermediary models between a run configuration and UI components for certain values,
  // because, e.g., the account selector cannot load an email if it is not logged in. In such a
  // case, although nothing is selected in the account selector, we should not clear the email saved
  // in the run configuration.
  private String accountEmailModel;
  private String gcpProjectIdModel;
  // To prevent updating above models when programmatically setting up UI components.
  private boolean initializingUiValues;

  private boolean activated;  // to avoid https://github.com/GoogleCloudPlatform/google-cloud-eclipse/pull/2568#discussion_r150128582

  public GcpLocalRunTab(EnvironmentTab environmentTab) {
    this(environmentTab, PlatformUI.getWorkbench().getService(IGoogleLoginService.class),
        new ProjectRepository(PlatformUI.getWorkbench().getService(IGoogleApiFactory.class)));
  }

  @VisibleForTesting
  GcpLocalRunTab(EnvironmentTab environmentTab,
      IGoogleLoginService loginService, ProjectRepository projectRepository) {
    this.environmentTab = environmentTab;
    this.loginService = loginService;
    this.projectRepository = projectRepository;
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);

    // Account row
    new Label(composite, SWT.LEAD).setText(Messages.getString("label.account")); //$NON-NLS-1$
    accountSelector = new AccountSelector(composite, loginService);
    accountSelector.addSelectionListener(new Runnable() {
      @Override
      public void run() {
        updateProjectSelector();

        if (!initializingUiValues) {
          boolean somethingSelected = !accountSelector.getSelectedEmail().isEmpty();
          boolean savedEmailAvailable = accountSelector.isEmailAvailable(accountEmailModel);
          if (somethingSelected || savedEmailAvailable) {
            accountEmailModel = accountSelector.getSelectedEmail();
            gcpProjectIdModel = ""; //$NON-NLS-1$
            updateLaunchConfigurationDialog();
          }
        }
      }
    });

    // Project row
    Label projectLabel = new Label(composite, SWT.LEAD);
    projectLabel.setText(Messages.getString("label.project")); //$NON-NLS-1$

    Composite projectSelectorComposite = new Composite(composite, SWT.NONE);
    final Text filterField = new Text(projectSelectorComposite,
        SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);

    projectSelector = new ProjectSelector(projectSelectorComposite);
    projectSelector.addSelectionChangedListener(new ISelectionChangedListener() {
      @Override
      public void selectionChanged(SelectionChangedEvent event) {
        if (!initializingUiValues) {
          boolean somethingSelected = !projectSelector.getSelectProjectId().isEmpty();
          boolean savedIdAvailable = projectSelector.isProjectIdAvailable(gcpProjectIdModel);
          if (somethingSelected || savedIdAvailable) {
            gcpProjectIdModel = projectSelector.getSelectProjectId();
            updateLaunchConfigurationDialog();
          }
        }
      }
    });

    filterField.setMessage(Messages.getString("project.filter.hint")); //$NON-NLS-1$
    filterField.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent event) {
        projectSelector.setFilter(filterField.getText());
      }
    });

    // Service key row
    new Label(composite, SWT.LEAD).setText(Messages.getString("label.service.key")); //$NON-NLS-1$
    serviceKeyInput = new Text(composite, SWT.BORDER);
    serviceKeyInput.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent event) {
        updateLaunchConfigurationDialog();
      }
    });
    Button browse = new Button(composite, SWT.NONE);
    browse.setText(Messages.getString("button.browse")); //$NON-NLS-1$

    GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.TOP).applyTo(projectLabel);
    GridDataFactory.fillDefaults().span(2, 1).applyTo(accountSelector);
    GridLayoutFactory.swtDefaults().numColumns(3).generateLayout(composite);

    GridDataFactory.fillDefaults().span(2, 1).applyTo(projectSelectorComposite);
    GridDataFactory.fillDefaults().applyTo(filterField);
    GridDataFactory.fillDefaults().grab(true, false).hint(SWT.DEFAULT, 200)
        .applyTo(projectSelector);
    GridLayoutFactory.fillDefaults().spacing(0, 0).generateLayout(projectSelectorComposite);

    setControl(composite);

    gcpIcon = SharedImages.GCP_IMAGE_DESCRIPTOR.createImage();
    composite.addDisposeListener(new ImageDisposer(gcpIcon));
  }

  private void updateProjectSelector() {
    final Credential credential = accountSelector.getSelectedCredential();
    if (credential == null) {
      projectSelector.setProjects(new ArrayList<GcpProject>());
      return;
    }

    BusyIndicator.showWhile(Display.getCurrent(), new Runnable() {
      @Override
      public void run() {
        try {
          List<GcpProject> gcpProjects = projectRepository.getProjects(credential);
          projectSelector.setProjects(gcpProjects);
        } catch (ProjectRepositoryException e) {
          logger.log(Level.WARNING,
              "Could not retrieve GCP project information from server.", e); //$NON-NLS-1$
        }
      }
    });
  }

  @Override
  public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
    // No particular default values to set in a newly created configuration.
  }

  @Override
  public void activated(ILaunchConfigurationWorkingCopy workingCopy) {
    activated = true;
    super.activated(workingCopy);
  }

  @Override
  public void deactivated(ILaunchConfigurationWorkingCopy workingCopy) {
    super.deactivated(workingCopy);
    activated = false;

    if (environmentTab != null) {
      // Unfortunately, "EnvironmentTab" overrides "activated()" not to call "initializeFrom()".
      // (Calling "initializeFrom()" when "activated()" is the default implementation of the base
      // class retained for backward compatibility.) We needed to call it on behalf of
      // "EnvironmentTab" to re-initialize its UI with the changes made here.
      environmentTab.initializeFrom(workingCopy);
    }
  }

  @Override
  public void initializeFrom(ILaunchConfiguration configuration) {
    accountEmailModel = getAttribute(configuration, ATTRIBUTE_ACCOUNT_EMAIL, ""); //$NON-NLS-1$

    Map<String, String> environmentMap = getEnvironmentMap(configuration);
    gcpProjectIdModel = Strings.nullToEmpty(environmentMap.get(PROJECT_ID_ENVIRONMENT_VARIABLE));
    String serviceKey = Strings.nullToEmpty(environmentMap.get(SERVICE_KEY_ENVIRONMENT_VARIABLE));

    initializingUiValues = true;
    // Selecting an account loads projects into the project selector synchronously (via a listener).
    accountSelector.selectAccount(accountEmailModel);
    projectSelector.selectProjectId(gcpProjectIdModel);
    serviceKeyInput.setText(serviceKey);
    initializingUiValues = false;
  }

  @Override
  public void performApply(ILaunchConfigurationWorkingCopy configuration) {
    if (!activated) {  // to avoid https://github.com/GoogleCloudPlatform/google-cloud-eclipse/pull/2568#discussion_r150128582
      return;
    }

    configuration.setAttribute(ATTRIBUTE_ACCOUNT_EMAIL, accountEmailModel);

    Map<String, String> environmentMap = getEnvironmentMap(configuration);
    if (!gcpProjectIdModel.isEmpty()) {
      environmentMap.put(PROJECT_ID_ENVIRONMENT_VARIABLE, gcpProjectIdModel);
    } else {
      environmentMap.remove(PROJECT_ID_ENVIRONMENT_VARIABLE);
    }
    if (!serviceKeyInput.getText().isEmpty()) {
      environmentMap.put(SERVICE_KEY_ENVIRONMENT_VARIABLE, serviceKeyInput.getText());
    } else {
      environmentMap.remove(SERVICE_KEY_ENVIRONMENT_VARIABLE);
    }
    configuration.setAttribute(ILaunchManager.ATTR_ENVIRONMENT_VARIABLES, environmentMap);
  }

  @Override
  public String getName() {
    return Messages.getString("gcp.emulation.tab.name"); //$NON-NLS-1$
  }

  @Override
  public Image getImage() {
    return gcpIcon;
  }

  @VisibleForTesting
  static String getAttribute(ILaunchConfiguration configuration,
      String attribute, String defaultValue) {
    try {
      return configuration.getAttribute(attribute, defaultValue);
    } catch (CoreException e) {
      logger.log(Level.WARNING, "Can't get value from launch configuration.", e); //$NON-NLS-1$
      return defaultValue;
    }
  }

  @VisibleForTesting
  static Map<String, String> getEnvironmentMap(ILaunchConfiguration configuration) {
    Map<String, String> emptyMap = new HashMap<>();  // should be mutable
    try {
      return configuration.getAttribute(ILaunchManager.ATTR_ENVIRONMENT_VARIABLES, emptyMap);
    } catch (CoreException e) {
      logger.log(Level.WARNING, "Can't get value from launch configuration.", e); //$NON-NLS-1$
      return emptyMap;
    }
  }
}
