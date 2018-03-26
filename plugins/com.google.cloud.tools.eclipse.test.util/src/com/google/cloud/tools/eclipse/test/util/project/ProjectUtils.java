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

package com.google.cloud.tools.eclipse.test.util.project;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.cloud.tools.eclipse.test.util.ArrayAssertions;
import com.google.cloud.tools.eclipse.test.util.ThreadDumpingWatchdog;
import com.google.cloud.tools.eclipse.test.util.ZipUtil;
import com.google.cloud.tools.eclipse.test.util.reflection.ReflectionUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.eclipse.wst.validation.internal.operations.ValidationBuilder;
import org.eclipse.wst.validation.internal.operations.ValidatorManager;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * A set of utility methods for dealing with projects.
 */
public class ProjectUtils {
  private static boolean DEBUG = false;

  /**
   * Import the Eclipse projects found within the bundle containing {@code clazz} at the
   * {@code relativeLocation}. Return the list of projects imported.
   *
   * @throws IOException if the zip cannot be accessed
   * @throws CoreException if a project cannot be imported
   */
  public static List<IProject> importProjects(Class<?> clazz, String relativeLocation,
      boolean checkBuildErrors, IProgressMonitor monitor)
      throws IOException, CoreException {

    // Resolve the zip from within this bundle
    Bundle bundle = FrameworkUtil.getBundle(clazz);
    URL bundleLocation = bundle.getResource(relativeLocation);
    assertNotNull(bundleLocation);
    return importProjects(bundleLocation, checkBuildErrors, monitor);
  }

  /**
   * Import the Eclipse projects found within the bundle containing {@code clazz} at the
   * {@code relativeLocation}. Return the list of projects imported.
   *
   * @throws IOException if the zip cannot be accessed
   * @throws CoreException if a project cannot be imported
   */
  public static List<IProject> importProjects(URL fileLocation,
      boolean checkBuildErrors, IProgressMonitor monitor) throws IOException, CoreException {
    SubMonitor progress = SubMonitor.convert(monitor, 100);
    URL zipLocation = FileLocator.toFileURL(fileLocation);
    if (!zipLocation.getProtocol().equals("file")) {
      throw new IOException("could not resolve location to a file");
    }
    File zippedFile = new File(zipLocation.getPath());
    assertTrue(zippedFile.exists());
    progress.worked(5);

    IWorkspaceRoot root = getWorkspace().getRoot();
    // extract projects into our workspace using WTP internal utility class
    // assumes projects are contained in subdirectories within the zip
    IStatus status = ZipUtil.unzip(zippedFile, root.getLocation().toFile(), progress.split(10));
    assertTrue("failed to extract: " + status, status.isOK());

    List<IPath> projectFiles = new ArrayList<>();
    try (ZipFile zip = new ZipFile(zippedFile)) {
      for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
        ZipEntry entry = it.nextElement();
        if (entry.getName().endsWith("/.project")) {
          IPath projectFileLocation = root.getLocation().append(new Path(entry.getName()));
          projectFiles.add(projectFileLocation);
        }
      }
      progress.worked(5);
    }

    // import the projects
    progress.setWorkRemaining(10 * projectFiles.size() + 10);
    List<IProject> projects = new ArrayList<>(projectFiles.size());
    System.out.printf("Importing %d projects:\n", projectFiles.size());
    for (IPath projectFile : projectFiles) {
      System.out.println("    " + projectFile);
      IProjectDescription descriptor = root.getWorkspace().loadProjectDescription(projectFile);
      IProject project = root.getProject(descriptor.getName());
      // bring in the project to the workspace
      project.create(descriptor, progress.split(2));
      project.open(progress.split(8));
      projects.add(project);
    }

    // wait for any post-import operations too
    waitForProjects(projects);
    if (checkBuildErrors) {
      waitUntilNoBuildErrors();
      // changed from specific projects to see all possible errors
      failIfBuildErrors("Imported projects have errors");
    }

