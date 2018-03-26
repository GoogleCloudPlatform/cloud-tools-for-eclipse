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

package com.google.cloud.tools.eclipse.appengine.facets;

import com.google.cloud.tools.eclipse.util.status.StatusUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jst.common.project.facet.core.JavaFacet;
import org.eclipse.jst.j2ee.web.project.facet.WebFacetUtils;
import org.eclipse.wst.common.componentcore.internal.builder.DependencyGraphImpl;
import org.eclipse.wst.common.componentcore.internal.builder.IDependencyGraph;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.IFacetedProjectBase;
import org.eclipse.wst.common.project.facet.core.IFacetedProjectWorkingCopy;
import org.eclipse.wst.common.project.facet.core.IProjectFacet;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;
import org.eclipse.wst.common.project.facet.core.runtime.IRuntime;
import org.eclipse.wst.server.core.IRuntimeType;
import org.eclipse.wst.server.core.IRuntimeWorkingCopy;
import org.eclipse.wst.server.core.ServerCore;

@SuppressWarnings("restriction") // For DependencyGraphImpl and IDependencyGraph
public class AppEngineStandardFacet {
  private static final Logger logger = Logger.getLogger(AppEngineStandardFacet.class.getName());

  public static final String ID = "com.google.cloud.tools.eclipse.appengine.facets.standard";

  public static final IProjectFacet FACET = ProjectFacetsManager.getProjectFacet(ID);
  // See AppEngineStandardFacetChangeListener.APP_ENGINE_STANDARD_JRE8 too
  public static final IProjectFacetVersion JRE7 = FACET.getVersion("JRE7");

  static final String DEFAULT_RUNTIME_ID =
      "com.google.cloud.tools.eclipse.appengine.standard.runtime";
  static final String DEFAULT_RUNTIME_NAME = "App Engine Standard";

  /**
   * Locks for managing simultaneous installation of App Engine Standard facets and runtimes. Use a
   * CacheBuilder as it synchronizes simultaneous requests for the same project.
   */
  private final static LoadingCache<IProject, ILock> installationLocks =
      CacheBuilder.newBuilder().weakValues().build(new CacheLoader<IProject, ILock>() {
        @Override
        public ILock load(IProject project) throws Exception {
          return Job.getJobManager().newLock();
        }
      });

  /**
   * Obtain the project lock.
   */
  private static ILock acquireLock(IProject project) throws CoreException {
    try {
      ILock lock = installationLocks.get(project);
      lock.acquire();
      return lock;
    } catch (ExecutionException ex) {
      throw new CoreException(
          StatusUtil.error(AppEngineStandardFacet.class, "Unable to acquire project lock", ex));
    }
  }


  /**
   * Returns true if project has the App Engine Standard facet and false otherwise.
   *
   * @param project should not be null
   * @return true if project has the App Engine Standard facet and false otherwise
   */
  public static boolean hasFacet(IFacetedProject project) {
    Preconditions.checkNotNull(project);
    return project.hasProjectFacet(FACET);
  }

  /**
   * Return the App Engine standard facet for the given project, or {@code null} if none.
   */
  public static IProjectFacetVersion getProjectFacetVersion(IProject project) {
    try {
      IFacetedProject facetedProject = ProjectFacetsManager.create(project);
      if (facetedProject == null) {
        return null;
      }
      return facetedProject.getProjectFacetVersion(FACET);
    } catch (CoreException ex) {
      return null;
    }
  }

  /**
   * Check that the given Servlet API version string (expected to be from the
   * <tt>&lt;web-app version="xxx"&gt;</tt> attribute), is compatible with this project's App Engine
   * settings.
   *
   * @return {@code true} if supported or {@code false} otherwise
   * @throws NullPointerException if the project does not have an App Engine Standard facet
   */
  public static boolean checkServletApiSupport(IProject project, String servletApiVersion) {
    IProjectFacetVersion appEngineFacetVersion = getProjectFacetVersion(project);
    Preconditions.checkNotNull(appEngineFacetVersion, "Missing App Engine Standard facet");
    return checkServletApiSupport(appEngineFacetVersion, servletApiVersion);
  }

  @VisibleForTesting
  static boolean checkServletApiSupport(IProjectFacetVersion appEngineFacetVersion,
      String servletApiVersion) {
    // Assume that App Engine Standard facet constraints are properly set up, so look up
    // corresponding jst.web version and check for conflicts
    try {
      IProjectFacetVersion dynamicWebFacetVersion =
          WebFacetUtils.WEB_FACET.getVersion(servletApiVersion);
      return dynamicWebFacetVersion != null
          && !appEngineFacetVersion.conflictsWith(dynamicWebFacetVersion);
    } catch (IllegalArgumentException ex) {
      // ignore: invalid version specified (e.g., "2.6")
      return false;
    }
  }


