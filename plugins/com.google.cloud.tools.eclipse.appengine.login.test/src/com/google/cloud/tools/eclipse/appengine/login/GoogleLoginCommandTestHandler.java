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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import com.google.api.client.auth.oauth2.Credential;

public class GoogleLoginCommandTestHandler extends GoogleLoginCommandHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    super.execute(event);

    Credential credential = new GoogleLoginService().getCachedActiveCredential();
    if (credential != null) {
      boolean success = testLogin(credential);
      MessageDialog.openInformation(HandlerUtil.getActiveShell(event),
          "TESTING AUTH", success ? "SUCCESS" : "FAILURE (see console output)");
    }

    return null;
  }

  private boolean testLogin(Credential credential) {
    try {
      return testCredentialWithGcloud(getCredentialFile(credential));
    } catch (IOException ioe) {
      ioe.printStackTrace();
      return false;
    }
  }

  private File getCredentialFile(Credential credential) throws IOException {
    File credentialFile = File.createTempFile("tmp_eclipse_login_test_cred", ".json");
    credentialFile.deleteOnExit();
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(credentialFile))) {
      String jsonCredential = GoogleLoginService.getJsonCredential(credential);
      writer.write(jsonCredential);
    }
    return credentialFile;
  }

  private boolean testCredentialWithGcloud(File credentialFile) throws IOException {
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(
          "gcloud", "projects", "list", "--credential-file-override=" + credentialFile.toString());

      Process process = processBuilder.start();
      process.waitFor();

      try (
        BufferedReader outReader =
            new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader errReader =
            new BufferedReader(new InputStreamReader(process.getErrorStream()))
      ) {
        while (outReader.ready() || errReader.ready()) {
          if (outReader.ready()) {
            System.out.println("[stdout] " + outReader.readLine());
          }
          if (errReader.ready()) {
            System.out.println("[stderr] " + errReader.readLine());
          }
        }
      }
      return process.exitValue() == 0;

    } catch (InterruptedException ie) {
      ie.printStackTrace();
      return false;
    }
  }
}
