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

package com.google.cloud.tools.eclipse.appengine.libraries;

import com.google.cloud.tools.eclipse.appengine.libraries.model.Library;
import com.google.cloud.tools.eclipse.appengine.libraries.model.LibraryFile;
import com.google.cloud.tools.eclipse.appengine.libraries.model.MavenCoordinates;
import com.google.cloud.tools.eclipse.util.ArtifactRetriever;
import com.google.cloud.tools.eclipse.util.MappedNamespaceContext;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

class Pom {

  private static final XPathFactory xpathFactory = XPathFactory.newInstance();
  // todo we're doing enough of this we should import or write some utilities
  private static final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
  private static final TransformerFactory transformerFactory = TransformerFactory.newInstance();
  private static final NamespaceContext maven4NamespaceContext =
      new MappedNamespaceContext("m", "http://maven.apache.org/POM/4.0.0");
  
  static {
    builderFactory.setNamespaceAware(true);
  }
  
  @VisibleForTesting
  final Document document;
  private final IFile pomFile;
  private final List<Bom> boms = new ArrayList<>();
  
  private Pom(Document document, IFile pomFile) {
    this.document = document;
    this.pomFile = pomFile;
  }

  static Pom parse(IFile pomFile) throws SAXException, IOException, CoreException {
    Preconditions.checkState(pomFile.exists(), pomFile.getFullPath() + " does not exist");
    
    try {
      DocumentBuilder builder = builderFactory.newDocumentBuilder();
      Document document = builder.parse(pomFile.getContents());
      Pom pom = new Pom(document, pomFile);
      
      XPath xpath = xpathFactory.newXPath();
      xpath.setNamespaceContext(maven4NamespaceContext);

      NodeList bomNodes = (NodeList) xpath.evaluate(
          "//m:dependencyManagement/m:dependencies/m:dependency[m:type='pom'][m:scope='import']",
          document.getDocumentElement(),
          XPathConstants.NODESET);
      
      for (int i = 0; i < bomNodes.getLength(); i++) {
        String artifactId = (String) xpath.evaluate("string(./m:artifactId)",
            bomNodes.item(i),
            XPathConstants.STRING);
        String groupId = (String) xpath.evaluate("string(./m:groupId)",
            bomNodes.item(i),
            XPathConstants.STRING);
        String version = (String) xpath.evaluate("string(./m:version)",
            bomNodes.item(i),
            XPathConstants.STRING);
        Bom bom = Bom.loadBom(groupId, artifactId, version, null);
        pom.boms.add(bom);
      } 
      
      return pom;
    } catch (ParserConfigurationException | XPathExpressionException ex) {
      IStatus status = StatusUtil.error(Pom.class, ex.getMessage(), ex);
      throw new CoreException(status);
    }
  }

  /**
   * Select libraries whose artifacts are satisfied by the pom's dependencies.
   */
  public Collection<Library> resolveLibraries(Collection<Library> availableLibraries) {
    XPath xpath = xpathFactory.newXPath();
    xpath.setNamespaceContext(maven4NamespaceContext);

    try {
      NodeList dependenciesNodes = (NodeList) xpath.evaluate(
          "./m:dependencies", // top-level elements only
          document.getDocumentElement(),
          XPathConstants.NODESET);
      if (dependenciesNodes.getLength() == 0) {
        return Collections.emptyList();
      } 
      
      final Element dependencies = (Element) dependenciesNodes.item(0);
  
      Predicate<LibraryFile> dependencyFound = libraryFile -> {
        Preconditions.checkNotNull(libraryFile);
        MavenCoordinates coordinates = libraryFile.getMavenCoordinates();
        String groupId = coordinates.getGroupId();
        String artifactId = coordinates.getArtifactId();
        return dependencyExists(dependencies, groupId, artifactId);
      };
  
      List<Library> matched = new ArrayList<>();
      for(Library library : availableLibraries) {
        boolean allMatch = library.getDirectDependencies().stream().allMatch(dependencyFound);
        if (allMatch) {
          matched.add(library);
        }
      }
      return matched;
    } catch (XPathExpressionException ex) {
      // this only happens if the XPath expression embedded in this code is malformed
      throw new RuntimeException(ex);
    }
  }

