/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.eclipse.appengine.newproject.maven;

import java.text.MessageFormat;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.google.cloud.tools.eclipse.appengine.newproject.JavaPackageValidator;
import com.google.cloud.tools.eclipse.appengine.ui.AppEngineImages;
import com.google.cloud.tools.eclipse.usagetracker.AnalyticsEvents;
import com.google.cloud.tools.eclipse.usagetracker.AnalyticsPingManager;
import com.google.cloud.tools.project.ProjectIdValidator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;

/**
 * UI to collect all information necessary to create a new Maven-based App Engine Standard Java
 * project.
 */
public class MavenAppEngineStandardWizardPage extends WizardPage {

  private String defaultVersion = "0.1.0-SNAPSHOT";

  private Button useDefaults;
  private Text locationField;
  private Button locationBrowseButton;
  private Text groupIdField;
  private Text artifactIdField;
  private Text versionField;
  private Text javaPackageField;
  private Text projectIdField;

  private boolean canFlipPage;

  public MavenAppEngineStandardWizardPage() {
    super("basicNewProjectPage"); //$NON-NLS-1$
    setTitle(Messages.getString("WIZARD_TITLE")); //$NON-NLS-1$
    setDescription(Messages.getString("WIZARD_DESCRIPTION")); //$NON-NLS-1$
    setImageDescriptor(AppEngineImages.googleCloudPlatform(32));

    canFlipPage = false;
  }

  @Override
  public void createControl(Composite parent) {
    AnalyticsPingManager.getInstance().sendPing(
        AnalyticsEvents.APP_ENGINE_NEW_PROJECT_WIZARD,
        AnalyticsEvents.APP_ENGINE_NEW_PROJECT_WIZARD_TYPE,
        AnalyticsEvents.APP_ENGINE_NEW_PROJECT_WIZARD_TYPE_MAVEN, parent.getShell());

    Composite container = new Composite(parent, SWT.NONE);
    GridLayoutFactory.swtDefaults().numColumns(2).applyTo(container);

    createLocationArea(container);
    createMavenCoordinatesArea(container);
    createAppEngineProjectDetailsArea(container);

    setControl(container);

    Dialog.applyDialogFont(container);
  }

  /** Create UI for specifying the generated location area */
  private void createLocationArea(Composite container) {
    ModifyListener pageValidator = new PageValidator();

    Group locationGroup = new Group(container, SWT.NONE);
    locationGroup.setText("Location");
    GridDataFactory.fillDefaults().span(2, 1).applyTo(locationGroup);
    GridLayoutFactory.swtDefaults().numColumns(3).applyTo(locationGroup);

    useDefaults = new Button(locationGroup, SWT.CHECK);
    GridDataFactory.defaultsFor(useDefaults).span(3, 1).applyTo(useDefaults);
    useDefaults.setText("Create project in workspace");
    useDefaults.setSelection(true);

    Label locationLabel = new Label(locationGroup, SWT.NONE);
    locationLabel.setText("Location:");
    locationLabel
        .setToolTipText("This location will contain the directory created for the project");

    locationField = new Text(locationGroup, SWT.BORDER);
    GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false)
        .applyTo(locationField);
    locationField.addModifyListener(pageValidator);
    locationField.setEnabled(false);

