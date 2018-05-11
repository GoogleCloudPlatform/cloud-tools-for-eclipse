
package com.google.cloud.tools.eclipse.appengine.facets.ui.navigator;

import com.google.cloud.tools.appengine.AppEngineDescriptor;
import com.google.cloud.tools.appengine.api.AppEngineException;
import com.google.cloud.tools.eclipse.appengine.facets.AppEngineStandardFacet;
import com.google.cloud.tools.eclipse.appengine.facets.WebProjectUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;
import org.xml.sax.SAXException;

public class AppEngineContentProvider implements ITreeContentProvider {
  private static final Object[] EMPTY_ARRAY = new Object[0];
  private static final Logger logger = Logger.getLogger(AppEngineContentProvider.class.getName());

  @Override
  public Object[] getElements(Object inputElement) {
    return getChildren(inputElement);
  }

  @Override
  public boolean hasChildren(Object element) {
    return element instanceof IProject || element instanceof AppEngineWebDescriptor;
  }

  @Override
  public Object[] getChildren(Object parentElement) {
    if (parentElement instanceof IProject) {
      IFacetedProject project = getProject(parentElement);
      if (project != null && AppEngineStandardFacet.hasFacet(project)) {
        IFile appEngineWebDescriptorFile =
            WebProjectUtil.findInWebInf(project.getProject(), new Path("appengine-web.xml"));
        if (appEngineWebDescriptorFile != null && appEngineWebDescriptorFile.exists()) {
          try (InputStream input = appEngineWebDescriptorFile.getContents()) {
            AppEngineDescriptor descriptor = AppEngineDescriptor.parse(input);
            return new Object[] {
                new AppEngineWebDescriptor(project, appEngineWebDescriptorFile, descriptor)};
          } catch (CoreException | SAXException | IOException ex) {
            IPath path = appEngineWebDescriptorFile.getFullPath();
            logger.log(Level.WARNING, "Unable to parse " + path, ex);
          }
        }
      }
    } else if (parentElement instanceof AppEngineWebDescriptor) {
      List<Object> contents = new ArrayList<>();
      AppEngineWebDescriptor webElement = (AppEngineWebDescriptor) parentElement;
      try {
        AppEngineDescriptor descriptor = webElement.getDescriptor();
        if (descriptor.getServiceId() == null || "default".equals(descriptor.getServiceId())) {
          addCron(webElement.getProject(), contents);
          addDatastoreIndexes(webElement.getProject(), contents);
          addTaskQueues(webElement.getProject(), contents);
          addDenialOfService(webElement.getProject(), contents);
          addDispatch(webElement.getProject(), contents);
        }
        return contents.toArray();
      } catch (AppEngineException ex) {
        IPath path = webElement.getFile().getFullPath();
        logger.log(Level.WARNING, "Unable to parse " + path, ex);
      }
    }
    return EMPTY_ARRAY;
  }

  /** Add a {@code cron.xml} element if found */
  private void addCron(IFacetedProject project, List<Object> contents) {
    IFile cronXml = WebProjectUtil.findInWebInf(project.getProject(), new Path("cron.xml"));
    if (cronXml != null && cronXml.exists()) {
      contents.add(new AppEngineCronDescriptor(project, cronXml));
    }
  }

  /** Add a {@code datastore-indexes.xml} element if found */
  private void addDatastoreIndexes(IFacetedProject project, List<Object> contents) {
    IFile datastoreIndexes =
        WebProjectUtil.findInWebInf(project.getProject(), new Path("datastore-indexes.xml"));
    if (datastoreIndexes != null && datastoreIndexes.exists()) {
      contents.add(new AppEngineDatastoreIndexes(project, datastoreIndexes));
    }
  }

  /** Add a {@code dispatch.xml} element if found. */
  private void addDispatch(IFacetedProject project, List<Object> contents) {
    IFile dispatchXml = WebProjectUtil.findInWebInf(project.getProject(), new Path("dispatch.xml"));
    if (dispatchXml != null && dispatchXml.exists()) {
      contents.add(new AppEngineDispatchDescriptor(project, dispatchXml));
    }
  }

  /**
   * Add a {@code dos.xml} element if found.
   */
  private void addDenialOfService(IFacetedProject project, List<Object> contents) {
    IFile dosXml = WebProjectUtil.findInWebInf(project.getProject(), new Path("dos.xml"));
    if (dosXml != null && dosXml.exists()) {
      contents.add(new AppEngineDenialOfServiceDescriptor(project, dosXml));
    }
  }

  /** Add a {@code queue.xml} element if found */
  private void addTaskQueues(IFacetedProject project, List<Object> contents) {
    IFile queueXml = WebProjectUtil.findInWebInf(project.getProject(), new Path("queue.xml"));
    if (queueXml != null && queueXml.exists()) {
      contents.add(new AppEngineTaskQueuesDescriptor(project, queueXml));
    }
  }


  @Override
  public Object getParent(Object element) {
    return null;
  }

  /** Try to get a project from the given element, return {@code null} otherwise. */
  static IFacetedProject getProject(Object inputElement) {
    try {
      if (inputElement instanceof IFacetedProject) {
        return (IFacetedProject) inputElement;
      } else if (inputElement instanceof IProject) {
        return ProjectFacetsManager.create((IProject) inputElement);
      } else if (inputElement instanceof IResource) {
        return ProjectFacetsManager.create(((IResource) inputElement).getProject());
      } else if (inputElement instanceof IAdaptable) {
        IFacetedProject facetedProject =
            ((IAdaptable) inputElement).getAdapter(IFacetedProject.class);
        if (facetedProject != null) {
          return facetedProject;
        }
        IProject project = ((IAdaptable) inputElement).getAdapter(IProject.class);
        if (project != null) {
          return ProjectFacetsManager.create(project);
        }
        IResource resource = ((IAdaptable) inputElement).getAdapter(IResource.class);
        if (resource != null) {
          return ProjectFacetsManager.create(((IResource) inputElement).getProject());
        }
      }
    } catch (CoreException ex) {
      logger.log(Level.INFO, "Unable to obtain project", ex);
    }
    return null;
  }
}
