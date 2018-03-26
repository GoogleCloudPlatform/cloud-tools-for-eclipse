/*
 * Copyright 2016 Google Inc.
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

package com.google.cloud.tools.eclipse.appengine.facets;

import com.google.cloud.tools.eclipse.appengine.deploy.flex.FlexDeployPreferences;
import com.google.cloud.tools.eclipse.util.Templates;
import com.google.cloud.tools.eclipse.util.io.ResourceUtils;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.wst.common.project.facet.core.IDelegate;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;

/**
 * Creates {@code src/main/appengine/app.yaml} if not present. Applies to both the WAR facet and
 * the JAR facet.
 */
public class FlexFacetInstallDelegate implements IDelegate {
  @Override
  public void execute(IProject project,
                      IProjectFacetVersion version,
                      Object config,
                      IProgressMonitor monitor) throws CoreException {
    createConfigFiles(project, monitor);
  }

  // TODO: https://github.com/GoogleCloudPlatform/google-cloud-eclipse/issues/1640
  private void createConfigFiles(IProject project, IProgressMonitor monitor) throws CoreException {
    SubMonitor subMonitor = SubMonitor.convert(monitor, 100);

    FlexDeployPreferences flexDeployPreferences = new FlexDeployPreferences(project);
    String appYamlPath = flexDeployPreferences.getAppYamlPath();
    IFile appYaml = project.getFile(appYamlPath);
    if (appYaml.exists()) {
      return;
    }

    IContainer appYamlParentFolder = appYaml.getParent();
    if (!appYamlParentFolder.exists()) {
      ResourceUtils.createFolders(appYamlParentFolder, subMonitor.split(5));
    }

    appYaml.create(new ByteArrayInputStream(new byte[0]), true, subMonitor.split(10));
    String configFileLocation = appYaml.getLocation().toString();
    Templates.createFileContent(
        configFileLocation, Templates.APP_YAML_TEMPLATE,
        Collections.<String, String>emptyMap());
    subMonitor.worked(55);

    appYaml.refreshLocal(IResource.DEPTH_ZERO, subMonitor.split(30));
  }
}
