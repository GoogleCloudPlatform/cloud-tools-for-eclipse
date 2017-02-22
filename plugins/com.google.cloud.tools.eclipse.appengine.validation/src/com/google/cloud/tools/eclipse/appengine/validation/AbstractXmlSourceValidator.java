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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.wst.sse.ui.internal.reconcile.validator.ISourceValidator;
import org.eclipse.wst.validation.internal.core.ValidationException;
import org.eclipse.wst.validation.internal.operations.LocalizedMessage;
import org.eclipse.wst.validation.internal.provisional.core.IMessage;
import org.eclipse.wst.validation.internal.provisional.core.IReporter;
import org.eclipse.wst.validation.internal.provisional.core.IValidationContext;
import org.eclipse.wst.validation.internal.provisional.core.IValidator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Abstract source view validator.
 */
public abstract class AbstractXmlSourceValidator implements ISourceValidator, IValidator {
  IDocument document;
  
  private static final Logger logger = Logger.getLogger(
      AppEngineWebXmlValidator.class.getName());
  
  /**
   * Extracts byte[] from XML. 
   */
  public void validate(IValidationContext helper, IReporter reporter) throws ValidationException {
    String documentContents = document.get();
    byte[] bytes = documentContents.getBytes();
    try {
      this.validate(reporter, bytes);
    } catch (IOException | CoreException | ParserConfigurationException ex) {
      logger.log(Level.SEVERE, ex.getMessage());
    }
    
  }
  
  /**
   * Adds an {@link IMessage} to XML file for every 
   * {@link BannedElement} found in the file.
   */
  abstract protected void validate(IReporter reporter, byte[] bytes) 
      throws CoreException, IOException, ParserConfigurationException;
  
  /**
   * Creates a message from a given {@link BannedElement}.
   */
  void createMessage(IReporter reporter, BannedElement element, int elementOffset,
      String markerId, int severity) throws CoreException {
    IMessage message = new LocalizedMessage(severity, element.getMessage());
    message.setTargetObject(this);
    message.setMarkerId(markerId);
    message.setLineNo(element.getStart().getLineNumber());
    message.setOffset(elementOffset);
    message.setLength(element.getLength());
    reporter.addMessage(this, message);
  }
  
  /**
   * Sets error message where SAX parser fails.
   */
  void createSaxErrorMessage(IReporter reporter, SAXException ex) throws CoreException {
    IMessage message = new LocalizedMessage(IMessage.HIGH_SEVERITY, ex.getMessage());
    message.setTargetObject(this);
    message.setLineNo(((SAXParseException) ex.getException()).getLineNumber());
    message.setOffset(1);
    message.setLength(0);
    reporter.addMessage(this, message);
  }

  @Override
  public void cleanup(IReporter reporter) {
  }
  
  @Override
  public void connect(IDocument document) {
    this.document = document;
  }
  
  @Override
  public void disconnect(IDocument document) {
    this.document = null;
  }

  @Override
  public void validate(IRegion dirtyRegion, IValidationContext helper, IReporter reporter) {
  }
  
}