  /** Add dependencies required for the list of selected libraries. */
  void addDependencies(List<Library> selected) throws CoreException {
    updateDependencies(selected, null);
  }

  /**
   * Adjust the pom to add any required dependencies for the selected libraries and remove any
   * unnecessary dependencies for the removed libraries.
   * 
   * @param selectedLibraries the set of libraries to be included
   * @param removedLibraries these library dependencies are removed providing they are not required
   *        by any of the {@code selectedLibraries}
   */
  void updateDependencies(Collection<Library> selectedLibraries,
      Collection<Library> removedLibraries) throws CoreException {
    // see
    // m2e-core/org.eclipse.m2e.core.ui/src/org/eclipse/m2e/core/ui/internal/actions/AddDependencyAction.java
    // m2e-core/org.eclipse.m2e.core.ui/src/org/eclipse/m2e/core/ui/internal/editing/AddDependencyOperation.java
    
    XPath xpath = xpathFactory.newXPath();
    xpath.setNamespaceContext(maven4NamespaceContext);
    
    Element dependencies;
    try {
      NodeList dependenciesNodes = (NodeList) xpath.evaluate(
          "./m:dependencies", // top-level elements only
          document.getDocumentElement(),
          XPathConstants.NODESET);
      if (dependenciesNodes.getLength() > 0) {
        dependencies = (Element) dependenciesNodes.item(0);
      } else {
        dependencies = document.createElementNS(
            "http://maven.apache.org/POM/4.0.0", "dependencies");
      }
    } catch (XPathExpressionException ex) {
      IStatus status = StatusUtil.error(Pom.class, ex.getMessage(), ex);
      throw new CoreException(status);
    }

    // our template includes a <!— test dependencies —> comment 
    // to delimit compilation/runtime dependencies from test dependencies.
    Comment testComment = findTestComment(dependencies);
    
    createBOMIfNeeded(xpath);
    if (removedLibraries != null) {
      removeUnusedDependencies(dependencies, selectedLibraries, removedLibraries);
    }

    for (Library library : selectedLibraries) {
      for (LibraryFile artifact : library.getDirectDependencies()) {
        MavenCoordinates coordinates = artifact.getMavenCoordinates();
        
        String groupId = coordinates.getGroupId();
        String artifactId = coordinates.getArtifactId();
        
        Element dependency = findDependency(dependencies, groupId, artifactId);
        if (dependency == null) {
          dependency = document.createElementNS(
              "http://maven.apache.org/POM/4.0.0", "dependency");
          Element groupIdElement = document.createElementNS(
              "http://maven.apache.org/POM/4.0.0", "groupId");
          groupIdElement.setTextContent(groupId);
          dependency.appendChild(groupIdElement);

          Element artifactIdElement = document.createElementNS(
              "http://maven.apache.org/POM/4.0.0", "artifactId");
          artifactIdElement.setTextContent(artifactId);
          dependency.appendChild(artifactIdElement);

          if (testComment == null) {
            addChild(dependencies, dependency);
          } else {
            dependencies.insertBefore(dependency, testComment);
          }
        }
        handleDependencyManaged(artifact, dependency);
      }
    }
    
    if (dependencies.getParentNode() == null) {
      document.getDocumentElement().appendChild(dependencies);
    }
    
    try {
      writeDocument();
    } catch (TransformerException ex) {
      throw new CoreException(null);
    }   
  }

  private void handleDependencyManaged(LibraryFile artifact, Element dependency) {
    MavenCoordinates coordinates = artifact.getMavenCoordinates();
    String groupId = coordinates.getGroupId();
    String artifactId = coordinates.getArtifactId();
    Node versionNode = findChildByName(dependency, "version");
    if (!dependencyManaged(groupId, artifactId)) {
      if (versionNode == null) {
        String version = coordinates.getVersion();
        if (!artifact.isPinned()) {
          ArtifactVersion latestVersion =
              ArtifactRetriever.DEFAULT.getBestVersion(groupId, artifactId);
          if (latestVersion != null) {
            version = latestVersion.toString();
          }
        }
        // todo latest version may not be needed anymore.
        if (!MavenCoordinates.LATEST_VERSION.equals(version)) {
          Element versionElement =
              document.createElementNS("http://maven.apache.org/POM/4.0.0", "version");
          versionElement.setTextContent(version);
          dependency.appendChild(versionElement);
        }
      }
    } else {
      if (versionNode != null) {
        removeChild(dependency, versionNode);
      }
    }
  }