  /**
   * Returns true if {@code facetRuntime} is an App Engine Standard runtime and false otherwise.
   *
   * @param facetRuntime the facet runtime; runtime should not be null
   * @return true if <code>facetRuntime</code> is an App Engine Standard runtime and false otherwise
   */
  public static boolean isAppEngineStandardRuntime(IRuntime facetRuntime) {
    Preconditions.checkNotNull(facetRuntime, "runtime is null");

    org.eclipse.wst.server.core.IRuntime serverRuntime =
        org.eclipse.jst.server.core.FacetUtil.getRuntime(facetRuntime);
    if (serverRuntime != null) {
      IRuntimeType runtimeType = serverRuntime.getRuntimeType();
      if (runtimeType == null) {
        return false;
      }
      return DEFAULT_RUNTIME_ID.equals(runtimeType.getId());
    } else {
      return false;
    }
  }

  /**
   * Returns true if {@code serverRuntime} is an App Engine Standard runtime and false otherwise.
   *
   * @param serverRuntime the server runtime, runtime should not be null
   * @return true if <code>serverRuntime</code> is an App Engine Standard runtime and false
   *         otherwise
   */
  public static boolean isAppEngineStandardRuntime(
      org.eclipse.wst.server.core.IRuntime serverRuntime) {
    Preconditions.checkNotNull(serverRuntime, "runtime is null");
    IRuntimeType runtimeType = serverRuntime.getRuntimeType();
    if (runtimeType == null) {
      return false;
    }
    return DEFAULT_RUNTIME_ID.equals(runtimeType.getId());
  }

