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

package com.google.cloud.tools.eclipse.appengine.facets;

import com.google.cloud.tools.eclipse.test.util.project.TestProjectCreator;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jst.common.project.facet.core.JavaFacet;
import org.eclipse.jst.j2ee.web.project.facet.WebFacetUtils;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.IProjectFacet;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class FlexFacetInstallDelegateTest {
  @Rule public TestProjectCreator projectCreator = new TestProjectCreator().withFacetVersions(
          JavaFacet.VERSION_1_7, WebFacetUtils.WEB_25);

  @Test
  public void testFacetInstall() throws CoreException {
    IProject project = projectCreator.getProject();

    IProgressMonitor monitor = new NullProgressMonitor();
    IProjectFacet appEngineFacet = ProjectFacetsManager.getProjectFacet(AppEngineFlexWarFacet.ID);
    IProjectFacetVersion appEngineFacetVersion =
        appEngineFacet.getVersion(AppEngineFlexWarFacet.VERSION);
    IFacetedProject facetedProject = ProjectFacetsManager.create(project);
    facetedProject.installProjectFacet(appEngineFacetVersion, null /* config */, monitor);

    Assert.assertTrue(AppEngineFlexWarFacet.hasFacet(facetedProject));
    Assert.assertTrue(project.getFile("src/main/appengine/app.yaml").exists());
  }
}
