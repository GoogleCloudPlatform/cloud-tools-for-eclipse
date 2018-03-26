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

package com.google.cloud.tools.eclipse.appengine.compat.gpe;

import com.google.cloud.tools.eclipse.appengine.facets.convert.AppEngineStandardProjectConvertJob;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;

public class GpeConvertJob extends AppEngineStandardProjectConvertJob {

  public GpeConvertJob(IFacetedProject facetedProject) {
    super("Google Plugin for Eclipse Project Conversion Job", facetedProject);
  }

  @Override
  protected void convert(MultiStatus status, IProgressMonitor monitor) {
    SubMonitor progress = SubMonitor.convert(monitor, 100);

    // Updating project before installing App Engine facet to avoid
    // https://github.com/GoogleCloudPlatform/google-cloud-eclipse/issues/1155.
    try {
      GpeMigrator.removeObsoleteGpeRemnants(facetedProject, progress.split(20));
      super.convert(status, progress.split(20));
    } catch (CoreException ex) {
      status.add(StatusUtil.error(this, "Unable to remove GPE remains", ex));
    }
  }


}
