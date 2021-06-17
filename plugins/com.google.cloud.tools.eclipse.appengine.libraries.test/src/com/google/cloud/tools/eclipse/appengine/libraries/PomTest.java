/*
 * Copyright 2017 Google LLC
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
import com.google.cloud.tools.eclipse.test.util.project.TestProjectCreator;
import com.google.cloud.tools.eclipse.util.ArtifactRetriever;
import com.google.cloud.tools.eclipse.util.MappedNamespaceContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class PomTest {
  
  @Rule public final TestProjectCreator projectCreator = new TestProjectCreator();

  // todo we're doing enough of this we should import or write some utilities
  private static final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
  private static final XPathFactory xpathFactory = XPathFactory.newInstance();

  @BeforeClass 
  public static void configureParser() {
    factory.setNamespaceAware(true);
  }
  
  private Pom pom;
  private IFile pomFile;
  private final XPath xpath = xpathFactory.newXPath();
  
  @Before
  public void setUp() throws SAXException, IOException, CoreException {
    xpath.setNamespaceContext(new MappedNamespaceContext("m", "http://maven.apache.org/POM/4.0.0"));
    
    IProject project = projectCreator.getProject();
    pomFile = project.getFile("pom.xml");
    try (
        InputStream in = Files.newInputStream(Paths.get("testdata/testpom.xml").toAbsolutePath())) {
      pomFile.create(in, IFile.FORCE, null);
      pom = Pom.parse(pomFile);
    }
    
    Logger logger = Logger.getLogger(ArtifactRetriever.class.getName());
    logger.setLevel(Level.OFF);
  }
  
  @After
  public void tearDown() {
    Logger logger = Logger.getLogger(ArtifactRetriever.class.getName());
    logger.setLevel(null);
  }
  
  @Test
  public void testDependencyManaged() {
    Assert.assertTrue(pom.dependencyManaged("com.google.cloud", "google-cloud-speech")); 
  }
  
  @Test
  public void testWrongScope() {
    Assert.assertFalse(pom.dependencyManaged("org.springframework", "spring-aop")); 
  }
  
  @Test
  public void testWrongType() {
    Assert.assertFalse(pom.dependencyManaged("org.apache.httpcomponents", "httpcore")); 
  }
  
  @Test
  public void testAddDependencies() 
      throws CoreException, ParserConfigurationException, IOException, SAXException {
    
    Library library0 =
        newLibrary("id0", new LibraryFile(coordinates("com.example.group0", "artifact0", "1.2.3")));
    Library library1 =
        newLibrary("id1", new LibraryFile(coordinates("com.example.group1", "artifact1")));
    Library library2 =
        newLibrary("id2", new LibraryFile(coordinates("com.example.group2", "artifact2")),
            new LibraryFile(coordinates("com.example.group3", "artifact3")));
    List<Library> libraries = Arrays.asList(library0, library1, library2);
    
    pom.addDependencies(libraries);
    
    Assert.assertEquals(1, pomFile.getHistory(null).length);
    
    try (InputStream contents = pomFile.getContents()) {
      Document actual = parse(contents);
      
      NodeList dependencies =
          actual.getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", "dependencies");
      Assert.assertEquals(2, dependencies.getLength());
      NodeList children = ((Element) dependencies.item(1))
          .getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", "dependency");
      
      Assert.assertEquals(4, children.getLength());
      
      Element child0 = (Element) children.item(0);
      Element groupId = getOnlyChild(child0, "groupId");
      Assert.assertEquals("com.example.group0", groupId.getTextContent());
      Element artifactId = getOnlyChild(child0, "artifactId");
      Assert.assertEquals("artifact0", artifactId.getTextContent());
      Element version = getOnlyChild(child0, "version");
      Assert.assertEquals("1.2.3", version.getTextContent());
      
      Element child3 = (Element) children.item(3);
      Element groupId3 = getOnlyChild(child3, "groupId");
      Assert.assertEquals("com.example.group3", groupId3.getTextContent());
      Element artifactId3 = getOnlyChild(child3, "artifactId");
      Assert.assertEquals("artifact3", artifactId3.getTextContent());
      
      // now make sure the comment didn't move to the end
      // https://bugs.openjdk.java.net/browse/JDK-8146163
      Assert.assertEquals(Node.COMMENT_NODE, actual.getChildNodes().item(0).getNodeType());
    }
  }

  @Test
  public void testAddDependencies_withDuplicates() throws CoreException,
      ParserConfigurationException, IOException, SAXException, XPathExpressionException {
    
    LibraryFile file1 = new LibraryFile(coordinates("com.example.group1", "artifact1"));
    LibraryFile file2 = new LibraryFile(coordinates("com.example.group2", "artifact2"));
    
    Library library1 = newLibrary("id1", file1);
    Library library2 = newLibrary("id2", file1, file2);

    List<Library> libraries = new ArrayList<>();
    libraries.add(library1);
    libraries.add(library2);
    
    pom.addDependencies(libraries);
    
    try (InputStream contents = pomFile.getContents()) {
      Document actual = parse(contents);

      NodeList dependencyNodes = (NodeList) xpath.evaluate(
            "./m:dependencies/m:dependency",
            actual.getDocumentElement(),
            XPathConstants.NODESET);
      Assert.assertEquals(2, dependencyNodes.getLength());
      
      Element child0 = (Element) dependencyNodes.item(0);
      Element groupId = getOnlyChild(child0, "groupId");
      Assert.assertEquals("com.example.group1", groupId.getTextContent());
      Element artifactId = getOnlyChild(child0, "artifactId");
      Assert.assertEquals("artifact1", artifactId.getTextContent());
      
      Element child1 = (Element) dependencyNodes.item(1);
      Element groupId1 = getOnlyChild(child1, "groupId");
      Assert.assertEquals("com.example.group2", groupId1.getTextContent());
      Element artifactId1 = getOnlyChild(child1, "artifactId");
      Assert.assertEquals("artifact2", artifactId1.getTextContent());
    }
  }
  
  @Test
  public void testAddDependencies_areDirect() 
      throws CoreException, ParserConfigurationException, IOException, SAXException {
    
    // objectify depends on guava
    Library library = newLibrary("objectify",
        new LibraryFile(coordinates("com.googlecode.objectify", "objectify", "5.1.10")));
    
    List<Library> libraries = new ArrayList<>();
    libraries.add(library);
    
    pom.addDependencies(libraries);
    
    try (InputStream contents = pomFile.getContents()) {
      Document actual = parse(contents);
      
      NodeList dependencies =
          actual.getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", "dependencies");
      Assert.assertEquals(2, dependencies.getLength());    
      
      Element dependency = getOnlyChild(((Element) dependencies.item(1)), "dependency");
      Element groupId = getOnlyChild(dependency, "groupId");
      Assert.assertEquals("com.googlecode.objectify", groupId.getTextContent());
      Element artifactId = getOnlyChild(dependency, "artifactId");
      Assert.assertEquals("objectify", artifactId.getTextContent());
      Element version = getOnlyChild(dependency, "version");
      Assert.assertNotEquals("5.1.10", version.getTextContent());
    }
  }
  
  @Test
  public void testPinnedDependencies() 
      throws CoreException, ParserConfigurationException, IOException, SAXException {
    
    LibraryFile objectify =
        new LibraryFile(coordinates("com.googlecode.objectify", "objectify", "5.1.10"));
    objectify.setPinned(true);
    Library library = newLibrary("objectify", objectify);
    
    List<Library> libraries = new ArrayList<>();
    libraries.add(library);
    
    pom.addDependencies(libraries);
    
    try (InputStream contents = pomFile.getContents()) {
      Document actual = parse(contents);
      
      NodeList dependencies =
          actual.getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", "dependencies");
      Assert.assertEquals(2, dependencies.getLength());    
      
      Element dependency = getOnlyChild(((Element) dependencies.item(1)), "dependency");
      Element version = getOnlyChild(dependency, "version");
      Assert.assertEquals("5.1.10", version.getTextContent());
    }
  }

  @Test
  public void testRemoveUnusedDependencies_selectAll()
      throws CoreException, ParserConfigurationException, IOException, SAXException {
    LibraryFile file1 = new LibraryFile(coordinates("com.example.group1", "artifact1"));
    LibraryFile file2 = new LibraryFile(coordinates("com.example.group2", "artifact2"));
    Library library1 = newLibrary("id1", file1);
    Library library2 = newLibrary("id2", file1, file2);

    pom.addDependencies(Arrays.asList(library1, library2));
    try (InputStream contents = pomFile.getContents()) {
      Document actual = parse(contents);
      NodeList dependenciesList =
          actual.getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", "dependencies");
      Assert.assertEquals(2, dependenciesList.getLength());
      
      // first one is in dependencyManagement
      Element dependencies = (Element) dependenciesList.item(1);
      Assert.assertEquals(2, dependencies
          .getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", "dependency").getLength());
  
      // no dependencies should be removed
      Pom.removeUnusedDependencies(dependencies, Arrays.asList(library1, library2),
          Arrays.asList(library1, library2));
      Assert.assertEquals(2, dependencies
          .getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", "dependency").getLength());
    }
  }

  @Test
  public void testRemoveUnusedDependencies_keepLibrary1()
      throws CoreException, ParserConfigurationException, IOException, SAXException {
    LibraryFile file1 = new LibraryFile(coordinates("com.example.group1", "artifact1"));
    LibraryFile file2 = new LibraryFile(coordinates("com.example.group2", "artifact2"));
    Library library1 = newLibrary("id1", file1);
    Library library2 = newLibrary("id2", file1, file2);

    pom.addDependencies(Arrays.asList(library1, library2));
    try (InputStream contents = pomFile.getContents()) {
      Document actual = parse(contents);
      NodeList dependenciesList = actual.getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", "dependencies");
      Assert.assertEquals(2, dependenciesList.getLength());
      Element dependencies = (Element) dependenciesList.item(1);
      Assert.assertEquals(2, dependencies.getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", "dependency").getLength());
  
      // dependencies from library2 should be removed
      Pom.removeUnusedDependencies(dependencies, Arrays.asList(library1),
          Arrays.asList(library1, library2));
      Assert.assertEquals(1, dependencies.getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", "dependency").getLength());
      Element dependency = getOnlyChild((dependencies), "dependency");
      Element groupId = getOnlyChild(dependency, "groupId");
      Assert.assertEquals("com.example.group1", groupId.getTextContent());
      Element artifactId = getOnlyChild(dependency, "artifactId");
      Assert.assertEquals("artifact1", artifactId.getTextContent());
    }
  }

  @Test
  public void testRemoveUnusedDependencies_removesAll()
      throws CoreException, ParserConfigurationException, IOException, SAXException {
    LibraryFile file1 = new LibraryFile(coordinates("com.example.group1", "artifact1"));
    LibraryFile file2 = new LibraryFile(coordinates("com.example.group2", "artifact2"));
    Library library1 = newLibrary("id1", file1);
    Library library2 = newLibrary("id2", file1, file2);

    pom.addDependencies(Arrays.asList(library1, library2));
    try (InputStream contents = pomFile.getContents()) {
      Document actual = parse(contents);
      NodeList dependenciesList = actual.getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", "dependencies");
      Assert.assertEquals(2, dependenciesList.getLength());
      Element dependencies = (Element) dependenciesList.item(1);
      Assert.assertEquals(2, dependencies.getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", "dependency").getLength());
  
      // all dependencies should be removed
      Pom.removeUnusedDependencies(dependencies, Collections.<Library>emptyList(),
          Arrays.asList(library1, library2));
      Assert.assertEquals(0, dependencies.getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", "dependency").getLength());
    }
  }

  @Test
  public void testResolveLibraries() throws CoreException {
    LibraryFile file1 = new LibraryFile(coordinates("com.example.group1", "artifact1"));
    LibraryFile file2 = new LibraryFile(coordinates("com.example.group2", "artifact2"));

    Library library1 = newLibrary("id1", file1);
    Library library2 = newLibrary("id2", file1, file2);

    pom.updateDependencies(Arrays.asList(library1), Arrays.asList(library1, library2));
    
    // library2 should not be resolved since file2 is not in the available libraries
    Collection<Library> resolved = pom.resolveLibraries(Arrays.asList(library1, library2));
    Assert.assertEquals(1, resolved.size());
    Assert.assertEquals(library1, resolved.iterator().next());

    pom.addDependencies(Arrays.asList(library2));

    // now both library1 and library2 should be resolved
    resolved = pom.resolveLibraries(Arrays.asList(library1, library2));
    Assert.assertEquals(2, resolved.size());
    Assert.assertThat(resolved, Matchers.hasItem(library1));
    Assert.assertThat(resolved, Matchers.hasItem(library2));
  }

  private static Document parse(InputStream in)
      throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document actual = builder.parse(in);
    return actual;
  }

  private static Element getOnlyChild(Element element, String name) {
    NodeList children = element.getElementsByTagNameNS("http://maven.apache.org/POM/4.0.0", name);
    if (children.getLength() == 0) {
      Assert.fail("No element " + name);
    }
    Assert.assertEquals("More than one " + name, 1, children.getLength());
    return (Element) children.item(0);
  }

  static Library newLibrary(String libraryId, LibraryFile... libraryFiles) {
    Library library = new Library(libraryId);
    library.setLibraryFiles(Arrays.asList(libraryFiles));
    return library;
  }

  static MavenCoordinates coordinates(String groupId, String artifactId) {
    return new MavenCoordinates.Builder().setGroupId(groupId).setArtifactId(artifactId).build();
  }

  static MavenCoordinates coordinates(String groupId, String artifactId, String version) {
    return new MavenCoordinates.Builder().setGroupId(groupId).setArtifactId(artifactId)
        .setVersion(version).build();
  }
}
