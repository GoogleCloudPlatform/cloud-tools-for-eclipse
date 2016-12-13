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

package com.google.cloud.tools.eclipse.test.util;

import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.RegistryFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class BasePluginXmlTest {

  @Rule public final PluginXmlDocument pluginXmlDocument = new PluginXmlDocument();
  private Document doc;

  @Before
  public void setUp() {
    doc = pluginXmlDocument.get();
  }

  protected Document getDocument() {
    return doc;
  }
  
  // Generic tests that should be true of all plugin.xml files
  
  @Test
  public void testRootElementIsPlugin() {
    Assert.assertEquals("plugin", getDocument().getDocumentElement().getNodeName());
  }
   
  @Test
  public void testValidExtensionPoints() {
    NodeList extensions = getDocument().getDocumentElement().getElementsByTagName("extension");
    
    // todo should we test that the file has at least one extension point?
    
    for (int i = 0; i < extensions.getLength(); i++) {
      Element extension = (Element) extensions.item(i);
      String point = extension.getAttribute("point");
      IExtensionRegistry registry = RegistryFactory.getRegistry();
      IExtensionPoint extensionPoint = registry.getExtensionPoint(point);
      Assert.assertNotNull(extensionPoint);
    }
  }

}
