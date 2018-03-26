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

package com.google.cloud.tools.eclipse.appengine.localserver.launching;

import com.google.cloud.tools.eclipse.appengine.localserver.Messages;
import com.google.cloud.tools.eclipse.appengine.localserver.server.LocalAppEngineServerDelegate;
import com.google.cloud.tools.eclipse.util.AdapterUtil;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.IServer;
import org.eclipse.wst.server.core.IServerType;
import org.eclipse.wst.server.core.IServerWorkingCopy;
import org.eclipse.wst.server.core.ServerCore;
import org.eclipse.wst.server.core.ServerUtil;
import org.eclipse.wst.server.ui.internal.ServerUIPlugin;

/**
 * A helper class for launching modules on a server
 */
public class LaunchHelper {
  private static final Logger logger = Logger.getLogger(LaunchHelper.class.getName());

  public void launch(IModule[] modules, String launchMode) throws CoreException {
    SubMonitor progress = SubMonitor.convert(null);
    Collection<IServer> servers =
        findExistingServers(modules, /* exact */ true, progress.split(3));
    IServer server = null;
    if (!servers.isEmpty()) {
      for (IServer existing : servers) {
        if (isRunning(existing)) {
          ILaunch launch = existing.getLaunch();
          Preconditions.checkNotNull(launch, "A running server should have a launch"); //$NON-NLS-1$
          String detail = launchMode.equals(launch.getLaunchMode()) ? Messages.getString("SERVER_ALREADY_RUNNING") //$NON-NLS-1$
              : MessageFormat.format(Messages.getString("SERVER_ALREADY_RUNNING_IN_MODE"), //$NON-NLS-1$
                  launch.getLaunchMode());
          throw new CoreException(StatusUtil.info(this, detail));
        }
        server = existing;
      }
    }
    if (server == null) {
      server = createServer(modules, progress.split(3));
    }
    launch(server, launchMode, progress.split(4));
  }

  private static boolean isRunning(IServer server) {
    return server.getServerState() == IServer.STATE_STARTED
        || server.getServerState() == IServer.STATE_STARTING;
  }

  /**
   * Look for servers that may match.
   * 
   * @param modules the web modules to search for
   * @param exact if true, look for exact module match
   * @return an existing server
   */
  @VisibleForTesting
  Collection<IServer> findExistingServers(IModule[] modules, boolean exact,
      SubMonitor progress) {
    if (modules.length == 1) {
      IServer defaultServer = ServerCore.getDefaultServer(modules[0]);
      if (defaultServer != null && defaultServer.getServerType() != null
          && LocalAppEngineServerDelegate.SERVER_TYPE_ID
              .equals(defaultServer.getServerType().getId())) {
        return Collections.singletonList(defaultServer);
      }
    }
    Set<IModule> myModules = ImmutableSet.copyOf(modules);
    List<IServer> matches = new ArrayList<>();
    // Look for servers that contain these modules
    // Could prioritize servers that have *exactly* these modules,
    // or that have the smallest overlap
    for (IServer server : ServerCore.getServers()) {
      // obsolete or unavailable server definitions have serverType == null
      if (server.getServerType() == null
          || !LocalAppEngineServerDelegate.SERVER_TYPE_ID.equals(server.getServerType().getId())) {
        continue;
      }
      Set<IModule> serverModules = ImmutableSet.copyOf(server.getModules());
      SetView<IModule> overlap = Sets.intersection(myModules, serverModules);
      if (overlap.size() == myModules.size()
          && (!exact || overlap.size() == serverModules.size())) {
        matches.add(server);
      }
    }
    return matches;
  }

  private IServer createServer(IModule[] modules, SubMonitor progress) throws CoreException {
    IServerType serverType = ServerCore.findServerType(LocalAppEngineServerDelegate.SERVER_TYPE_ID);
    IServerWorkingCopy serverWorkingCopy =
        serverType.createServer(null, null, progress.split(4));
    serverWorkingCopy.modifyModules(modules, null, progress.split(4));
    return serverWorkingCopy.save(false, progress.split(2));
  }

  @VisibleForTesting
  protected void launch(IServer server, String launchMode, SubMonitor progress)
      throws CoreException {
    // Explicitly offer to save dirty editors to avoid the puzzling prompt-to-save in
    // IServer#start() that prompts the user *as the server continues to launch*.
    // ServerUIPlugin.saveEditors() respects the "Save editors before starting the server"
    // preference.
    if (!ServerUIPlugin.saveEditors()) {
      return;
    }
    server.start(launchMode, progress);
  }

  /** Identify the relevant modules from the selection. */
  public IModule[] asModules(ISelection selection) throws CoreException {
    if (selection instanceof IStructuredSelection && !selection.isEmpty()) {
      Object[] selectedObjects = ((IStructuredSelection) selection).toArray();
      List<IModule> modules = new ArrayList<>(selectedObjects.length);
      for (Object object : selectedObjects) {
        modules.add(asModule(object));
      }
      return modules.toArray(new IModule[modules.size()]);
    }
    throw new CoreException(
        StatusUtil.error(this, Messages.getString("CANNOT_DETERMINE_EXECUTION_CONTEXT"))); //$NON-NLS-1$
  }

  /** Check the project of the active editor. */
  public IModule[] asModules(IEditorPart editor) throws CoreException {
    if (editor != null && editor.getEditorInput() instanceof IFileEditorInput) {
      IFileEditorInput input = (IFileEditorInput) editor.getEditorInput();
      IProject project = input.getFile().getProject();
      if (project != null) {
        return new IModule[] {asModule(project)};
      }
    }
    throw new CoreException(
        StatusUtil.error(this, Messages.getString("CANNOT_DETERMINE_EXECUTION_CONTEXT"))); //$NON-NLS-1$
  }

  private IModule asModule(Object object) throws CoreException {
    IModule module = AdapterUtil.adapt(object, IModule.class);
    if (module != null) {
      return module;
    }
    IProject project = toProject(object);
    if (project != null) {
      module = ServerUtil.getModule(project);
      if (module != null) {
        return module;
      }
    }
    logger.warning("Unable to map to a module: " + object);
    throw new CoreException(
        StatusUtil.error(this, Messages.getString("CANNOT_DETERMINE_EXECUTION_CONTEXT"))); //$NON-NLS-1$
  }

  @VisibleForTesting
  static IProject toProject(Object object) {
    IProject project = AdapterUtil.adapt(object, IProject.class);
    if (project != null) {
      return project;
    }
    IResource resource = AdapterUtil.adapt(object, IResource.class);
    if (resource != null) {
      return resource.getProject();
    }
    return null;
  }

}
