/*******************************************************************************
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/

package com.google.cloud.tools.eclipse.appengine.login;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.util.Utils;
import com.google.cloud.tools.eclipse.appengine.login.ui.GoogleLoginBrowser;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.jface.window.IShellProvider;
import org.eclipse.ui.PlatformUI;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Provides service related to login, e.g., account management, getting a credential of a
 * currently active user, etc.
 */
public class GoogleLoginService {

  private static final String STASH_OAUTH_CRED_KEY = "OAUTH_CRED";

  // For the detailed info about each scope, see
  // https://github.com/GoogleCloudPlatform/gcloud-eclipse-tools/wiki/Cloud-Tools-for-Eclipse-Technical-Design#oauth-20-scopes-requested
  private static final List<String> OAUTH_SCOPES = Collections.unmodifiableList(Arrays.asList(
      "https://www.googleapis.com/auth/cloud-platform" //$NON-NLS-1$
  ));

  private CredentialHelper credentialHelper = new CredentialHelper();
  
  /**
   * Returns the credential of an active user (among multiple logged-in users). A login screen
   * may be presented, e.g., if no user is logged in or login is required due to an expired
   * credential. This method returns {@code null} if a user cancels the login process.
   * For this reason, if {@code null} is returned, the caller should cancel the current
   * operation and display a general message that login is required but was cancelled or failed.
   *
   * Must be called from a UI context.
   *
   * @param shellProvider provides a shell for the login screen if login is necessary
   * @throws IOException can be thrown by the underlying Login API request (e.g., network
   *     error from the transport layer while sending/receiving a HTTP request/response.)
   */
  public Credential getActiveCredential(IShellProvider shellProvider) throws IOException {
    Credential credential = getCachedActiveCredential();

    if (credential == null) {
      credential = logIn(shellProvider);

      IEclipseContext eclipseContext = PlatformUI.getWorkbench().getService(IEclipseContext.class);
      eclipseContext.set(STASH_OAUTH_CRED_KEY, credential);
    }
    return credential;
  }

  /**
   * Returns the credential of an active user (among multiple logged-in users). Unlike {@link
   * #getActiveCredential}, this version does not involve login process or make API calls.
   * Returns {@code null} if no credential has been cached.
   *
   * Safe to call from non-UI contexts.
   */
  public Credential getCachedActiveCredential() {
    IEclipseContext eclipseContext = PlatformUI.getWorkbench().getService(IEclipseContext.class);
    return (Credential) eclipseContext.get(STASH_OAUTH_CRED_KEY);
  }

  private Credential logIn(IShellProvider shellProvider) throws IOException {
    GoogleLoginBrowser loginBrowser = new GoogleLoginBrowser(
        shellProvider.getShell(), Constants.getOAuthClientId(), OAUTH_SCOPES);
    if (loginBrowser.open() != GoogleLoginBrowser.OK) {
      return null;
    }

    GoogleAuthorizationCodeTokenRequest authRequest = new GoogleAuthorizationCodeTokenRequest(
        Utils.getDefaultTransport(), Utils.getDefaultJsonFactory(),
        Constants.getOAuthClientId(), Constants.getOAuthClientSecret(),
        loginBrowser.getAuthorizationCode(),
        GoogleLoginBrowser.REDIRECT_URI);

    return createCredential(authRequest.execute());
  }

  private Credential createCredential(GoogleTokenResponse tokenResponse) {
    return credentialHelper.createCredential(tokenResponse.getAccessToken(),
                                             tokenResponse.getRefreshToken());
  }

  public void clearCredential() {
    IEclipseContext eclipseContext = PlatformUI.getWorkbench().getService(IEclipseContext.class);
    eclipseContext.remove(STASH_OAUTH_CRED_KEY);
  }
}