  /**
   * Checks to see if <code>facetedProject</code> has the App Engine standard facet.
   * If not, it installs the App Engine standard facet.
   *
   * @param facetedProject the faceted project receiving the App Engine facet
   * @param installDependentFacets true if the facets required by the App Engine facet should be
   *        installed, false otherwise
   * @param monitor the progress monitor
   * @throws CoreException if anything goes wrong during install
   */
  public static void installAppEngineFacet(final IFacetedProject facetedProject,
      final boolean installDependentFacets, final IProgressMonitor monitor) throws CoreException {
    ILock lock = acquireLock(facetedProject.getProject());
    try {
      SubMonitor subMonitor = SubMonitor.convert(monitor, 100);

      if (facetedProject.hasProjectFacet(FACET)) {
        // nothing to do, move along
        return;
      }

      /*
       * So the project does not have the App Engine Standard facet. We first try our AES JRE7 and
       * JRE8 detectors: these detectors look for appengine-web.xml and either add or change the
       * required facets to correspond to the AES runtime in the appengine-web.xml. The JRE7
       * detector may downgrade the Java and Dynamic Web Project facets (if set).
       * 
       * If no appengine-web.xml is detected, then there should be no changes to the IFPWC, unless
       * there are other detectors that make a contribution.
       */
      String projectName = facetedProject.getProject().getName();
      logger.fine(projectName + ": current facets: " + facetedProject.getProjectFacets());
      IFacetedProjectWorkingCopy workingCopy = facetedProject.createWorkingCopy();
      workingCopy.detect(subMonitor.split(20));

      logger.fine(projectName + ": detector changes: " + workingCopy.getProjectFacetActions());

      // If there is a conflict, send the CoreException up
      // If successful, workingCopy will mirror the IFacetedProject.
      // The only known potential for conflict was downgrading DWP from 3.x -> 2.5 for
      // AES JRE7 which we've side-stepped by allowing this version change.
      workingCopy.commitChanges(subMonitor.split(20));

      if (facetedProject.hasProjectFacet(FACET)) {
        // success!
        return;
      }

      // So we must have a project with no appengine-web.xml.

      /*
       * https://github.com/GoogleCloudPlatform/google-cloud-eclipse/issues/1155
       * 
       * Instead of calling "IFacetedProject.installProjectFacet()" multiple times, we install
       * facets in a batch using "IFacetedProject.modify()" so that we hold the lock until we finish
       * installing all the facets. This ensures that the first ConvertJob starts installing the
       * JSDT facet only after the batch is complete, which in turn prevents the first ConvertJob
       * from scheduling the second ConvertJob (triggered by installing the JSDT facet.)
       * 
       * we continue to update workingCopy to use FacetUtil.getHighestSatisfyingVersion() FIXME:
       * investigate using IFacetProjectWorkingCopy instead
       */
      FacetUtil facetUtil = new FacetUtil(facetedProject);
      IProjectFacetVersion standardFacet;
      // See if the default AppEngine Standard facet is ok with the project's current settings
      if (!FacetUtil.conflictsWith(workingCopy, FACET.getDefaultVersion())) {
        standardFacet = FACET.getDefaultVersion();
        logger.fine(projectName + ": installing default AES facet " + standardFacet);
        facetUtil.addFacetToBatch(standardFacet, null);
        workingCopy.addProjectFacet(standardFacet);
      } else {
        // first see if there's a AES version that works with our current constraints
        standardFacet = FacetUtil.getHighestSatisfyingVersion(workingCopy, FACET);
        if (standardFacet == null && installDependentFacets) {
          // if we're not installing/updating dependent facets, see if there's something
          // that works without Java and DWP
          standardFacet = FacetUtil.getHighestSatisfyingVersion(workingCopy, FACET,
              Arrays.asList(JavaFacet.FACET));
        }
        if (standardFacet == null) {
          throw new CoreException(StatusUtil.error(AppEngineStandardFacet.class,
              "No compatible AppEngine Standard facet found"));
        }
        logger.fine(projectName + ": installing AES facet " + standardFacet);
        facetUtil.addFacetToBatch(standardFacet, /* config */ null);
        workingCopy.addProjectFacet(standardFacet);
      }

      if (installDependentFacets) {
        // check if we need to add/update Java facet
        IProjectFacetVersion currentJavaFacet = workingCopy.getProjectFacetVersion(JavaFacet.FACET);
        if (currentJavaFacet == null || standardFacet.conflictsWith(currentJavaFacet)) {
          IProjectFacetVersion javaFacet =
              FacetUtil.getHighestSatisfyingVersion(workingCopy, JavaFacet.FACET);
          facetUtil.addJavaFacetToBatch(javaFacet);
          if (currentJavaFacet == null) {
            logger.fine(projectName + ": adding Java facet " + javaFacet);
            workingCopy.addProjectFacet(javaFacet);
          } else {
            logger.fine(projectName + ": updating Java facet to " + javaFacet);
            workingCopy.changeProjectFacetVersion(javaFacet);
          }
        }

        // check if we need to add/update DWP facet
        IProjectFacetVersion currentWebFacet =
            workingCopy.getProjectFacetVersion(WebFacetUtils.WEB_FACET);
        if (currentWebFacet == null || standardFacet.conflictsWith(currentWebFacet)) {
          IProjectFacetVersion webFacet =
              FacetUtil.getHighestSatisfyingVersion(workingCopy, WebFacetUtils.WEB_FACET);
          facetUtil.addWebFacetToBatch(webFacet);
          if (currentWebFacet == null) {
            logger.fine(projectName + ": adding DWP facet " + webFacet);
            workingCopy.addProjectFacet(webFacet);
          } else {
            logger.fine(projectName + ": updating DWP facet to " + webFacet);
            workingCopy.changeProjectFacetVersion(webFacet);
          }
        }
      }

      facetUtil.install(subMonitor.split(90));
    } finally {
      lock.release();
    }
  }

