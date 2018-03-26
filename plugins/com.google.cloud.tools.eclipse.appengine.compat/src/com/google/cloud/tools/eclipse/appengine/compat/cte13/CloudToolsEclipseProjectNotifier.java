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

package com.google.cloud.tools.eclipse.appengine.compat.cte13;

import com.google.cloud.tools.eclipse.appengine.compat.Messages;
import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.Ordering;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

/**
 * Looks for projects using the per-library classpath container approach used in versions prior
 * Cloud Tools for Eclipse 1.4, and notifies the user to upgrade.
 */
public class CloudToolsEclipseProjectNotifier implements IStartup {
  private IWorkbench workbench;
  private IWorkspace workspace;

  @Override
  public void earlyStartup() {
    workbench = PlatformUI.getWorkbench();
    workspace = ResourcesPlugin.getWorkspace();

    Job projectUpdater = new WorkspaceJob(Messages.getString("updating.projects.jobname")) { //$NON-NLS-1$
      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        SubMonitor progress = SubMonitor.convert(monitor, 40);
        progress.subTask(Messages.getString("searching.for.projects")); //$NON-NLS-1$
        Collection<IProject> projects = findCandidates(progress.split(10));
        if (projects.isEmpty()) {
          return Status.OK_STATUS;
        }
        projects = promptUser(projects, progress.split(5));
        if (projects.isEmpty()) {
          return Status.OK_STATUS;
        }
        progress.subTask(Messages.getString("updating.projects")); //$NON-NLS-1$
        return upgradeProjects(projects, progress.split(25));
      }
    };
    projectUpdater.setRule(workspace.getRoot());
    projectUpdater.setUser(true);
    projectUpdater.schedule(500);
  }

  /**
   * Scan the current projects to identify those requiring upgrading.
   */
  private Collection<IProject> findCandidates(SubMonitor progress) {
    List<IProject> projects = new ArrayList<>();

    IProject[] allProjects = workspace.getRoot().getProjects();
    progress.setWorkRemaining(allProjects.length);
    for (IProject project : allProjects) {
      if (CloudToolsEclipseProjectUpdater.hasOldContainers(project)) {
        projects.add(project);
      }
      progress.worked(1);
    }
    Collections.sort(projects, Ordering.usingToString());
    return projects;
  }


  /**
   * Prompt the user to select the projects to upgrade.
   */
  private Collection<IProject> promptUser(Collection<IProject> projects, SubMonitor progress) {
    Preconditions.checkArgument(!projects.isEmpty(), "no projects specified!"); // $NON-NLS-1$ //$NON-NLS-1$
    progress.setBlocked(StatusUtil.info(this, Messages.getString("waiting.for.user"))); //$NON-NLS-1$
    boolean[] proceed = new boolean[1];
    workbench.getDisplay().syncExec(() -> {
      StringBuilder sb = new StringBuilder(
          Messages.getString("following.projects.must.be.updated")); //$NON-NLS-1$
      sb.append("\n"); //$NON-NLS-1$
      for (IProject project : projects) {
        sb.append("\n    ").append(project.getName()); //$NON-NLS-1$
      }
      sb.append("\n\n"); //$NON-NLS-1$
      sb.append(Messages.getString("update.now")); //$NON-NLS-1$
      proceed[0] =
          MessageDialog.openQuestion(getShell(), Messages.getString("cloud.tools.for.eclipse"), sb.toString()); //$NON-NLS-1$
    });
    progress.clearBlocked();
    return proceed[0] ? projects : Collections.<IProject>emptyList();
  }

  /**
   * Find a shell for the projects-require-updating prompt. May return {@code null}.
   */
  private Shell getShell() {
    if (workbench.getWorkbenchWindowCount() > 0) {
      if (workbench.getActiveWorkbenchWindow() != null) {
        return workbench.getActiveWorkbenchWindow().getShell();
      }
    }
    return workbench.getDisplay().getActiveShell();
  }

  /**
   * Perform the upgrade.
   */
  private IStatus upgradeProjects(Collection<IProject> projects, SubMonitor progress) {
    progress.setWorkRemaining(projects.size());
    MultiStatus status = StatusUtil.multi(this, Messages.getString("updating.projects.jobname")); //$NON-NLS-1$
    for (IProject project : projects) {
      progress.subTask(Messages.getString("updating.project", project.getName())); //$NON-NLS-1$
      IStatus result = CloudToolsEclipseProjectUpdater.updateProject(project, progress.split(1));
      status.merge(result);
    }
    // rewrite if OK as otherwise Progress View shows the "Updating projects for..." message
    return StatusUtil.filter(status);
  }
}
