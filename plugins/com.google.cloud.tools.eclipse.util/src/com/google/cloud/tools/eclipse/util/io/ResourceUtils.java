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

package com.google.cloud.tools.eclipse.util.io;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;

/**
 * Utility methods for handling Eclipse Core Resources.
 */
public class ResourceUtils {

  /**
   * Create the components of the provided folder as required. Assumes the containing project
   * already exists.
   * 
   * @param folder the path to be created if it does not already exist
   * @param monitor may be {@code null}
   * @throws CoreException on error
   */
  public static void createFolders(IContainer folder, IProgressMonitor monitor)
      throws CoreException {
    
    IPath path = folder.getProjectRelativePath();
    IContainer current = folder.getProject();
    SubMonitor progress = SubMonitor.convert(monitor, path.segmentCount());
    for (String segment : path.segments()) {
      IFolder child = current.getFolder(new Path(segment));
      if (!child.exists()) {
        child.create(true, true, progress.split(1));
      } else {
        progress.worked(1);
      }
      current = child;
    }
  }


}
