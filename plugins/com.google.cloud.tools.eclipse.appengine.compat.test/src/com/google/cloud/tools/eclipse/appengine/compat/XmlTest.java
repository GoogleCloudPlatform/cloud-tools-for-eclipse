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

package com.google.cloud.tools.eclipse.appengine.compat;

import com.google.cloud.tools.eclipse.test.util.BasePluginXmlTest;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class XmlTest extends BasePluginXmlTest {

  @Test
  public void testFacetsDefined() {
    checkFacetDefined("com.google.appengine.facet");
    checkFacetDefined("com.google.appengine.facet.ear");
  }

  private void checkFacetDefined(String facetId) {
    NodeList elements = getDocument().getElementsByTagName("project-facet");
    for (int i = 0; i < elements.getLength(); i++) {
      Element element = (Element) elements.item(i);
      String facet = element.getAttribute("id");
      Assert.assertTrue(ProjectFacetsManager.isProjectFacetDefined(facet));
    }
  }
}
