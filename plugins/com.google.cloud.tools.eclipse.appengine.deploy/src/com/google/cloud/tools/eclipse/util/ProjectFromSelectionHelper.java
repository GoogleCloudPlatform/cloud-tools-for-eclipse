package com.google.cloud.tools.eclipse.util;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;

public class ProjectFromSelectionHelper {

  private FacetedProjectHelper facetedProjectHelper;
  
  public ProjectFromSelectionHelper(FacetedProjectHelper facetedProjectHelper) {
    this.facetedProjectHelper = facetedProjectHelper;
  }

  public IProject getProject(ExecutionEvent event) throws CoreException, ExecutionException {
    ISelection selection = HandlerUtil.getCurrentSelectionChecked(event);
    if (selection instanceof IStructuredSelection) {
      IStructuredSelection structuredSelection = (IStructuredSelection) selection;
      if (structuredSelection.size() == 1) {
        IProject project;
        if (structuredSelection.getFirstElement() instanceof IProject) {
          project = (IProject) structuredSelection.getFirstElement();
        } else if (structuredSelection.getFirstElement() instanceof IJavaProject) {
          project = ((IJavaProject) structuredSelection.getFirstElement()).getProject();
        } else {
          return null;
        }

        IFacetedProject facetedProject = facetedProjectHelper.getFacetedProject(project);
        // TODO replace with constant from AppEngineFacet (after possibly relocating that class)
        if (facetedProjectHelper.projectHasFacet(facetedProject, "com.google.cloud.tools.eclipse.appengine.facet")) {
          return project;
        }
      }
    }
    return null;
  }
}
