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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Validator for appengine-web.xml
 */
public class AppEngineWebXmlValidator extends AbstractXmlValidator {
  
  /**
   * Clears all problem markers from the resource, then adds a marker to 
   * appengine-web.xml for every {@link BannedElement} found in the file.
   * @throws CoreException 
   * @throws IOException 
   * @throws ParserConfigurationException 
   */
  @Override
  protected void validate(IFile resource, byte[] bytes)
      throws CoreException, ParserConfigurationException, IOException {
    try {
      deleteMarkers(resource);
      Document document = PositionalXmlScanner.parse(bytes);
      if (document != null) {
        ArrayList<String> blacklistedElements = AppEngineWebBlacklist.getBlacklistElements();
        ArrayList<BannedElement> blacklist =
            ValidationUtils.checkForElements(document, blacklistedElements);
        String encoding = (String) document.getDocumentElement().getUserData("encoding");
        Map<BannedElement, Integer> bannedElementOffsetMap =
            ValidationUtils.getOffsetMap(bytes, blacklist, encoding);
        for (Map.Entry<BannedElement, Integer> entry : bannedElementOffsetMap.entrySet()) {
          createMarker(resource, entry.getKey(), entry.getValue());
        }
      }
    } catch (SAXException ex) {
      createSaxErrorMessage(resource, ex);
    }
  }
  
}
