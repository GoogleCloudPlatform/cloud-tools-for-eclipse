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

package com.google.cloud.tools.eclipse.appengine.deploy.ui.internal;

import com.google.api.client.auth.oauth2.Credential;
import com.google.cloud.tools.eclipse.appengine.deploy.ui.Messages;
import com.google.cloud.tools.eclipse.login.ui.AccountSelector;
import com.google.cloud.tools.eclipse.projectselector.GcpProject;
import com.google.cloud.tools.eclipse.projectselector.ProjectRepository;
import com.google.cloud.tools.eclipse.projectselector.ProjectRepositoryException;
import com.google.cloud.tools.eclipse.projectselector.ProjectSelector;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;

public class ProjectSelectorSelectionChangedListener implements ISelectionChangedListener {

  private final AccountSelector accountSelector;
  private final ProjectRepository projectRepository;
  private final ProjectSelector projectSelector;

  public ProjectSelectorSelectionChangedListener(AccountSelector accountSelector,
                                                 ProjectRepository projectRepository,
                                                 ProjectSelector projectSelector) {
    this.accountSelector = accountSelector;
    this.projectRepository = projectRepository;
    this.projectSelector = projectSelector;
  }

  @Override
  public void selectionChanged(SelectionChangedEvent event) {
    IStructuredSelection selection = (IStructuredSelection) event.getSelection();
    try {
      if (!selection.isEmpty()) {
        GcpProject project = (GcpProject) selection.getFirstElement();
        Credential selectedCredential = accountSelector.getSelectedCredential();
        String projectId = project.getId();
        boolean hasAppEngineApplication =
            projectRepository.hasAppEngineApplication(selectedCredential, projectId);
        if (!hasAppEngineApplication) {
          projectSelector.setStatusLink(
              Messages.getString("projectselector.missing.appengine.application.link",
                                 projectId));
        } else {
          projectSelector.clearStatusLink();
        }
      } else {
        projectSelector.clearStatusLink();
      }
    } catch (ProjectRepositoryException ex) {
      projectSelector.setStatusLink(Messages.getString("projectselector.retrieveapplication.error.message",
                                                       ex.getLocalizedMessage()));
    }
  }
}