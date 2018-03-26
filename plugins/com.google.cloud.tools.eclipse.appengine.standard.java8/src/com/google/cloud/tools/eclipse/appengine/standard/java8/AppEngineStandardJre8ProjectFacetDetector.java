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

package com.google.cloud.tools.eclipse.appengine.standard.java8;

import com.google.cloud.tools.appengine.AppEngineDescriptor;
import com.google.cloud.tools.eclipse.appengine.facets.AppEngineStandardFacet;
import com.google.cloud.tools.eclipse.appengine.facets.FacetUtil;
import com.google.cloud.tools.eclipse.appengine.facets.WebProjectUtil;
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

public class AppEngineStandardJre8ProjectFacetDetector extends ProjectFacetDetector {
  private static final Logger logger = Logger.getLogger(AppEngineStandardJre8ProjectFacetDetector.class.getName());

  @Override
  public void detect(IFacetedProjectWorkingCopy workingCopy, IProgressMonitor monitor)
      throws CoreException {
    String projectName = workingCopy.getProjectName();
    SubMonitor progress = SubMonitor.convert(monitor, 5);

    if (workingCopy.hasProjectFacet(AppEngineStandardFacet.FACET)) {
      return;
    }

    // Check if there are some fundamental conflicts with AESv8 other than Java and DWP versions
    if (FacetUtil.conflictsWith(workingCopy,
        AppEngineStandardFacetChangeListener.APP_ENGINE_STANDARD_JRE8,
        Arrays.asList(JavaFacet.FACET, WebFacetUtils.WEB_FACET))) {
      logger.warning(
          "skipping " + projectName + ": project conflicts with AES Java 8 runtime");
      return;
    }

    IFile appEngineWebXml =
        WebProjectUtil.findInWebInf(workingCopy.getProject(), new Path("appengine-web.xml"));
    if (appEngineWebXml == null || !appEngineWebXml.exists()) {
      return;
    }
    progress.worked(1);
    try (InputStream content = appEngineWebXml.getContents()) {
      AppEngineDescriptor descriptor = AppEngineDescriptor.parse(content);
      progress.worked(1);
      if (!descriptor.isJava8()) {
        return;
      }
      
      workingCopy.addProjectFacet(AppEngineStandardFacetChangeListener.APP_ENGINE_STANDARD_JRE8);
      progress.worked(1);

      // Always change to the Java 8 facet
      if (!workingCopy.hasProjectFacet(JavaFacet.VERSION_1_8)) {
        if (workingCopy.hasProjectFacet(JavaFacet.FACET)) {
          workingCopy.changeProjectFacetVersion(JavaFacet.VERSION_1_8);
        } else {
          Object javaModel = FacetUtil.createJavaDataModel(workingCopy.getProject());
          workingCopy.addProjectFacet(JavaFacet.VERSION_1_8);
          workingCopy.setProjectFacetActionConfig(JavaFacet.FACET, javaModel);
        }
        progress.worked(1);
      }

      // But we don't touch the Dynamic Web facet unless required
      if (!workingCopy.hasProjectFacet(WebFacetUtils.WEB_FACET)) {
        // Should we attempt to detect the version from web.xml? what if web.xml doesn't exist?
        Object webModel =
            FacetUtil.createWebFacetDataModel(appEngineWebXml.getParent().getParent());
        workingCopy.addProjectFacet(WebFacetUtils.WEB_31);
        workingCopy.setProjectFacetActionConfig(WebFacetUtils.WEB_FACET, webModel);
        progress.worked(1);
      }
      AppEngineStandardFacet.installAllAppEngineRuntimes(workingCopy, progress.split(1));
    } catch (SAXException | IOException ex) {
      throw new CoreException(StatusUtil.error(this, "Unable to retrieve appengine-web.xml", ex));
    }
  }
}
