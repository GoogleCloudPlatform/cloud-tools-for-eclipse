package com.google.cloud.tools.eclipse.appengine.libraries;

import com.google.cloud.tools.eclipse.appengine.libraries.persistence.LibraryClasspathContainerSerializer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

/**
 * Job to fill in the source attachment path attribute of an {@link IClasspathEntry}.
 * <p>
 * The {@link IPath} referencing the source artifact is provided by a {@link Callable} object. The
 * job will create a new {@link IClasspathEntry} by copying the original and adding the source
 * attachment path. The {@link LibraryClasspathContainer} associated with the container path will
 * also be replaced with a copy that is identical to the original except for the updated
 * {@link IClasspathEntry}s.
 * <p>
 * If the source resolution or setting the source attachment attribute fails, the job will still
 * return {@link Status#OK_STATUS} as this is not considered an error that the user should be
 * notified of.
 */
public class SourceAttacherJob extends Job {

  private static final Logger logger = Logger.getLogger(SourceAttacherJob.class.getName());

  private final IJavaProject javaProject;
  private final IPath containerPath;
  private final IPath libraryPath;
  private final Callable<IPath> sourceArtifactPathProvider;
  private final LibraryClasspathContainerSerializer serializer;

  public SourceAttacherJob(IJavaProject javaProject, IPath containerPath, IPath libraryPath,
                    Callable<IPath> sourceArtifactPathProvider) {
    super(Messages.getString("SourceAttachmentDownloaderJobName",
                             javaProject.getProject().getName()));
    this.javaProject = javaProject;
    this.containerPath = containerPath;
    this.libraryPath = libraryPath;
    this.sourceArtifactPathProvider = sourceArtifactPathProvider;
    serializer = new LibraryClasspathContainerSerializer();
    setRule(javaProject.getSchedulingRule());
  }

  @Override
  protected IStatus run(IProgressMonitor monitor) {
    try {
      IClasspathContainer container = JavaCore.getClasspathContainer(containerPath, javaProject);
      if (container instanceof LibraryClasspathContainer) {
        logger.log(Level.FINE, Messages.getString("ContainerClassUnexpected",
            container.getClass().getName(), LibraryClasspathContainer.class.getName()));
        return Status.OK_STATUS;
      };

      LibraryClasspathContainer libraryClasspathContainer = (LibraryClasspathContainer) container;
      IPath sourceArtifactPath = sourceArtifactPathProvider.call();
      List<IClasspathEntry> newClasspathEntries = new ArrayList<>();

      for (IClasspathEntry entry : libraryClasspathContainer.getClasspathEntries()) {
        if (!entry.getPath().equals(libraryPath)) {
          newClasspathEntries.add(entry);
        } else {
          newClasspathEntries.add(JavaCore.newLibraryEntry(
              entry.getPath(), sourceArtifactPath, null /* sourceAttachmentRootPath */,
              entry.getAccessRules(), entry.getExtraAttributes(), entry.isExported()));
        }
      }

      LibraryClasspathContainer newContainer =
          libraryClasspathContainer.copyWithNewEntries(newClasspathEntries);
      JavaCore.setClasspathContainer(containerPath, new IJavaProject[]{ javaProject },
                                     new IClasspathContainer[]{ newContainer }, monitor);
      serializer.saveContainer(javaProject, newContainer);
      return Status.OK_STATUS;

    } catch (Exception ex) {
      // it's not needed to be logged normally
      logger.log(Level.FINE, Messages.getString("SourceAttachmentFailed"), ex);
      return Status.OK_STATUS;  // even if it fails, we should not display an error to the user
    }
  }
}