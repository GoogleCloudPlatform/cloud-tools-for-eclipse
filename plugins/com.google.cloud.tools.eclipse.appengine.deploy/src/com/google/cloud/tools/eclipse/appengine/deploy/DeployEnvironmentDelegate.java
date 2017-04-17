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

package com.google.cloud.tools.eclipse.appengine.deploy;

import com.google.cloud.tools.appengine.cloudsdk.CloudSdk;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

/**
 * Delegate that handles deploy behaviors that differ based on if the App Engine environment is
 * standard or flexible.
 */
public interface DeployEnvironmentDelegate {

  /**
   * @param project Eclipse project to be deployed
   * @param stagingDirectory directory where implementors methods should place necessary files for
   *     deployment, where this job will execute {@code gcloud app deploy}
   * @param safeWorkDirectory directory path that implementors methods may create safely to use as
   *     a temporary work directory during staging
   * @param cloudSdk {@link CloudSdk} that implementors may utilize
   */
  IStatus stage(IProject project, IPath stagingDirectory,
      IPath safeWorkDirectory, CloudSdk cloudSdk, IProgressMonitor monitor) throws CoreException;

  IPath getOptionalConfigurationFilesDirectory();

}
