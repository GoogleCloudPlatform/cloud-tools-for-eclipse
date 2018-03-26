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

package com.google.cloud.tools.eclipse.appengine.deploy;

import com.google.common.base.Preconditions;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jst.server.core.IJ2EEModule;
import org.eclipse.jst.server.core.IUtilityModule;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.ServerUtil;
import org.eclipse.wst.server.core.model.IModuleResource;
import org.eclipse.wst.server.core.model.ModuleDelegate;
import org.eclipse.wst.server.core.util.ModuleFile;
import org.eclipse.wst.server.core.util.PublishUtil;

/**
 * Writes a WAR file of a project, or the exploded contents of it to a destination directory.
 */
public class WarPublisher {

  public static final Logger logger = Logger.getLogger(WarPublisher.class.getName());

  public static void publishExploded(IProject project, IPath destination,
      IPath safeWorkDirectory, IProgressMonitor monitor) throws CoreException {
    Preconditions.checkNotNull(project, "project is null"); //$NON-NLS-1$
    Preconditions.checkNotNull(destination, "destination is null"); //$NON-NLS-1$
    Preconditions.checkArgument(!destination.isEmpty(), "destination is empty path"); //$NON-NLS-1$
    Preconditions.checkNotNull(safeWorkDirectory, "safeWorkDirectory is null"); //$NON-NLS-1$
    if (monitor.isCanceled()) {
      throw new OperationCanceledException();
    }

    SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
    subMonitor.setTaskName(Messages.getString("task.name.publish.war"));

    IModuleResource[] resources =
        flattenResources(project, safeWorkDirectory, subMonitor.split(10));
    PublishUtil.publishFull(resources, destination, subMonitor.split(90));
  }

  public static void publishWar(IProject project, IPath destination, IPath safeWorkDirectory,
      IProgressMonitor monitor) throws CoreException {
    Preconditions.checkNotNull(project, "project is null"); //$NON-NLS-1$
    Preconditions.checkNotNull(destination, "destination is null"); //$NON-NLS-1$
    Preconditions.checkArgument(!destination.isEmpty(), "destination is empty path"); //$NON-NLS-1$
    Preconditions.checkNotNull(safeWorkDirectory, "safeWorkDirectory is null"); //$NON-NLS-1$
    if (monitor.isCanceled()) {
      throw new OperationCanceledException();
    }

    SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
    subMonitor.setTaskName(Messages.getString("task.name.publish.war"));

    IModuleResource[] resources =
        flattenResources(project, safeWorkDirectory, subMonitor.split(10));
    PublishUtil.publishZip(resources, destination, subMonitor.split(90));
  }

  private static IModuleResource[] flattenResources(IProject project, IPath safeWorkDirectory,
      IProgressMonitor monitor) throws CoreException {
    List<IModuleResource> resources = new ArrayList<>();

    IModule[] modules = ServerUtil.getModules(project);
    for (IModule module : modules) {
      ModuleDelegate delegate = (ModuleDelegate) module.loadAdapter(ModuleDelegate.class, monitor);
      if (delegate == null) {
        continue;
      }

      // module references can either be as members or child modules (http://eclip.se/467759)
      Collections.addAll(resources, delegate.members());

      // now handle web fragment child modules, if they exist
      for (IModule child : delegate.getChildModules()) {
        ModuleDelegate childDelegate = (ModuleDelegate)
            child.loadAdapter(ModuleDelegate.class, monitor);
        IJ2EEModule j2eeModule = (IJ2EEModule) child.loadAdapter(IJ2EEModule.class, monitor);
        IUtilityModule utilityModule =
            (IUtilityModule) child.loadAdapter(IUtilityModule.class, monitor);
        if (childDelegate == null || (j2eeModule == null && utilityModule == null)) {
          logger.log(Level.WARNING, "child modules other than J2EE module or utility module are "
              + "not supported: module=" + child + ", moduleType=" + child.getModuleType());
          continue;
        }

        // destination (relative to root), e.g., "WEB-INF/lib/spring-web-4.3.6.RELEASE.jar"
        IPath destination = new Path(delegate.getPath(child));
        String zipName = destination.lastSegment();  // to be created
        IPath zipParent = destination.removeLastSegments(1);

        boolean alreadyZip = j2eeModule != null && j2eeModule.isBinary()
            || utilityModule != null && utilityModule.isBinary();
        if (alreadyZip) {
          // per "isBinary()" Javadoc, "members()" should have a single resource.
          IModuleResource zipResource = childDelegate.members()[0];

          File javaIoFile = zipResource.getAdapter(File.class);
          IFile iFile = zipResource.getAdapter(IFile.class);

          if (javaIoFile != null) {
            resources.add(new ModuleFile(javaIoFile, zipName, zipParent));
          } else if (iFile != null) {
            resources.add(new ModuleFile(iFile, zipName, zipParent));
          }
        } else {
          IPath tempZip = safeWorkDirectory.append(zipName);
          PublishUtil.publishZip(childDelegate.members(), tempZip, monitor);
          resources.add(new ModuleFile(tempZip.toFile(), destination.lastSegment(), zipParent));
        }
      }
    }
    return resources.toArray(new IModuleResource[0]);
  }
}
