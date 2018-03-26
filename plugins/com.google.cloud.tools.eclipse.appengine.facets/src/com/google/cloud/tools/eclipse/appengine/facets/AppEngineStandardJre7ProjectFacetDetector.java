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

import com.google.cloud.tools.appengine.AppEngineDescriptor;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.logging.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jst.common.project.facet.core.JavaFacet;
import org.eclipse.jst.j2ee.web.project.facet.WebFacetUtils;
import org.eclipse.wst.common.project.facet.core.IFacetedProjectWorkingCopy;
import org.eclipse.wst.common.project.facet.core.ProjectFacetDetector;
import org.xml.sax.SAXException;

public class AppEngineStandardJre7ProjectFacetDetector extends ProjectFacetDetector {
  private static final Logger logger =
      Logger.getLogger(AppEngineStandardJre7ProjectFacetDetector.class.getName());

  @Override
  public void detect(IFacetedProjectWorkingCopy workingCopy, IProgressMonitor monitor)
      throws CoreException {
    String projectName = workingCopy.getProjectName();
    SubMonitor progress = SubMonitor.convert(monitor, 5);

    if (workingCopy.hasProjectFacet(AppEngineStandardFacet.FACET)) {
      return;
    }

    // Check if there are some fundamental conflicts with AESv7 other than Java and DWP versions
    if (FacetUtil.conflictsWith(workingCopy, AppEngineStandardFacet.JRE7,
        Arrays.asList(JavaFacet.FACET, WebFacetUtils.WEB_FACET))) {
      logger.warning(
          "skipping " + projectName + ": project conflicts with AES Java 7 runtime");
      return;
    }

    IFile appEngineWebXml =
        WebProjectUtil.findInWebInf(workingCopy.getProject(), new Path("appengine-web.xml"));
    progress.worked(1);
    if (appEngineWebXml == null || !appEngineWebXml.exists()) {
      logger.fine("skipping " + projectName + ": cannot find appengine-web.xml");
      return;
    }
    try (InputStream content = appEngineWebXml.getContents()) {
      AppEngineDescriptor descriptor = AppEngineDescriptor.parse(content);
      progress.worked(1);
      if (descriptor.getRuntime() != null && !"java7".equals(descriptor.getRuntime())) {
        logger.fine("skipping " + projectName + ": appengine-web.xml is not java7");
        return;
      }
      
      workingCopy.addProjectFacet(AppEngineStandardFacet.JRE7);
      progress.worked(1);

      if (!workingCopy.hasProjectFacet(JavaFacet.VERSION_1_7)) {
        if (workingCopy.hasProjectFacet(JavaFacet.FACET)) {
          workingCopy.changeProjectFacetVersion(JavaFacet.VERSION_1_7);
        } else {
          Object javaModel = FacetUtil.createJavaDataModel(workingCopy.getProject());
          workingCopy.addProjectFacet(JavaFacet.VERSION_1_7);
          workingCopy.setProjectFacetActionConfig(JavaFacet.FACET, javaModel);
        }
        progress.worked(1);
      }

      if (!workingCopy.hasProjectFacet(WebFacetUtils.WEB_25)) {
        if (workingCopy.hasProjectFacet(WebFacetUtils.WEB_FACET)) {
          workingCopy.changeProjectFacetVersion(WebFacetUtils.WEB_25);
        } else {
          Object webModel =
              FacetUtil.createWebFacetDataModel(appEngineWebXml.getParent().getParent());
          workingCopy.addProjectFacet(WebFacetUtils.WEB_25);
          workingCopy.setProjectFacetActionConfig(WebFacetUtils.WEB_FACET, webModel);
        }
        progress.worked(1);
      }
      AppEngineStandardFacet.installAllAppEngineRuntimes(workingCopy, progress.split(1));
    } catch (SAXException | IOException ex) {
      throw new CoreException(StatusUtil.error(this, "Unable to retrieve appengine-web.xml", ex));
    }
  }

}
