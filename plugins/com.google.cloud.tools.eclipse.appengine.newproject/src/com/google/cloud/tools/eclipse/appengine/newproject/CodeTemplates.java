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

package com.google.cloud.tools.eclipse.appengine.newproject;

import com.google.cloud.tools.eclipse.appengine.ui.AppEngineRuntime;
import com.google.cloud.tools.eclipse.util.ArtifactRetriever;
import com.google.cloud.tools.eclipse.util.Templates;
import com.google.cloud.tools.eclipse.util.io.ResourceUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;

public class CodeTemplates {

  /**
   * Creates files for a sample App Engine Standard project in the supplied Eclipse project.
   *
   * @param project the Eclipse project to be filled with templated code
   * @param config replacement values
   * @param monitor progress monitor
   * @return the most important file created that should be opened in an editor
   */
  public static IFile materializeAppEngineStandardFiles(IProject project,
      AppEngineProjectConfig config, IProgressMonitor monitor) throws CoreException {
    return materialize(project, config, true /* isStandardProject */, monitor);
  }

  /**
   * Creates files for a sample App Engine Flexible project in the supplied Eclipse project.
   *
   * @param project the Eclipse project to be filled with templated code
   * @param config replacement values
   * @param monitor progress monitor
   * @return the most important file created that should be opened in an editor
   */
  public static IFile materializeAppEngineFlexFiles(IProject project, AppEngineProjectConfig config,
      IProgressMonitor monitor) throws CoreException {
    return materialize(project, config, false /* isStandardProject */, monitor);
  }

  /**
   * Creates files for a sample App Engine project in the supplied Eclipse project.
   *
   * @param project the Eclipse project to be filled with templated code
   * @param config replacement values
   * @param isStandardProject true if project should be configured to have the App Engine Standard
   *   configuration files and false if project should have the App Engine Flexible configuration
   *   files.
   * @param monitor progress monitor
   * @return the most important file created that should be opened in an editor
   */
  private static IFile materialize(IProject project, AppEngineProjectConfig config,
      boolean isStandardProject, IProgressMonitor monitor) throws CoreException {
    SubMonitor subMonitor = SubMonitor.convert(monitor, "Generating code", 45);

    IFile hello =
        createJavaSourceFiles(project, config, isStandardProject, subMonitor.split(15));

    createAppEngineWebXmlOrAppYaml(project, config, isStandardProject, subMonitor.split(5));

    createWebXml(project, config, isStandardProject, subMonitor.split(5));

    createWebContents(project, subMonitor.split(15));

    if (config.getUseMaven()) {
      createPomXml(project, config, isStandardProject, subMonitor.split(5));
    } else {
      subMonitor.worked(5);
    }

    return hello;
  }