    return projects;
  }

  /**
   * Waits until no error markers are found in the workspace. Returns true unless it times out
   * after 300 seconds. Returns false if it times out.
   * 
   * This method does not wait for pending build and validation jobs, so it is usually required to
   * call methods such as {@link #waitForProjects} beforehand.
   */
  public static boolean waitUntilNoBuildErrors() throws CoreException {
    IProject[] projects = getWorkspace().getRoot().getProjects();
    return waitUntilNoBuildErrors(projects);
  }

  /**
   * Waits until no error markers are found for the projects. Returns true unless it times out after
   * 300 seconds. Returns false if it times out.
   * 
   * This method does not wait for pending build and validation jobs, so it is usually required to
   * call methods such as {@link #waitForProjects} beforehand.
   */
  public static boolean waitUntilNoBuildErrors(IProject... projects) throws CoreException {
    try {
      Stopwatch elapsed = Stopwatch.createStarted();
      while (true) {
        Set<String> errors = getAllBuildErrors(projects);
        if (errors.isEmpty()) {
          return true;
        } else if (elapsed.elapsed(TimeUnit.SECONDS) > 300) {
          return false;
        }

        if (Display.getCurrent() != null) {
          while (Display.getCurrent().readAndDispatch());
        }
        Thread.sleep(50);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /** Fail if there are any build errors on any project in the workspace. */
  public static void failIfBuildErrors() throws CoreException {
    failIfBuildErrors("Projects have build errors", getWorkspace().getRoot().getProjects());
  }

  /** Fail if there are any build errors on the specified projects. */
  public static void failIfBuildErrors(String message, Collection<IProject> projects)
      throws CoreException {
    failIfBuildErrors(message, projects.toArray(new IProject[projects.size()]));
  }

  /** Fail if there are any build errors on the specified projects. */
  public static void failIfBuildErrors(String message, IProject... projects) throws CoreException {
    Set<String> errors = getAllBuildErrors(projects);
    assertTrue(message + "\n" + Joiner.on("\n").join(errors), errors.isEmpty());
  }

  /** Return a list of all build errors on the specified projects. */
  public static Set<String> getAllBuildErrors(IProject... projects) throws CoreException {
    Set<String> errors = new LinkedHashSet<>();
    for (IProject project : projects) {
      IMarker[] problems = project.findMarkers(IMarker.PROBLEM, true /* includeSubtypes */,
          IResource.DEPTH_INFINITE);
      for (IMarker problem : problems) {
        int severity = problem.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
        if (severity >= IMarker.SEVERITY_ERROR) {
          errors.add(formatProblem(problem));
        }
      }
    }
    return errors;
  }

  public static String formatProblem(IMarker problem) {
    StringBuilder sb = new StringBuilder();
    sb.append(problem.getResource().getFullPath());
    sb.append(':');
    sb.append(problem.getAttribute(IMarker.LINE_NUMBER, -1));
    sb.append(": ");
    sb.append(problem.getAttribute(IMarker.MESSAGE, ""));

    // Derived from
    // org.eclipse.wst.xml.ui.internal.validation.core.errorinfo.ReferencedFileErrorsHandler
    // ValidationMessage.ERROR_MESSAGE_MAP_QUALIFIED_NAME
    QualifiedName errorMessageMapName =
        new QualifiedName("org.eclipse.wst.xml.validation", "errorMessageMap");
    // map is list of file-URI -> ValidationMessage objects
    try {
      Map<?, ?> map = (Map<?, ?>) problem.getResource().getSessionProperty(errorMessageMapName);
      if (map != null) {
        for (Entry<?, ?> entry : map.entrySet()) {
          // show == false as the top message object is in the marker message
          appendValidationMessage(sb, entry.getValue(), 0, /* show */ false);
        }
      }
    } catch (CoreException ex) {
      /* ignore: not a validation problem */
    }
    return sb.toString();
  }

  /**
   * Format a validation message error
   */
  private static void appendValidationMessage(StringBuilder sb, Object message, int indent,
      boolean show) {
    if (message == null || !message.getClass().getName().endsWith("ValidationMessage")) {
      return;
    }
    try {
      if (show) {
        String uri = ReflectionUtil.getField(message, "uri", String.class);
        String text = ReflectionUtil.getField(message, "message", String.class);
        int lineNumber = ReflectionUtil.getField(message, "lineNumber", Integer.class);
        int columnNumber = ReflectionUtil.getField(message, "columnNumber", Integer.class);
        sb.append("\n");
        for (int i = 0; i < indent; i++) {
          sb.append("  ");
        }
        sb.append(uri).append("[").append(lineNumber).append(":").append(columnNumber).append("]: ")
            .append(text);
      }
      // getNestedMessages() is never null
      List<?> nested = ReflectionUtil.invoke(message, "getNestedMessages", List.class);
      for (Object subMessage : nested) {
        appendValidationMessage(sb, subMessage, indent + 1, true);
      }
    } catch (Exception ex) {
      /* ignore: this is just a helper */
    }
  }

  public static void waitForProjects(Collection<IProject> projects) {
    waitForProjects(projects.toArray(new IProject[0]));
  }

  /** Wait for any spawned jobs and builds to complete (e.g., validation jobs). */
  public static void waitForProjects(IProject... projects) {
    Runnable delayTactic = () -> {
      Display display = Display.getCurrent();
      if (display != null) {
        while (display.readAndDispatch()) {
          /* spin */
        }
      }
      Thread.yield();
    };
    waitForProjects(delayTactic, projects);
  }

  /** Wait for any spawned jobs and builds to complete (e.g., validation jobs). */
  public static void waitForProjects(Runnable delayTactic, IProject... projects) {
    if (projects.length == 0) {
      projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
    }
    Stopwatch timer = Stopwatch.createStarted();
    try {
      Collection<Job> jobs = Collections.emptyList();
      Set<String> previousBuildErrors = Collections.emptySet();
      boolean buildErrorsChanging;
      do {
        // wait a little bit to give the builders a chance
        delayTactic.run();

        // wait for any previously-identified build jobs
        for (Job job : jobs) {
          job.join();
        }

        // identify any pending build-related jobs
        jobs = findPendingBuildJobs(projects);

        // track whether we've reached a fixpoint in the build errors
        Set<String> currentBuildErrors = getAllBuildErrors(projects);
        buildErrorsChanging = !previousBuildErrors.equals(currentBuildErrors);
        previousBuildErrors = currentBuildErrors;

        if (DEBUG || timer.elapsed(TimeUnit.SECONDS) > 10) {
          if (!jobs.isEmpty()) {
            System.err.printf("ProjectUtils#waitForProjects[%s]: waiting for %d jobs: %s\n", timer,
                jobs.size(), jobs);
          }
          if (buildErrorsChanging) {
            System.err.printf("ProjectUtils#waitForProjects[%s]: waiting for %d build errors\n",
                timer, currentBuildErrors.size());
          }
          // Uncomment if tests are failing to identify any other build-related jobs.
          // Job[] otherJobs = Job.getJobManager().find(null);
          // if (otherJobs.length > 0) {
          // System.err.printf("Ignoring %d unrelated jobs:\n", otherJobs.length);
          // for (Job job : otherJobs) {
          // System.err.printf(" %s: %s\n", job.getClass().getName(), job);
          // }
          // }
        }
      } while (!jobs.isEmpty() || buildErrorsChanging);
    } catch (CoreException | InterruptedException ex) {
      throw new RuntimeException(ex);
    }
  }

  /** Identify all jobs that we know of that are related to building. */
  private static Collection<Job> findPendingBuildJobs(IProject... projects) {
    Set<Job> jobs = new HashSet<>();
    IJobManager jobManager = Job.getJobManager();
    Collections.addAll(jobs, jobManager.find(ResourcesPlugin.FAMILY_MANUAL_BUILD));
    Collections.addAll(jobs, jobManager.find(ResourcesPlugin.FAMILY_AUTO_BUILD));
    // J2EEElementChangedListener.PROJECT_COMPONENT_UPDATE_JOB_FAMILY
    Collections.addAll(jobs, jobManager.find("org.eclipse.jst.j2ee.refactor.component"));
    // ServerPlugin.SHUTDOWN_JOB_FAMILY
    Collections.addAll(jobs, jobManager.find("org.eclipse.wst.server.core.family"));
    Collections.addAll(jobs, jobManager.find("org.eclipse.wst.server.ui.family"));
    Collections.addAll(jobs, jobManager.find(ValidationBuilder.FAMILY_VALIDATION_JOB));
    for (IProject project : projects) {
      Collections.addAll(jobs,
          jobManager.find(project.getName() + ValidatorManager.VALIDATOR_JOB_FAMILY));
    }
    // some jobs are not part of a family
    for (Job job : jobManager.find(null)) {
      switch (job.getClass().getName()) {
        case "org.eclipse.wst.jsdt.web.core.internal.project.ConvertJob":
        case "org.eclipse.m2e.core.ui.internal.wizards.ImportMavenProjectsJob":
        case "org.eclipse.m2e.core.internal.project.registry.ProjectRegistryRefreshJob":
          jobs.add(job);
          break;
        default:
          break;
      }
    }
    return jobs;
  }

  private static IWorkspace getWorkspace() {
    return ResourcesPlugin.getWorkspace();
  }


  /**
   * Wait until the project has finished building and markers have disappeared.
   * 
   * @throws AssertionError if markers are still present
   */
  public static IMarker[] waitUntilNoMarkersFound(IResource resource, String markerType,
      boolean includeSubtypes, int depth) throws CoreException {
    Stopwatch elapsed = Stopwatch.createStarted();
    IMarker[] markers;
    do {
      ProjectUtils.waitForProjects(resource.getProject());
      markers = resource.findMarkers(markerType, includeSubtypes, depth);
      if (markers.length > 0) {
        System.err.printf("[WARNING][TESTS] %s: %d markers found of type %s\n", elapsed,
            markers.length, markerType);
        ThreadDumpingWatchdog.report("Expected no markers of type " + markerType, elapsed);
      }
    } while (elapsed.elapsed(TimeUnit.SECONDS) < 300 && markers.length > 0);

    ArrayAssertions.assertIsEmpty(markers, ProjectUtils::formatProblem);
    return markers;
  }

  /**
   * Wait until the project has finished building and markers have been registered.
   * 
   * @throws AssertionError if there are no markers by the timeout
   */
  public static IMarker[] waitUntilMarkersFound(IResource resource, String markerType,
      boolean includeSubtypes, int depth) throws CoreException {
    Stopwatch elapsed = Stopwatch.createStarted();
    IMarker[] markers;
    do {
      ProjectUtils.waitForProjects(resource.getProject());
      markers = resource.findMarkers(markerType, includeSubtypes, depth);
      if (markers.length == 0) {
        System.err.printf("[WARNING][TESTS] %s: no markers found of type %s\n", elapsed,
            markerType);
        ThreadDumpingWatchdog.report("Expected to find markers of type " + markerType, elapsed);
      }
    } while (elapsed.elapsed(TimeUnit.SECONDS) < 300 && markers.length == 0);
    assertTrue("No markers found", markers.length > 0);
    return markers;
  }

  private ProjectUtils() {}
}
