/*
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.eclipse.appengine.facets;

import com.google.cloud.tools.eclipse.util.io.ResourceUtils;
import com.google.common.collect.ImmutableList;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jst.j2ee.componentcore.J2EEModuleVirtualComponent;
import org.eclipse.wst.common.componentcore.ComponentCore;
import org.eclipse.wst.common.componentcore.resources.IVirtualComponent;
import org.eclipse.wst.common.componentcore.resources.IVirtualFile;
import org.eclipse.wst.common.componentcore.resources.IVirtualFolder;

/**
 * Utility classes for accessing and creating configuration files under {@code WEB-INF}. These
 * methods support WTP Web Projects ({@code jst.web} and {@code jst.utility}) and non-WTP projects
 * too.
 * <p>
 * WTP Web Projects use a virtual component model to define a logical file system layout for the web
 * module that is assembled from different source locations in the project. This virtual component
 * model is reflected in the project's <em>deployment assembly</em> property page. For example, the
 * {@code WEB-INF} directory may be assembled from files contained in
 * {@code target/m2e-wtp/web-resources/WEB-INF}, and {@code src/main/webapp/WEB-INF}. The virtual
 * component model defines the order of lookup and where new files should be created.
 */
public class WebProjectUtil {
  /** Default top-level locations for WEB_INF */
  public static final String DEFAULT_WEB_PATH = "src/main/webapp";

  /** All possible top-level locations for WEB_INF */
  private static final ImmutableList<String> DEFAULT_WEB_PATHS =
      ImmutableList.of(DEFAULT_WEB_PATH, "WebContent", "war", "web");

  static final String WEB_INF = "WEB-INF";

  /**
   * Create a file in the appropriate location for the project's {@code WEB-INF}. This
   * implementation respects the order and tags on the WTP virtual component model, creating the
   * file in the {@code <wb-resource>} with the {@code defaultRootSource} tag when present. This
   * method is typically used after ensuring the file does not exist with
   * {@link #findInWebInf(IProject, IPath)}.
   * 
   * @param project the hosting project
   * @param filePath the path of the file within the project's {@code WEB-INF}
   * @param contents the content for the file
   * @see #findInWebInf(IProject, IPath)
   * @see <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=448544">Eclipse bug 448544</a>
   *       for details of the {@code defaultRootSource} tag
   */
  public static IFile createFileInWebInf(IProject project, IPath filePath, InputStream contents,
      IProgressMonitor monitor) throws CoreException {
    IFolder webInfDir = findWebInfForNewResource(project);
    IFile file = webInfDir.getFile(filePath);
    if (!file.exists()) {
      SubMonitor progress = SubMonitor.convert(monitor, 2);
      ResourceUtils.createFolders(file.getParent(), progress.split(1));
      file.create(contents, true, progress.split(1));
    }
    return file;
  }


  /**
   * Create a folder in the appropriate location for the project's {@code WEB-INF}, respecting the
   * order and tags on the WTP virtual component model. Specifically, we create files and
   * directories in the {@code <wb-resource>} with the {@code defaultRootSource} tag when present.
   * 
   * @param project the hosting project
   * @param folderPath the path of the folder within the project's {@code WEB-INF}
   * @see <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=448544">Eclipse bug 448544</a>
   *       for details of the {@code defaultRootSource} tag
   */
  public static IFolder createFolderInWebInf(IProject project, Path folderPath,
      IProgressMonitor monitor) throws CoreException {
    IFolder webInfDir = findWebInfForNewResource(project);
    IFolder folder = webInfDir.getFolder(folderPath);
    if (!folder.exists()) {
      ResourceUtils.createFolders(folder, monitor);
    }
    return folder;
  }

  /**
   * Find the directory within the project where new resources for the {@code WEB-INF} should be
   * placed, respecting the order and tags on the WTP virtual component model. Specifically, we use
   * the directory in the {@code <wb-resource>} with the {@code defaultRootSource} tag when present.
   */
  private static IFolder findWebInfForNewResource(IProject project) {
    // Check if the project is a faceted project, and use the deployment assembly information
    IVirtualComponent component = ComponentCore.createComponent(project);
    if (component != null && component.exists()) {
      IVirtualFolder root = component.getRootFolder();
      // first see if there is a resource tagged as the defaultSourceRoot
      IPath defaultPath = J2EEModuleVirtualComponent.getDefaultDeploymentDescriptorFolder(root);
      if (defaultPath != null) {
        return project.getFolder(defaultPath).getFolder(WEB_INF);
      }
      // otherwise use the first
      return (IFolder) root.getFolder(WEB_INF).getUnderlyingFolder();
    }
    // Otherwise it's seemingly fair game
    for (String possibleWebInfContainer : DEFAULT_WEB_PATHS) {
      // simplify mocking: get the location as two parts and check for null despite that getFolder()
      // should be @NonNull
      IFolder defaultLocation = project.getFolder(possibleWebInfContainer);
      if (defaultLocation != null && defaultLocation.exists()) {
        defaultLocation = defaultLocation.getFolder(WEB_INF);
        if (defaultLocation != null && defaultLocation.exists()) {
          return defaultLocation;
        }
      }
    }
    return project.getFolder(DEFAULT_WEB_PATH).getFolder(WEB_INF);
  }

  /**
   * Attempt to resolve the given file within the project's {@code WEB-INF}. Note that this method
   * may return a file that is in a build location (e.g.,
   * {@code target/m2e-wtp/web-resources/WEB-INF}) which may be frequently removed or regenerated.
   *
   * @return the file location or {@code null} if not found
   */
  public static IFile findInWebInf(IProject project, IPath filePath) {
    // Try to obtain the directory as if it was a Dynamic Web Project
    IVirtualComponent component = ComponentCore.createComponent(project);
    if (component != null && component.exists()) {
      IVirtualFolder root = component.getRootFolder();
      // the root should exist, but the WEB-INF may not yet exist
      IVirtualFile file = root.getFolder(WEB_INF).getFile(filePath);
      if (file != null && file.exists()) {
        return file.getUnderlyingFile();
      }
      return null;
    }
    // Otherwise check the standard places
    for (String possibleWebInfContainer : DEFAULT_WEB_PATHS) {
      // check each directory component to simplify mocking in tests
      // so we can just say WEB-INF doesn't exist
      IFolder defaultLocation = project.getFolder(possibleWebInfContainer);
      if (defaultLocation != null && defaultLocation.exists()) {
        defaultLocation = defaultLocation.getFolder(WEB_INF);
        if (defaultLocation != null && defaultLocation.exists()) {
          IFile resourceFile = defaultLocation.getFile(filePath);
          if (resourceFile.exists()) {
            return resourceFile;
          }
        }
      }
    }
    return null;
  }

  /**
   * Return the set of Java source paths for the given project, relative to the project. Return an
   * empty list if not a Java project.
   */
  public static List<IPath> getJavaSourcePaths(IProject project) {
    IJavaProject javaProject = JavaCore.create(project);
    if (!javaProject.exists()) {
      return Collections.emptyList();
    }
    try {
      IClasspathEntry[] classpathEntries = javaProject.getRawClasspath();
      List<IPath> paths = new ArrayList<>();
      for (IClasspathEntry entry : classpathEntries) {
        if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
          // source paths are absolute to the root folder of the project
          paths.add(entry.getPath().removeFirstSegments(1));
        }
      }
      return paths;
    } catch (JavaModelException ex) {
      return Collections.emptyList();
    }
  }
}
