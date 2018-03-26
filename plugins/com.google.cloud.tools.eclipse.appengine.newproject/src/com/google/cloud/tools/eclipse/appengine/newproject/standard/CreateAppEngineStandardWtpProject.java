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

package com.google.cloud.tools.eclipse.appengine.newproject.standard;

import com.google.cloud.tools.eclipse.appengine.facets.AppEngineStandardFacet;
import com.google.cloud.tools.eclipse.appengine.libraries.repository.ILibraryRepositoryService;
import com.google.cloud.tools.eclipse.appengine.newproject.AppEngineProjectConfig;
import com.google.cloud.tools.eclipse.appengine.newproject.CodeTemplates;
import com.google.cloud.tools.eclipse.appengine.newproject.CreateAppEngineWtpProject;
import com.google.cloud.tools.eclipse.appengine.newproject.Messages;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;

/**
 * Utility to make a new Eclipse project with the App Engine Standard facets in the workspace.
 */
public class CreateAppEngineStandardWtpProject extends CreateAppEngineWtpProject {

  public CreateAppEngineStandardWtpProject(AppEngineProjectConfig config,
      IAdaptable uiInfoAdapter, ILibraryRepositoryService repositoryService) {
    super(config, uiInfoAdapter, repositoryService);
  }

  @Override
  public void addAppEngineFacet(IFacetedProject newProject, IProgressMonitor monitor)
      throws CoreException {
    SubMonitor subMonitor = SubMonitor.convert(monitor,
        Messages.getString("add.appengine.standard.facet"), 100); 
 
    AppEngineStandardFacet.installAppEngineFacet(
        newProject, true /* installDependentFacets */, subMonitor.split(100));
  }

  @Override
  public String getDescription() {
    return Messages.getString("creating.app.engine.standard.project"); //$NON-NLS-1$
  }

  @Override
  public IFile createAndConfigureProjectContent(IProject newProject, AppEngineProjectConfig config,
      IProgressMonitor monitor) throws CoreException {
    return CodeTemplates.materializeAppEngineStandardFiles(newProject, config, monitor);
  }
}