  private static IFile createJavaSourceFiles(IProject project, AppEngineProjectConfig config,
      boolean isStandardProject, IProgressMonitor monitor) throws CoreException {
    SubMonitor subMonitor = SubMonitor.convert(monitor, 15);

    String packageName = config.getPackageName();
    String packagePath = packageName.replace('.', '/');
    IFolder mainPackageFolder = project.getFolder("src/main/java/" + packagePath); //$NON-NLS-1$
    IFolder testPackageFolder = project.getFolder("src/test/java/" + packagePath); //$NON-NLS-1$

    Map<String, String> properties = new HashMap<>();
    properties.put("package", Strings.nullToEmpty(packageName)); //$NON-NLS-1$
    if (isStandardProject
        && Objects.equal(AppEngineRuntime.STANDARD_JAVA_7.getId(), config.getRuntimeId())) {
      properties.put("servletVersion", "2.5"); //$NON-NLS-1$ //$NON-NLS-2$
    } else {
      properties.put("servletVersion", "3.1"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    IFile hello = createChildFile("HelloAppEngine.java", //$NON-NLS-1$
        Templates.HELLO_APPENGINE_TEMPLATE,
        mainPackageFolder, properties, subMonitor.split(5));

    createChildFile("HelloAppEngineTest.java", //$NON-NLS-1$
        Templates.HELLO_APPENGINE_TEST_TEMPLATE, testPackageFolder,
        properties, subMonitor.split(5));
    createChildFile("MockHttpServletResponse.java", //$NON-NLS-1$
        Templates.MOCK_HTTPSERVLETRESPONSE_TEMPLATE,
        testPackageFolder, properties, subMonitor.split(5));

    return hello;
  }

  private static void createAppEngineWebXmlOrAppYaml(IProject project,
      AppEngineProjectConfig config, boolean isStandardProject, IProgressMonitor monitor)
      throws CoreException {
    Map<String, String> properties = new HashMap<>();
    String service = config.getServiceName();
    if (!Strings.isNullOrEmpty(service)) {
      properties.put("service", service);  //$NON-NLS-1$
    }
    String runtime = config.getRuntimeId();
    if (!Strings.isNullOrEmpty(runtime)) {
      properties.put("runtime", runtime); //$NON-NLS-1$
    }

    if (isStandardProject) {
      IFolder webInf = project.getFolder("src/main/webapp/WEB-INF"); //$NON-NLS-1$
      createChildFile("appengine-web.xml", //$NON-NLS-1$
          Templates.APPENGINE_WEB_XML_TEMPLATE,
          webInf, properties, monitor);
    } else {
      IFolder appengine = project.getFolder("src/main/appengine"); //$NON-NLS-1$
      createChildFile("app.yaml", Templates.APP_YAML_TEMPLATE, //$NON-NLS-1$
          appengine, properties, monitor);
    }
  }

  private static void createWebXml(IProject project, AppEngineProjectConfig config,
      boolean isStandardProject, IProgressMonitor monitor) throws CoreException {
    Map<String, String> properties = new HashMap<>();

    String packageValue = config.getPackageName().isEmpty()
        ? ""  //$NON-NLS-1$
        : config.getPackageName() + "."; //$NON-NLS-1$
    properties.put("package", packageValue);  //$NON-NLS-1$

    if (isStandardProject
        && Objects.equal(AppEngineRuntime.STANDARD_JAVA_7.getId(), config.getRuntimeId())) {
      properties.put("servletVersion", "2.5"); //$NON-NLS-1$ //$NON-NLS-2$
      properties.put("namespace", "http://java.sun.com/xml/ns/javaee"); //$NON-NLS-1$ //$NON-NLS-2$
      properties.put("schemaUrl", "http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd"); //$NON-NLS-1$ //$NON-NLS-2$
    } else {
      properties.put("servletVersion", "3.1"); //$NON-NLS-1$ //$NON-NLS-2$
      properties.put("namespace", "http://xmlns.jcp.org/xml/ns/javaee"); //$NON-NLS-1$ //$NON-NLS-2$
      properties.put("schemaUrl", "http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    IFolder webInf = project.getFolder("src/main/webapp/WEB-INF"); //$NON-NLS-1$
    createChildFile("web.xml", Templates.WEB_XML_TEMPLATE, webInf, //$NON-NLS-1$
        properties, monitor);
  }

  private static void createWebContents(IProject project, IProgressMonitor monitor)
      throws CoreException {
    SubMonitor subMonitor = SubMonitor.convert(monitor, 10);

    IFolder webapp = project.getFolder("src/main/webapp"); //$NON-NLS-1$
    createChildFile("index.html", Templates.INDEX_HTML_TEMPLATE, webapp, //$NON-NLS-1$
        Collections.<String, String>emptyMap(), subMonitor.split(5));

    copyChildFile("favicon.ico", webapp, subMonitor.split(5)); //$NON-NLS-1$
  }

  private static void createPomXml(IProject project, AppEngineProjectConfig config,
      boolean isStandardProject, IProgressMonitor monitor) throws CoreException {
    Map<String, String> properties = new HashMap<>();
    properties.put("projectGroupId", config.getMavenGroupId()); //$NON-NLS-1$
    properties.put("projectArtifactId", config.getMavenArtifactId()); //$NON-NLS-1$
    properties.put("projectVersion", config.getMavenVersion()); //$NON-NLS-1$
    
    String bomVersion = getCurrentVersion(
        "com.google.cloud", //$NON-NLS-1$
        "google-cloud", //$NON-NLS-1$
        "0.40.0-alpha"); //$NON-NLS-1$
    properties.put("googleCloudJavaBomVersion", bomVersion); //$NON-NLS-1$
    
    String mavenPluginVersion = getCurrentVersion(
        "com.google.cloud.tools", //$NON-NLS-1$
        "appengine-maven-plugin", //$NON-NLS-1$
        "1.3.2"); //$NON-NLS-1$
    properties.put("mavenPluginVersion", mavenPluginVersion); //$NON-NLS-1$

    String sdkVersion = getCurrentVersion(
        "com.google.appengine", //$NON-NLS-1$
        "appengine-api-1.0-sdk", //$NON-NLS-1$
        "1.9.63"); //$NON-NLS-1$
    properties.put("appEngineApiSdkVersion", sdkVersion); //$NON-NLS-1$
    
    if (isStandardProject) {
      if (Objects.equal(AppEngineRuntime.STANDARD_JAVA_7.getId(), config.getRuntimeId())) {
        properties.put("servletVersion", "2.5"); //$NON-NLS-1$ //$NON-NLS-2$
        properties.put("compilerVersion", "1.7"); //$NON-NLS-1$ //$NON-NLS-2$
      } else {
        properties.put("servletVersion", "3.1"); //$NON-NLS-1$ //$NON-NLS-2$
        properties.put("compilerVersion", "1.8"); //$NON-NLS-1$ //$NON-NLS-2$
      }
      createChildFile("pom.xml", Templates.POM_XML_STANDARD_TEMPLATE, //$NON-NLS-1$
          project, properties, monitor);
    } else {
      createChildFile("pom.xml", Templates.POM_XML_FLEX_TEMPLATE, //$NON-NLS-1$
          project, properties, monitor);
    }
  }

  private static String getCurrentVersion(String group, String artifact, String defaultVersion) {
    ArtifactVersion version = ArtifactRetriever.DEFAULT.getBestVersion(group, artifact);
    if (version == null) {
      return defaultVersion;
    }
    return version.toString();
  }

  @VisibleForTesting
  static IFile createChildFile(String name, String template, IContainer parent,
      Map<String, String> values, IProgressMonitor monitor) throws CoreException {
    monitor.subTask("Creating file " + name);

    ResourceUtils.createFolders(parent, monitor);
    IFile child = parent.getFile(new Path(name));
    if (!child.exists()) {
      Templates.createFileContent(child.getLocation().toString(), template, values);
      child.refreshLocal(IResource.DEPTH_ZERO, monitor);
    }
    return child;
  }

  @VisibleForTesting
  static void copyChildFile(String name, IContainer parent, IProgressMonitor monitor)
      throws CoreException {
    monitor.subTask("Copying file " + name);

    ResourceUtils.createFolders(parent, monitor);
    IFile child = parent.getFile(new Path(name));
    if (!child.exists()) {
      Templates.copyFileContent(child.getLocation().toString(), name);
      child.refreshLocal(IResource.DEPTH_ZERO, monitor);
    }
  }

}