    locationBrowseButton = new Button(locationGroup, SWT.PUSH);
    locationBrowseButton.setText("Browse");
    locationBrowseButton.setEnabled(false);
    locationBrowseButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        openLocationDialog();
      }
    });
    useDefaults.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        locationField.setEnabled(!useDefaults.getSelection());
        locationBrowseButton.setEnabled(!useDefaults.getSelection());
        checkFlipToNext();
      }
    });
  }

  /** Create UI for specifying desired Maven Coordinates */
  private void createMavenCoordinatesArea(Composite container) {
    ModifyListener pageValidator = new PageValidator();

    Group mavenCoordinatesGroup = new Group(container, SWT.NONE);
    mavenCoordinatesGroup.setText("Maven project coordinates");
    GridDataFactory.defaultsFor(mavenCoordinatesGroup).span(2, 1).applyTo(mavenCoordinatesGroup);
    GridLayoutFactory.swtDefaults().numColumns(2).applyTo(mavenCoordinatesGroup);

    Label groupIdLabel = new Label(mavenCoordinatesGroup, SWT.NONE);
    groupIdLabel.setText("Group ID:");
    groupIdField = new Text(mavenCoordinatesGroup, SWT.BORDER);
    GridDataFactory.defaultsFor(groupIdField).align(SWT.FILL, SWT.CENTER).applyTo(groupIdField);
    groupIdField.addModifyListener(pageValidator);
    groupIdField.addVerifyListener(new AutoPackageNameSetterOnGroupIdChange());

    Label artifactIdLabel = new Label(mavenCoordinatesGroup, SWT.NONE);
    artifactIdLabel.setText("Artifact ID:");
    artifactIdField = new Text(mavenCoordinatesGroup, SWT.BORDER);
    GridDataFactory.defaultsFor(artifactIdField).align(SWT.FILL, SWT.CENTER)
        .applyTo(artifactIdField);
    artifactIdField.addModifyListener(pageValidator);

    Label versionLabel = new Label(mavenCoordinatesGroup, SWT.NONE);
    versionLabel.setText("Version:");
    versionField = new Text(mavenCoordinatesGroup, SWT.BORDER);
    versionField.setText(defaultVersion);
    GridDataFactory.defaultsFor(versionField).align(SWT.FILL, SWT.CENTER).applyTo(versionField);
    versionField.addModifyListener(pageValidator);
  }

  /** Create UI for specifying App Engine project details */
  private void createAppEngineProjectDetailsArea(Composite container) {
    ModifyListener pageValidator = new PageValidator();

    // Java package name
    Label packageNameLabel = new Label(container, SWT.NONE);
    packageNameLabel.setText("Java package:");
    javaPackageField = new Text(container, SWT.BORDER);
    GridData javaPackagePosition = new GridData(GridData.FILL_HORIZONTAL);
    javaPackagePosition.horizontalSpan = 2;
    javaPackageField.setLayoutData(javaPackagePosition);
    javaPackageField.addModifyListener(pageValidator);

    // App Engine Project ID
    Label projectIdLabel = new Label(container, SWT.NONE);
    projectIdLabel.setText("App Engine Project ID: (optional)");
    projectIdField = new Text(container, SWT.BORDER);
    GridData projectIdPosition = new GridData(GridData.FILL_HORIZONTAL);
    projectIdPosition.horizontalSpan = 2;
    projectIdField.setLayoutData(projectIdPosition);
    projectIdField.addModifyListener(pageValidator);
  }

  protected void openLocationDialog() {
    DirectoryDialog dialog = new DirectoryDialog(getShell());
    dialog.setText("Please select the location to contain generated project");
    String location = dialog.open();
    if (location != null) {
      locationField.setText(location);
      checkFlipToNext();
    }
  }

  @Override
  public boolean canFlipToNextPage() {
    return canFlipPage;
  }

  protected void checkFlipToNext() {
    canFlipPage = validatePage();
    getContainer().updateButtons();
  }

  /**
   * Validate and report on the contents of this page
   *
   * @return true if valid, false if there is a problem
   */
  public boolean validatePage() {
    setMessage(null);
    setErrorMessage(null);

    // order here should match order of the UI fields

    String location = locationField.getText().trim();
    if (!useDefaults() && location.isEmpty()) {
      setMessage("Please provide a location", INFORMATION);
      return false;
    }

    if (!validateMavenSettings()) {
      return false;
    }
    if (!validateGeneratedProjectLocation()) {
      return false;
    }
    if (!validateAppEngineProjectDetails()) {
      return false;
    }

    return true;
  }

  /**
   * Check that we won't overwrite an existing location. Expects a valid Maven Artifact ID.
   */
  private boolean validateGeneratedProjectLocation() {
    String artifactId = getArtifactId();
    // assert !artifactId.isEmpty()
    IPath path = getLocationPath().append(artifactId);
    if (path.toFile().exists()) {
      setErrorMessage(MessageFormat.format("Location already exists: {0}.", path));
      return false;
    }
    return true;
  }

  private boolean validateMavenSettings() {
    String groupId = getGroupId();
    if (groupId.isEmpty()) {
      setMessage("Please provide Maven Group ID.", INFORMATION);
      return false;
    } else if (!MavenCoordinatesValidator.validateGroupId(groupId)) {
      setErrorMessage(MessageFormat.format("Illegal Maven Group ID: {0}.", groupId));
      return false;
    }
    String artifactId = getArtifactId();
    if (artifactId.isEmpty()) {
      setMessage("Please provide Maven Artifact ID.", INFORMATION);
      return false;
    } else if (!MavenCoordinatesValidator.validateArtifactId(artifactId)) {
      setErrorMessage("Illegal Maven Artifact ID: " + artifactId);
      return false;
    }
    String version = getVersion();
    if (version.isEmpty()) {
      setMessage("Please provide Maven artifact version.", INFORMATION);
      return false;
    } else if (!MavenCoordinatesValidator.validateVersion(version)) {
      setErrorMessage("Illegal Maven version: " + version);
      return false;
    }
    return true;
  }

  private boolean validateAppEngineProjectDetails() {
    String packageName = getPackageName();
    IStatus status = JavaPackageValidator.validate(packageName);
    if (!status.isOK()) {
      String details = status.getMessage() == null ? packageName : status.getMessage();
      String message = MessageFormat.format("Illegal Java package name: {0}", details);
      setErrorMessage(message);
      return false;
    }

    String projectId = getAppEngineProjectId();
    if (!projectId.isEmpty() && !ProjectIdValidator.validate(projectId)) {
      setErrorMessage(MessageFormat.format("Illegal App Engine Project ID: {0}.", projectId));
      return false;
    }
    return true;
  }

  /** Return the Maven group for the project */
  public String getGroupId() {
    return groupIdField.getText().trim();
  }

  /** Return the Maven artifact for the project */
  public String getArtifactId() {
    return artifactIdField.getText().trim();
  }

  /** Return the Maven version for the project */
  public String getVersion() {
    return versionField.getText().trim();
  }

  /**
   * If true, projects are generated into the workspace, otherwise placed into a specified location.
   */
  public boolean useDefaults() {
    return useDefaults.getSelection();
  }

  /** Return the App Engine Project ID (if any) */
  public String getAppEngineProjectId() {
    return this.projectIdField.getText();
  }

  /** Return the package name for any example code */
  public String getPackageName() {
    return this.javaPackageField.getText();
  }

  /** Return the location where the project should be generated into */
  public IPath getLocationPath() {
    if (useDefaults()) {
      return ResourcesPlugin.getWorkspace().getRoot().getLocation();
    }
    return new Path(locationField.getText());
  }

  private final class PageValidator implements ModifyListener {
    @Override
    public void modifyText(ModifyEvent event) {
      checkFlipToNext();
    }
  }

  /**
   * Auto-fills javaPackageField as groupId when 
   * 1) javaPackageField is empty; or 
   * 2) the field matches previous auto-fill before ID modification.
   */
  private final class AutoPackageNameSetterOnGroupIdChange implements VerifyListener {
    @Override
    public void verifyText(VerifyEvent event) {
      // Below explains how to get text after modification:
      // http://stackoverflow.com/questions/32872249/get-text-of-swt-text-component-before-modification
      String newGroupId =
          getGroupId().substring(0, event.start) + event.text + getGroupId().substring(event.end);

      String oldPackageName = suggestPackageName(getGroupId());
      String newPackageName = suggestPackageName(newGroupId);
      adjustPackageName(oldPackageName, newPackageName);
    }
  }

  /**
   * See {@link AutoPackageNameSetterOnGroupIdChange#verifyText(VerifyEvent)}.
   */
  private void adjustPackageName(String oldPackageName, String newPackageName) {
    if (getPackageName().isEmpty() || getPackageName().equals(oldPackageName)) {
      javaPackageField.setText(newPackageName);
    }
  }

  /**
   * Helper function returning a suggested package name based on groupId.
   * It does basic string filtering/manipulation, which does not completely eliminate
   * naming issues. However, users will be alerted of any errors in naming by
   * {@link #validatePage}.
   */
  @VisibleForTesting
  static String suggestPackageName(String groupId) {

    if (JavaPackageValidator.validate(groupId).isOK()) {
      return groupId;
    }

    // 1) Remove leading and trailing dots.
    // 2) Keep only word characters ([a-zA-Z_0-9]) and dots (escaping inside [] not necessary).
    // 3) Replace consecutive dots with a single dot.
    return CharMatcher.is('.').trimFrom(groupId)
        .replaceAll("[^\\w.]", "").replaceAll("\\.+",  ".");
  }
}