  private void createBOMIfNeeded(XPath xpath) throws CoreException {
    try {
      Element bomElement = (Element) xpath.evaluate(
          "//m:dependencyManagement/m:dependencies/m:dependency[m:groupId='com.google.cloud'][m:artifactId='libraries-bom']",
          document.getDocumentElement(), XPathConstants.NODE);
      if (bomElement == null) {
        Element dependencies = null;
        NodeList dependenciesNodes =
            (NodeList) xpath.evaluate("//m:dependencyManagement/m:dependencies",
                document.getDocumentElement(), XPathConstants.NODESET);
        if (dependenciesNodes.getLength() > 0) {
          dependencies = (Element) dependenciesNodes.item(0);
        } else {
          dependencies =
              document.createElementNS("http://maven.apache.org/POM/4.0.0", "dependencies");
          Node dependencyManagement = (Node) xpath.evaluate("//m:dependencyManagement",
              document.getDocumentElement(), XPathConstants.NODE);
          if (dependencyManagement == null) {
            dependencyManagement = document.createElementNS("http://maven.apache.org/POM/4.0.0",
                "dependencyManagement");
          }
          addChild(dependencyManagement, dependencies);
          addChild(document.getDocumentElement(), dependencyManagement);
        }

        Element dependency =
            document.createElementNS("http://maven.apache.org/POM/4.0.0", "dependency");
        dependency.appendChild(createChild("groupId", "com.google.cloud"));
        dependency.appendChild(createChild("artifactId", "libraries-bom"));
        dependency.appendChild(createChild("type", "pom"));
        dependency.appendChild(createChild("scope", "import"));
        String version = getBestVersion("com.google.cloud", "libraries-bom");
        dependency.appendChild(createChild("version", version));
        Bom bom = Bom.loadBom("com.google.cloud", "libraries-bom", version, null);
        if (bom != null) {
          boms.add(bom);
        }
        addChild(dependencies, dependency);
      }
    } catch (XPathExpressionException ex) {
      IStatus status = StatusUtil.error(Pom.class, ex.getMessage(), ex);
      throw new CoreException(status);
    }
  }

  private String getBestVersion(String groupId, String artifactId) {
    ArtifactVersion latestVersion = ArtifactRetriever.DEFAULT.getBestVersion(groupId, artifactId);
    return latestVersion != null ? latestVersion.toString() : MavenCoordinates.LATEST_VERSION;
  }

  private Element createChild(String name, String value) {
    Element child = document.createElementNS("http://maven.apache.org/POM/4.0.0", name);
    child.setTextContent(value);
    return child;
  }

