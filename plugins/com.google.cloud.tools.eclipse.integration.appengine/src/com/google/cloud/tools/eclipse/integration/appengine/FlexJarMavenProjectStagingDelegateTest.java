/*
 * Copyright 2017 Google Inc. All Rights Reserved.
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

package com.google.cloud.tools.eclipse.integration.appengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.cloud.tools.eclipse.appengine.deploy.StagingDelegate;
import com.google.cloud.tools.eclipse.appengine.deploy.flex.FlexJarMavenProjectStagingDelegate;
import com.google.cloud.tools.eclipse.test.util.project.ProjectUtils;
import java.io.IOException;
import java.util.List;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Test;

public class FlexJarMavenProjectStagingDelegateTest {

  @Test
  public void testStage() throws IOException, CoreException {
    List<IProject> projects = ProjectUtils.importProjects(getClass(),
        "test-projects/spring-boot-test.zip", false /* checkBuildErrors */, null);
    assertEquals(1, projects.size());

    IProject project = projects.get(0);
    IPath safeWorkDirectory = project.getFolder("safe-work-directory").getLocation();
    IPath stagingDirectory = project.getFolder("staging-result").getLocation();
    IPath appEngineDirectory = project.getFolder("src/main/appengine").getLocation();

    StagingDelegate delegate = new FlexJarMavenProjectStagingDelegate(appEngineDirectory);
    IStatus status = delegate.stage(project, stagingDirectory, safeWorkDirectory,
        null, null, new NullProgressMonitor());

    assertTrue(stagingDirectory.append("customized-final-artifact-name.jar").toFile().exists());
    assertTrue(stagingDirectory.append("app.yaml").toFile().exists());
    assertTrue(status.isOK());
  }
}
