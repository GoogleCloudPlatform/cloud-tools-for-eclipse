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

package com.google.cloud.tools.eclipse.sdk.ui.preferences;

import com.google.cloud.tools.appengine.api.AppEngineException;
import com.google.cloud.tools.appengine.cloudsdk.CloudSdk;
import com.google.cloud.tools.eclipse.preferences.areas.PreferenceArea;
import com.google.cloud.tools.eclipse.sdk.internal.PreferenceConstants;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CloudSdkPreferenceArea extends PreferenceArea {
  /** Preference Page ID that hosts this area. */
  public static final String PAGE_ID =
      "com.google.cloud.tools.eclipse.preferences.main";
  private static final Logger logger = Logger.getLogger(CloudSdkPreferenceArea.class.getName());

  private IWorkbench workbench;
  private DirectoryFieldEditor sdkLocation;
  private IStatus status = Status.OK_STATUS;
  private IPropertyChangeListener wrappedPropertyChangeListener = new IPropertyChangeListener() {

    @Override
    public void propertyChange(PropertyChangeEvent event) {
      if (event.getProperty() == DirectoryFieldEditor.IS_VALID) {
        fireValueChanged(IS_VALID, event.getOldValue(), event.getNewValue());
      } else if (event.getProperty() == DirectoryFieldEditor.VALUE) {
        fireValueChanged(VALUE, event.getOldValue(), event.getNewValue());
      }
    }
  };

  public CloudSdkPreferenceArea() {
    // Should we assume this?
    this.workbench = PlatformUI.getWorkbench();
  }

  @Override
  public Control createContents(Composite parent) {
    Composite contents = new Composite(parent, SWT.NONE);
    Link instructions = new Link(contents, SWT.WRAP);
    instructions.setText(SdkUiMessages.CloudSdkPreferencePage_2);
    instructions.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent event) {
        openUrl(event.text);
      }
    });

    Composite fieldContents = new Composite(parent, SWT.NONE);
    sdkLocation = new CloudSdkDirectoryFieldEditor(PreferenceConstants.CLOUDSDK_PATH,
        SdkUiMessages.CloudSdkPreferencePage_5, fieldContents);
    sdkLocation.setPreferenceStore(getPreferenceStore());
    sdkLocation.setPropertyChangeListener(wrappedPropertyChangeListener);
    GridLayoutFactory.swtDefaults().numColumns(sdkLocation.getNumberOfControls())
        .generateLayout(fieldContents);

    GridLayoutFactory.swtDefaults().generateLayout(contents);
    return contents;
  }

  @Override
  public void load() {
    sdkLocation.load();
  }

  @Override
  public void loadDefault() {
    sdkLocation.loadDefault();
  }

  @Override
  public IStatus getStatus() {
    return status;
  }

  @Override
  public void performApply() {
    sdkLocation.store();
  }

  /** Sets the new value or {@code null} for the empty string. */
  public void setStringValue(String value) {
    sdkLocation.setStringValue(value);
  }

  protected void openUrl(String urlText) {
    try {
      URL url = new URL(urlText);
      IWorkbenchBrowserSupport browserSupport = workbench.getBrowserSupport();
      browserSupport.createBrowser(null).openURL(url);
    } catch (MalformedURLException mue) {
      logger.log(Level.WARNING, SdkUiMessages.CloudSdkPreferencePage_3, mue);
    } catch (PartInitException pie) {
      logger.log(Level.WARNING, SdkUiMessages.CloudSdkPreferencePage_4, pie);
    }
  }

  protected boolean validateSdk(Path location) {
    try {
      CloudSdk sdk = new CloudSdk.Builder().sdkPath(location).build();
      sdk.validate();
    } catch (AppEngineException ex) {
      // accept a seemingly invalid location in case the SDK organization
      // has changed and the CloudSdk#validate() code is out of date
      status = new Status(IStatus.WARNING, getClass().getName(),
          MessageFormat.format(SdkUiMessages.CloudSdkPreferencePage_6, ex.getMessage()));
    }
    return true;
  }

  /**
   * A wrapper around DirectoryFieldEditor for performing validation checks that the location holds
   * a SDK. Uses {@code VALIDATE_ON_KEY_STROKE} to perform check on per keystroke to avoid wiping
   * out the validation messages.
   */
  class CloudSdkDirectoryFieldEditor extends DirectoryFieldEditor {
    public CloudSdkDirectoryFieldEditor(String name, String labelText, Composite parent) {
      // unfortunately cannot use super(name,labelText,parent) as must specify the
      // validateStrategy before the createControl()
      init(name, labelText);
      setErrorMessage(JFaceResources.getString("DirectoryFieldEditor.errorMessage"));//$NON-NLS-1$
      setChangeButtonText(JFaceResources.getString("openBrowse"));//$NON-NLS-1$
      setEmptyStringAllowed(true);
      setValidateStrategy(VALIDATE_ON_KEY_STROKE);
      createControl(parent);
    }

    @Override
    protected boolean doCheckState() {
      if (!super.doCheckState()) {
        status = new Status(IStatus.ERROR, getClass().getName(), "Invalid directory");
        return false;
      }
      status = Status.OK_STATUS;
      return getStringValue().isEmpty() || validateSdk(Paths.get(getStringValue()));
    }
  }
}