  private static Comment findTestComment(Element dependencies) {
    NodeList children = dependencies.getChildNodes();
    
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node.getNodeType() == Node.COMMENT_NODE) {
        if (node.getNodeValue().trim().toLowerCase(Locale.US).startsWith("test")) {
          return (Comment) node; 
        }
      }
    }
    return null;
  }

  @VisibleForTesting
  static Node findChildByName(Element dependency, String name) {
    if (name == null || name.isEmpty())
      return null;
    NodeList childNodes = dependency.getChildNodes();
    for (int i = 0; i < childNodes.getLength(); i++) {
      Node node = childNodes.item(i);
      if (name.equals(node.getNodeName())) {
        return node;
      }
    }
    return null;
  }

  /**
   * @return true if and only if this artifact is controlled by a BOM
   */
  @VisibleForTesting
  boolean dependencyManaged(String groupId, String artifactId) {
    for (Bom bom : boms) {
      if (bom.defines(groupId, artifactId)) {
        return true; 
      }
    }
    return false;
  }

  /**
   * Remove any dependencies that are not required by the currently selected libraries.
   * 
   * @param selectedLibraries the currently selected libraries
   * @param removedLibraries previously selected libraries
   */
  @VisibleForTesting
  static void removeUnusedDependencies(Element dependencies,
      Collection<Library> selectedLibraries,
      Collection<Library> removedLibraries) {

    // a list of group:artifact keys that are currently required and must not be removed
    Set<String> selectedDependencies = new HashSet<>();
    for (Library library : selectedLibraries) {
      for (LibraryFile libraryFile : library.getDirectDependencies()) {
        MavenCoordinates coordinates = libraryFile.getMavenCoordinates();
        String encoded = coordinates.getGroupId() + ":" + coordinates.getArtifactId(); //$NON-NLS-1$
        selectedDependencies.add(encoded);
      }
    }
    Verify.verify(selectedDependencies.isEmpty() == selectedLibraries.isEmpty());

    // the currently specified dependencies (group:artifact -> DOM element)
    Map<String, Node> currentDependencies = new HashMap<>();
    NodeList children = dependencies.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element dependency = (Element) node;
        String groupId = getValue(dependency, "groupId"); //$NON-NLS-1$
        String artifactId = getValue(dependency, "artifactId"); //$NON-NLS-1$
        String encoded = groupId + ":" + artifactId; //$NON-NLS-1$
        currentDependencies.put(encoded, node);
      }
    }
    Verify.verify(currentDependencies.isEmpty() == (dependencies
        .getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", "dependency")
        .getLength() == 0));

    // iterate through each library-to-remove and, providing all of its dependencies are
    // present, then remove the dependencies that are not required by any selected library
    Set<Node> nodesToRemove = new HashSet<>();
    for (Library library : removedLibraries) {
      // true if all coordinates for this library are found
      boolean allFound = true;
      Set<Node> libraryNodes = new HashSet<>();
      for (LibraryFile file : library.getDirectDependencies()) {
        MavenCoordinates coord = file.getMavenCoordinates();
        String encoded = coord.getGroupId() + ":" + coord.getArtifactId(); //$NON-NLS-1$
        allFound &= currentDependencies.containsKey(encoded);
        // if not required by selected libraries then mark for removal
        if (allFound && !selectedDependencies.contains(encoded)) {
          libraryNodes.add(currentDependencies.get(encoded));
        }
      }
      // all library dependencies were found (i.e., the library was previously specified)
      if (allFound) {
        // remove all unnecessary dependencies
        nodesToRemove.addAll(libraryNodes);
      }
    }

    for (Node node : nodesToRemove) {
      removeChild(dependencies, node);
    }
  }

  private boolean dependencyExists(Element dependencies, String targetGroupId,
      String targetArtifactId) {
    return findDependency(dependencies, targetGroupId, targetArtifactId) != null;
  }

  @VisibleForTesting
  Element findDependency(Element dependencies, String targetGroupId, String targetArtifactId) {
    NodeList children = dependencies.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        Element dependency = (Element) node;
        String groupId = getValue(dependency, "groupId");
        String artifactId = getValue(dependency, "artifactId");
        if (targetGroupId.equals(groupId) && targetArtifactId.equals(artifactId)) {
          return dependency;
        }
      }
    }
    return null;
  }

  private static String getValue(Element dependency, String childName) {
    NodeList children = dependency.getElementsByTagNameNS(
        "http://maven.apache.org/POM/4.0.0", childName);
    if (children.getLength() > 0) {
      return children.item(0).getTextContent();
    }
    return null;
  }

  private void writeDocument() throws CoreException, TransformerException {
    Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount","2"); //$NON-NLS-1$ //$NON-NLS-2$
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    transformer.transform(new DOMSource(document), new StreamResult(out));
    InputStream in = new ByteArrayInputStream(out.toByteArray());
    
    pomFile.setContents(in, true, true, null);
  }

  private void addChild(Node parent, Node newChild) {
    parent.appendChild(newChild);
    removeWhiteSpaceBefore(newChild);// for child indentation
  }

  private static void removeChild(Node parent, Node oldChild) {
    removeWhiteSpaceBefore(oldChild);
    parent.removeChild(oldChild);
  }

  private static void removeWhiteSpaceBefore(Node node) {
    Node prevElement = node.getPreviousSibling();
    if (prevElement != null && prevElement.getNodeType() == Node.TEXT_NODE
        && prevElement.getNodeValue().trim().length() == 0) {
      node.getParentNode().removeChild(prevElement);
    }
  }
}
