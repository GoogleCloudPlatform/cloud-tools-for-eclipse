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

import com.google.cloud.tools.eclipse.appengine.facets.WebProjectUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Stack;
import javax.xml.parsers.ParserConfigurationException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.wst.validation.AbstractValidator;
import org.eclipse.wst.validation.ValidationEvent;
import org.eclipse.wst.validation.ValidatorMessage;
import org.eclipse.wst.validation.ValidationResult;
import org.eclipse.wst.validation.ValidationState;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Validator for appengine-web.xml
 */
public class AppEngineWebXmlValidator extends AbstractValidator {
  
  private static final Logger logger = Logger.getLogger(
      AppEngineWebXmlValidator.class.getName());
  private IProject project;
  
  @Override
  public void validationStarting(IProject project,
      ValidationState state, IProgressMonitor monitor) {        
    this.project = project;
  }
  
  /**
   * Locates appengine-web.xml in project.
   */
  @Override
  public ValidationResult validate(ValidationEvent event, ValidationState state,
      IProgressMonitor monitor) {
    IResource resource = event.getResource();
    IFile file = WebProjectUtil.findInWebInf(project, new Path("appengine-web.xml"));
    try (InputStream in = file.getContents()) {
      byte[] bytes = ByteStreams.toByteArray(in);
      return validate(resource, bytes);
    } catch (IOException | CoreException | ParserConfigurationException ex) {
      logger.log(Level.WARNING, ex.getMessage());
      return new ValidationResult();
    }
  }
    
  /**
   * Validates given XML file. Assigns markers to all elements found in blacklist.
   */
  @VisibleForTesting
  static ValidationResult validate(IResource resource, byte[] bytes) 
      throws CoreException, IOException, ParserConfigurationException {
    try {
      Stack<BannedElement> blacklist = BlacklistSaxParser.readXml(bytes);
      return addMessages(resource, bytes, blacklist);
    } catch (SAXException ex) {
      return createSaxErrorMessage(resource, ex);
    }
  }
  
  /**
   * Adds message to project's IResource for every BannedElement in the blacklist.
   */
  @VisibleForTesting
  static ValidationResult addMessages(IResource resource, byte[] bytes,
      Stack<BannedElement> blacklist) throws IOException {
    ValidationResult result = new ValidationResult();
    Map<BannedElement, Integer> bannedElementOffsetMap = ValidationUtils.getOffsetMap(bytes, blacklist);
    for (BannedElement element : bannedElementOffsetMap.keySet()) {
      result.add(createMessage(resource, element, bannedElementOffsetMap.get(element)));
    }
    return result;
  }
  
  /**
   * Creates message from given BannedElement and adds message to project's IResource.
   */
  static ValidatorMessage createMessage(IResource resource, BannedElement element,
                                         int elementOffset) {
    ValidatorMessage message = ValidatorMessage.create(element.getMessage(), resource);
    message.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
    message.setAttribute(IMarker.SOURCE_ID, IMarker.PROBLEM);
    message.setAttribute(IMarker.CHAR_START, elementOffset);
    message.setAttribute(IMarker.CHAR_END, elementOffset + element.getLength());
    return message;
  }
  
  /**
   * Set error marker where SAX parser fails.
   */
  static ValidationResult createSaxErrorMessage(IResource resource, SAXException e) {
    ValidationResult result = new ValidationResult();
    ValidatorMessage message = ValidatorMessage.create(e.getMessage(), resource);
    message.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
    message.setAttribute(IMarker.SOURCE_ID, IMarker.PROBLEM);
    message.setAttribute(IMarker.LINE_NUMBER,
      ((SAXParseException)e.getException()).getLineNumber());
    result.add(message);
    return result;
  }
  
}