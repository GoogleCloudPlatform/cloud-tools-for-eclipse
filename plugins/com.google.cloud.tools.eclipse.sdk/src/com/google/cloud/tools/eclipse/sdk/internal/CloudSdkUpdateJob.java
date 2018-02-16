/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.eclipse.sdk.internal;

import com.google.cloud.tools.eclipse.sdk.MessageConsoleWriterListener;
import com.google.cloud.tools.eclipse.sdk.Messages;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import com.google.cloud.tools.managedcloudsdk.ManagedCloudSdk;
import com.google.cloud.tools.managedcloudsdk.ManagedSdkVerificationException;
import com.google.cloud.tools.managedcloudsdk.ManagedSdkVersionMismatchException;
import com.google.cloud.tools.managedcloudsdk.UnsupportedOsException;
import com.google.cloud.tools.managedcloudsdk.command.CommandExecutionException;
import com.google.cloud.tools.managedcloudsdk.command.CommandExitException;
import com.google.cloud.tools.managedcloudsdk.update.SdkUpdater;
import java.util.concurrent.locks.ReadWriteLock;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.console.MessageConsoleStream;

/** Updates the Managed Google Cloud SDK, if installed. */
public class CloudSdkUpdateJob extends CloudSdkModifyJob {

  public CloudSdkUpdateJob(MessageConsoleStream consoleStream, ReadWriteLock cloudSdkLock) {
    super(Messages.getString("updating.cloud.sdk"), consoleStream, cloudSdkLock); // $NON-NLS-1$
  }

  /** The severity reported on installation failure. */
  private int failureSeverity = IStatus.ERROR;

  /**
   * Perform the installation and configuration of the managed Cloud SDK. Any errors are returned as
   * {@link IStatus#WARNING} to avoid the Eclipse UI ProgressManager reporting the error with no
   * context (e.g., that deployment fails as the Cloud SDK could not be installed).
   */
  @Override
  protected IStatus modifySdk(IProgressMonitor monitor) {
    if (monitor.isCanceled()) {
      return Status.CANCEL_STATUS;
    }
    monitor.beginTask(Messages.getString("configuring.cloud.sdk"), 10); // $NON-NLS-1$
    try {
      ManagedCloudSdk managedSdk = getManagedCloudSdk();
      if (!managedSdk.isInstalled()) {
        return StatusUtil.create(
            failureSeverity, this, Messages.getString("cloud.sdk.not.installed"));
      } else if (!managedSdk.isUpToDate()) {
        subTask(monitor, Messages.getString("updating.cloud.sdk")); // $NON-NLS-1$
        SdkUpdater updater = managedSdk.newUpdater();
        updater.update(new MessageConsoleWriterListener(consoleStream));
        monitor.worked(10);
      }
      return Status.OK_STATUS;

    } catch (InterruptedException e) {
      return Status.CANCEL_STATUS;
    } catch (ManagedSdkVerificationException | CommandExecutionException | CommandExitException e) {
      String message = Messages.getString("installing.cloud.sdk.failed");
      return StatusUtil.create(failureSeverity, this, message, e); // $NON-NLS-1$
    } catch (UnsupportedOsException e) {
      String message = Messages.getString("unsupported.os.installation");
      return StatusUtil.create(failureSeverity, this, message, e); // $NON-NLS-1$

    } catch (ManagedSdkVersionMismatchException e) {
      throw new IllegalStateException(
          "This is never thrown because we always use LATEST.", e); // $NON-NLS-1$
    }
  }

  /**
   * Set the {@link IStatus#getSeverity() severity} of installation failure. This is useful for
   * situations where the Cloud SDK installation is a step of some other work, and the installation
   * failure should be surfaced to the user in the context of that work. If reported as {@link
   * IStatus#ERROR} then the Eclipse UI ProgressManager will report the installation failure
   * directly.
   */
  public void setFailureSeverity(int severity) {
    failureSeverity = severity;
  }
}
