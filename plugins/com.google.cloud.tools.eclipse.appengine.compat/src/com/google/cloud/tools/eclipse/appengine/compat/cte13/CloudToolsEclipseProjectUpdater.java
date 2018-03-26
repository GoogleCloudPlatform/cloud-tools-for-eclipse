/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.eclipse.appengine.compat.cte13;

import com.google.cloud.tools.eclipse.appengine.compat.Messages;
import com.google.cloud.tools.eclipse.appengine.libraries.BuildPath;
import com.google.cloud.tools.eclipse.appengine.libraries.LibraryClasspathContainer;
import com.google.cloud.tools.eclipse.appengine.libraries.model.CloudLibraries;
import com.google.cloud.tools.eclipse.appengine.libraries.model.Library;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

/**
 * Fix older projects that have a per-library classpath container.
 */
public class CloudToolsEclipseProjectUpdater {
  private static final Logger logger =
      Logger.getLogger(CloudToolsEclipseProjectUpdater.class.getName());

  /**
   * Return true if this projects uses the old-style per-library container.
   */
  public static boolean hasOldContainers(IProject project) {
    try {
      if (!project.isAccessible() || !project.hasNature(JavaCore.NATURE_ID)) {
        return false;
      }
      IJavaProject javaProject = JavaCore.create(project);
      for (IClasspathEntry entry : javaProject.getRawClasspath()) {
        IPath containerPath = entry.getPath();
        if (isLibraryContainer(entry, containerPath)
            && !CloudLibraries.MASTER_CONTAINER_ID.equals(containerPath.segment(1))) {
          return true;
        }
      }
    } catch (CoreException ex) {
      logger.log(Level.WARNING, "Skipping project: " + project.getName(), ex); //$NON-NLS-1$
    }
    return false;
  }

  /**
   * Upgrade this specific project.
   */
  public static IStatus updateProject(IProject project, SubMonitor progress) {
    progress.beginTask(Messages.getString("updating.project", project.getName()), 50); //$NON-NLS-1$
    IJavaProject javaProject = JavaCore.create(project);

    try {
      // Identify the different libraries that should be added and classpath entries to be preserved
      List<IClasspathEntry> remainingEntries = new ArrayList<>();
      Set<String> libraryIds = new HashSet<>();
      for (IClasspathEntry entry : javaProject.getRawClasspath()) {
        IPath containerPath = entry.getPath();
        if (isLibraryContainer(entry, containerPath)
            && !CloudLibraries.MASTER_CONTAINER_ID.equals(containerPath.segment(1))) {
          libraryIds.add(containerPath.segment(1));
        } else {
          remainingEntries.add(entry);
        }
      }

      // Update classpath to remove the old entries
      progress.subTask(Messages.getString("removing.old.library.classpath.containers")); //$NON-NLS-1$
      javaProject.setRawClasspath(
          remainingEntries.toArray(new IClasspathEntry[remainingEntries.size()]),
          progress.split(10));

      progress.subTask(Messages.getString("removing.old.library.container.definitions")); //$NON-NLS-1$
      for (String libraryId : libraryIds) {
        IFile definition = project.getFolder(".settings") //$NON-NLS-1$
            .getFolder(LibraryClasspathContainer.CONTAINER_PATH_PREFIX)
            .getFile(libraryId + ".container"); //$NON-NLS-1$
        if (definition.exists()) {
          definition.delete(true, null);
        }
      }
      progress.worked(5);

      // remove "appengine-api" as appengine-api-1.0-sdk now included in the servlet container
      libraryIds.remove("appengine-api"); //$NON-NLS-1$
      // remove "googlecloudcore" and "googleapiclient" as they were utility definitions, now
      // pulled from library dependencies
      libraryIds.remove("googlecloudcore"); //$NON-NLS-1$
      libraryIds.remove("googleapiclient"); //$NON-NLS-1$

      // add the master-library container
      List<Library> libraries = new ArrayList<>();
      for (String libraryId : libraryIds) {
        Library library = CloudLibraries.getLibrary(libraryId);
        if (library != null) {
          libraries.add(library);
        } else {
          logger.warning("Library not found: " + libraryId); //$NON-NLS-1$
        }
      }
      progress.worked(5);
      BuildPath.addNativeLibrary(javaProject, libraries, progress.split(30));
      return Status.OK_STATUS;
    } catch (CoreException ex) {
      return StatusUtil.error(CloudToolsEclipseProjectUpdater.class,
          Messages.getString("unable.to.update.project", project.getName()), ex); //$NON-NLS-1$
    }
  }

  private static boolean isLibraryContainer(IClasspathEntry entry, IPath containerPath) {
    return entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER
        && containerPath.segmentCount() == 2
        && LibraryClasspathContainer.CONTAINER_PATH_PREFIX
            .equals(containerPath.segment(0));
  }


}
