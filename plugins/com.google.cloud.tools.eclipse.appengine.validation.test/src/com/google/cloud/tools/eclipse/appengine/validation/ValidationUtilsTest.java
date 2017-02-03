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
import java.io.IOException;
import java.util.Map;
import java.util.Stack;

import org.junit.Before;
import org.junit.Test;

public class ValidationUtilsTest {
  
  private static final String NEWLINE_UNIX = "\n";
  private static final String NEWLINE_MAC = "\r";
  private static final String NEWLINE_WINDOWS = "\r\n";
  private static final String PROJECT_ID = "<application></application>";
  private static final String LINE_WITH_WHITESPACE = " \n";
  private static final String UTF = "UTF-8";
  private static final String ISO = "ISO-8859-1";
  private static final String CP = "CP1252";
  private static final String UNIX_XML_WITH_PROJECT_ID =
      "1234567" + NEWLINE_UNIX + PROJECT_ID;
  
  private static final String MAC_XML_WITH_PROJECT_ID =
      "1234567" + NEWLINE_MAC + PROJECT_ID;
  
  private static final String WINDOWS_XML_WITH_PROJECT_ID =
      "1234567" + NEWLINE_WINDOWS + PROJECT_ID;
  
  private static final String MIXED_XML_WITH_PROJECT_ID =
      NEWLINE_UNIX + MAC_XML_WITH_PROJECT_ID;
  
  private static final String XML_WITH_PROJECT_ID_WHITESPACE =
      LINE_WITH_WHITESPACE + PROJECT_ID;
  
  private static final String XML_WITH_PROJECT_ID_FIRST =
      PROJECT_ID;
  
  private Stack<BannedElement> blacklist;
  private BannedElement element;
  
  @Before
  public void setUp() {
    this.blacklist = new Stack<BannedElement>();
    DocumentLocation start = new DocumentLocation(2, 1);
    DocumentLocation end = new DocumentLocation(2, 2);
    element = new BannedElement("message", start, end, 1);
    blacklist.add(element);
  }
  
  @Test
  public void testGetOffsetMap_unixXml() throws IOException {
    byte[] bytes = UNIX_XML_WITH_PROJECT_ID.getBytes(UTF);
    Map<BannedElement, Integer> map = ValidationUtils.getOffsetMap(bytes, blacklist);
    assertEquals(1, map.size());
    int offset = map.get(element);
    assertEquals(8, offset);
  }
  
  @Test
  public void testGetOffsetMap_macXml() throws IOException {
    byte[] bytes = MAC_XML_WITH_PROJECT_ID.getBytes(ISO);
    Map<BannedElement, Integer> map = ValidationUtils.getOffsetMap(bytes, blacklist);
    assertEquals(1, map.size());
    int offset = map.get(element);
    assertEquals(8, offset);
  }
  
  @Test
  public void testGetOffsetMap_windowsXml() throws IOException {
    byte[] bytes = WINDOWS_XML_WITH_PROJECT_ID.getBytes(CP);
    Map<BannedElement, Integer> map = ValidationUtils.getOffsetMap(bytes, blacklist);
    assertEquals(1, map.size());
    int offset = map.get(element);
    assertEquals(8, offset);
  }
  
  @Test
  public void testGetOffsetMap_mixedXml() throws IOException {
    byte[] bytes = MIXED_XML_WITH_PROJECT_ID.getBytes(UTF);
    blacklist.clear();
    DocumentLocation start = new DocumentLocation(3, 1);
    DocumentLocation end = new DocumentLocation(3, 2);
    BannedElement element = new BannedElement("message", start, end, 1);
    blacklist.push(element);
    Map<BannedElement, Integer> map = ValidationUtils.getOffsetMap(bytes, blacklist);
    assertEquals(1, map.size());
    int offset = map.get(element);
    assertEquals(9, offset);
  }
  
  @Test
  public void testGetOffsetMap_lineWithWhitespace() throws IOException {
    byte[] bytes = XML_WITH_PROJECT_ID_WHITESPACE.getBytes(UTF);
    Map<BannedElement, Integer> map = ValidationUtils.getOffsetMap(bytes, blacklist);
    assertEquals(1, map.size());
    int offset = map.get(element);
    assertEquals(2, offset);
  }
  
  @Test
  public void testGetOffsetMap_firstElement() throws IOException {
    blacklist.clear();
    DocumentLocation start = new DocumentLocation(1, 1);
    DocumentLocation end = new DocumentLocation(1, 2);
    BannedElement newElement = new BannedElement("message", start, end, 1);
    blacklist.add(newElement);
    byte[] bytes = XML_WITH_PROJECT_ID_FIRST.getBytes(UTF);
    Map<BannedElement, Integer> map = ValidationUtils.getOffsetMap(bytes, blacklist);
    assertEquals(1, map.size());
    int offset = map.get(newElement);
    assertEquals(0, offset);
  }
}
