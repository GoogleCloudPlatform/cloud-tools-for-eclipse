package com.google.cloud.tools.eclipse.appengine.deploy;

import static org.mockito.Mockito.mock;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.junit.Test;

import com.google.cloud.tools.eclipse.appengine.deploy.standard.ProjectToStagingExporter;

public class ProjectToStagingExporterTest {

  @Test(expected = NullPointerException.class)
  public void testWriteProjectToStageDir_nullProject() throws CoreException {
    new ProjectToStagingExporter().writeProjectToStageDir(null, null);
  }

  @Test(expected = NullPointerException.class)
  public void testWriteProjectToStageDir_nullStagingDir() throws CoreException {
    new ProjectToStagingExporter().writeProjectToStageDir(mock(IProject.class), null);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void testWriteProjectToStageDir_emptyStagingDir() throws CoreException {
    new ProjectToStagingExporter().writeProjectToStageDir(mock(IProject.class), "");
  }
}
