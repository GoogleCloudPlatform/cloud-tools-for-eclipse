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

package com.google.cloud.tools.eclipse.sdk;

import com.google.cloud.tools.appengine.api.AppEngineException;
import com.google.cloud.tools.appengine.cloudsdk.CloudSdk;
import com.google.cloud.tools.appengine.cloudsdk.CloudSdk.Builder;
import com.google.cloud.tools.eclipse.sdk.internal.PreferenceConstants;
import com.google.common.annotations.VisibleForTesting;

import org.eclipse.jface.preference.IPreferenceStore;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility to find the Google Cloud SDK either at locations configured by the user or in standard
 * locations on the system.
 */
public class CloudSdkProvider {

  private IPreferenceStore preferences = PreferenceConstants.getPreferenceStore();
  private Path location;
  
  public CloudSdkProvider() {}
  
  @VisibleForTesting
  public CloudSdkProvider(IPreferenceStore preferences) {
    this.preferences = preferences;
  }
  
  // Used when the SDK is being built from the context.
  public CloudSdkProvider(Path location) {
    this.location = location;
  }

  /**
   * Return a {@link CloudSdk.Builder} instance, suitable for creating a {@link CloudSdk} instance
   * using the configured location if set.
   * 
   * @return a builder, or {@code null} if the Google Cloud SDK cannot be located
   */
  public CloudSdk.Builder createBuilder() {
    return createBuilder(location);
  }

  private Builder createBuilder(Path location) {
    CloudSdk.Builder sdkBuilder = new CloudSdk.Builder();
    if (location != null) {
      sdkBuilder.sdkPath(location);
    } else {
      String configuredPath = preferences.getString(PreferenceConstants.CLOUDSDK_PATH);
      if (configuredPath != null && !configuredPath.isEmpty()) {
        sdkBuilder.sdkPath(Paths.get(configuredPath));
      }
    }
    return sdkBuilder;
  }


  /**
   * Return the {@link CloudSdk} instance from the configured or discovered Cloud SDK.
   * 
   * <p>
   * It searches for Cloud SDK in {@code location} first. If Cloud SDK isn't there, it searches for
   * a valid location in {@code preferences}. If no valid location is found there, we let the
   * library discover the SDK in its typical locations.
   * 
   * <p>
   * This method ensures that the return SDK is valid, i.e., it contains the most important files,
   * or that no SDK is returned.
   * 
   * @return the configured {@link CloudSdk} or {@code null} if no valid SDK could be found
   */
  public CloudSdk getCloudSdk() {
    CloudSdk.Builder sdkBuilder = createBuilder(location);
    // If no location is set, let library discover the location.
    
    try {
      return sdkBuilder.build();
    } catch (AppEngineException aee) {
      // If no SDK could be discovered, let the caller prompt the user for a new one.
      return null;
    }
  }
}
