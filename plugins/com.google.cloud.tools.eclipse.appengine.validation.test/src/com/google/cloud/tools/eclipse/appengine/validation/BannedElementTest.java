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
import org.junit.Test;

public class BannedElementTest {

  @Test
  public void testBannedElementConstructor_nullElementName() {
    BannedElement element = new BannedElement(null);
    assertNotNull(element.getMessage());
  }
  
  @Test
  public void testBannedElementConstructor_nullArgs() {
    BannedElement element = new BannedElement(null, null, null, 0);
    assertNotNull(element.getMessage());
    assertNotNull(element.getStart());
    assertNotNull(element.getEnd());
    assertEquals(0, element.getLength());
  }
  
}

