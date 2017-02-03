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

package com.google.cloud.tools.eclipse.appengine.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

public class BlacklistScannerTest {
  
  private static final String ELEMENT_NAME = "application";
  private static final String ELEMENT_MESSAGE = "project ID tag not recommended";

  private BlacklistScanner scanner = new BlacklistScanner();
  
  @Before
  public void setUp() throws SAXException {
    scanner.startDocument();
    scanner.setDocumentLocator(new LocatorImpl());
  }
  
  @Test
  public void testStartDocument() throws SAXException {
    scanner.startDocument();
    assertNotNull(scanner.getBlacklist());
  }
  
  @Test
  public void testStartElement() throws SAXException {
    scanner.startElement("", "", ELEMENT_NAME, new AttributesImpl());
    assertEquals(1, scanner.getPreBlacklist().size());
    String message = scanner.getPreBlacklist().peek().getMessage();
    assertEquals(ELEMENT_MESSAGE, message);
  }
  
  @Test
  public void testEndElement() throws SAXException {
    scanner.startElement("", "", ELEMENT_NAME, new AttributesImpl());
    scanner.endElement("", "", ELEMENT_NAME);
    assertEquals(1, scanner.getBlacklist().size());
    String message = scanner.getBlacklist().peek().getMessage();
    assertEquals(ELEMENT_MESSAGE, message);
  }
  
}