  /**
   * If App Engine runtimes exist in the workspace, add them to the list of targeted runtimes
   * of <code>project</code>. Otherwise create a new App Engine runtime and add it to the list
   * of targeted runtimes.
   *
   * @param project the faceted project receiving the App Engine runtime(s)
   * @param monitor the progress monitor
   * @throws CoreException if the project contains one or more facets that are not supported by
   *     this runtime; if failed for any other reason
   */
  public static void installAllAppEngineRuntimes(IFacetedProjectBase project,
      IProgressMonitor monitor) throws CoreException {
    ILock lock = acquireLock(project.getProject());
    try {
      if (!project.getProject().isOpen()) {
        // no sense trying to install into a deleted/closed project
        return;
      }
      SubMonitor progress = SubMonitor.convert(monitor, 100);
      // If the project already has an App Engine runtime instance
      // do not add any other App Engine runtime instances to the list of targeted runtimes
      for (IRuntime existingTargetedRuntime : project.getTargetedRuntimes()) {
        if (AppEngineStandardFacet.isAppEngineStandardRuntime(existingTargetedRuntime)) {
          return;
        }
      }


      // Workaround deadlock bug described in Eclipse bug (https://bugs.eclipse.org/511793).
      // There are graph update jobs triggered by the completion of the CreateProjectOperation
      // above (from resource notifications) and from other resource changes from modifying the
      // project facets. So we force the dependency graph to defer updates.
      try {
        IDependencyGraph.INSTANCE.preUpdate();
        try {
          Job.getJobManager().join(DependencyGraphImpl.GRAPH_UPDATE_JOB_FAMILY,
              progress.split(10));
        } catch (OperationCanceledException | InterruptedException ex) {
          logger.log(Level.WARNING, "Exception waiting for WTP Graph Update job", ex);
        }

        org.eclipse.wst.server.core.IRuntime[] appEngineRuntimes = getAppEngineRuntimes();
        if (appEngineRuntimes.length > 0) {
          IRuntime appEngineFacetRuntime = null;
          progress.setWorkRemaining(appEngineRuntimes.length);
          for (org.eclipse.wst.server.core.IRuntime appEngineRuntime : appEngineRuntimes) {
            appEngineFacetRuntime =
                org.eclipse.jst.server.core.FacetUtil.getRuntime(appEngineRuntime);
            if (project instanceof IFacetedProject) {
              ((IFacetedProject) project).addTargetedRuntime(appEngineFacetRuntime,
                  progress.split(1));
            } else {
              ((IFacetedProjectWorkingCopy) project).addTargetedRuntime(appEngineFacetRuntime);
            }
          }
          if (project instanceof IFacetedProject) {
            ((IFacetedProject) project).setPrimaryRuntime(appEngineFacetRuntime,
                progress.split(1));
          } else {
            ((IFacetedProjectWorkingCopy) project).setPrimaryRuntime(appEngineFacetRuntime);
          }
        } else { // Create a new App Engine runtime
          IRuntime appEngineFacetRuntime = createAppEngineFacetRuntime(progress.split(10));
          if (appEngineFacetRuntime == null) {
            throw new NullPointerException("Could not locate App Engine facet runtime");
          }

          if (project instanceof IFacetedProject) {
            ((IFacetedProject) project).addTargetedRuntime(appEngineFacetRuntime,
                progress.split(10));
            ((IFacetedProject) project).setPrimaryRuntime(appEngineFacetRuntime,
                progress.split(10));
          } else {
            ((IFacetedProjectWorkingCopy) project).addTargetedRuntime(appEngineFacetRuntime);
            ((IFacetedProjectWorkingCopy) project).setPrimaryRuntime(appEngineFacetRuntime);
          }
        }
      } catch (CoreException ex) {
        logger.log(Level.SEVERE, "Exception occurred when installing App Engine Runtime", ex);
      } finally {
        IDependencyGraph.INSTANCE.postUpdate();
      }
    } finally {
      lock.release();
    }
  }

  public static org.eclipse.wst.server.core.IRuntime createAppEngineServerRuntime(
      IProgressMonitor monitor) throws CoreException {
    IRuntimeType appEngineRuntimeType =
        ServerCore.findRuntimeType(AppEngineStandardFacet.DEFAULT_RUNTIME_ID);
    if (appEngineRuntimeType == null) {
      logger.warning("RuntimeTypes: " + Joiner.on(",").join(ServerCore.getRuntimeTypes()));
      throw new NullPointerException(
          "Could not find " + AppEngineStandardFacet.DEFAULT_RUNTIME_NAME + " runtime type");
    }

    IRuntimeWorkingCopy appEngineRuntimeWorkingCopy
        = appEngineRuntimeType.createRuntime(null /* id */, monitor);
    return appEngineRuntimeWorkingCopy.save(true, monitor);
  }

  public static IRuntime createAppEngineFacetRuntime(IProgressMonitor monitor)
      throws CoreException {
    org.eclipse.wst.server.core.IRuntime appEngineServerRuntime =
        createAppEngineServerRuntime(monitor);
    return org.eclipse.jst.server.core.FacetUtil.getRuntime(appEngineServerRuntime);
  }

  private static org.eclipse.wst.server.core.IRuntime[] getAppEngineRuntimes() {
    org.eclipse.wst.server.core.IRuntime[] allRuntimes = ServerCore.getRuntimes();
    List<org.eclipse.wst.server.core.IRuntime> appEngineRuntimes = new ArrayList<>();

    for (int i = 0; i < allRuntimes.length; i++) {
      if (isAppEngineStandardRuntime(allRuntimes[i])) {
        appEngineRuntimes.add(allRuntimes[i]);
      }
    }

    org.eclipse.wst.server.core.IRuntime[] appEngineRuntimesArray =
        new org.eclipse.wst.server.core.IRuntime[appEngineRuntimes.size()];
    return appEngineRuntimes.toArray(appEngineRuntimesArray);
  }
}
