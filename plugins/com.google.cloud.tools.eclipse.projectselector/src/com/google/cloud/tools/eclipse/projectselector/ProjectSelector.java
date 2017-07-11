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

package com.google.cloud.tools.eclipse.projectselector;

import com.google.cloud.tools.eclipse.projectselector.model.GcpProject;
import com.google.cloud.tools.eclipse.ui.util.event.OpenUriSelectionListener;
import com.google.cloud.tools.eclipse.ui.util.event.OpenUriSelectionListener.ErrorDialogErrorHandler;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.eclipse.core.databinding.beans.IBeanValueProperty;
import org.eclipse.core.databinding.beans.PojoProperties;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.core.databinding.property.value.IValueProperty;
import org.eclipse.jface.databinding.viewers.ViewerSupport;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Link;

public class ProjectSelector extends Composite implements ISelectionProvider {

  private final TableViewer viewer;
  private final WritableList/* <GcpProject> */ input; // Generics supported only in Neon+
  private Link statusLink;
  private IBeanValueProperty[] projectProperties;

  public ProjectSelector(Composite parent) {
    super(parent, SWT.NONE);
    GridLayoutFactory.fillDefaults().numColumns(2).spacing(0, 0).applyTo(this);

    Composite tableComposite = new Composite(this, SWT.NONE);
    TableColumnLayout tableColumnLayout = new TableColumnLayout();
    tableComposite.setLayout(tableColumnLayout);
    GridDataFactory.fillDefaults().grab(true, true).applyTo(tableComposite);
    viewer = new TableViewer(tableComposite, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
    createColumns(tableColumnLayout);
    viewer.getTable().setHeaderVisible(true);
    viewer.getTable().setLinesVisible(false);

    input = WritableList.withElementType(GcpProject.class);
    projectProperties = PojoProperties.values(new String[] {"name", "id"}); //$NON-NLS-1$ //$NON-NLS-2$
    ViewerSupport.bind(viewer, input, projectProperties);
    viewer.setComparator(new ViewerComparator());

    Composite linkComposite = new Composite(this, SWT.NONE);
    statusLink = new Link(linkComposite, SWT.WRAP);
    statusLink.addSelectionListener(
        new OpenUriSelectionListener(new ErrorDialogErrorHandler(getShell())));
    statusLink.setText("");
    GridDataFactory.fillDefaults().span(2, 1).applyTo(linkComposite);
    GridLayoutFactory.fillDefaults().generateLayout(linkComposite);
  }

  private void createColumns(TableColumnLayout tableColumnLayout) {
    TableViewerColumn nameColumn = new TableViewerColumn(viewer, SWT.LEFT);
    nameColumn.getColumn().setWidth(200);
    nameColumn.getColumn().setText(Messages.getString("projectselector.header.name")); //$NON-NLS-1$
    tableColumnLayout.setColumnData(nameColumn.getColumn(), new ColumnWeightData(1, 200));

    TableViewerColumn idColumn = new TableViewerColumn(viewer, SWT.LEFT);
    idColumn.getColumn().setWidth(200);
    idColumn.getColumn().setText(Messages.getString("projectselector.header.id")); //$NON-NLS-1$
    tableColumnLayout.setColumnData(idColumn.getColumn(), new ColumnWeightData(1, 200));
  }

  public IStructuredSelection getSelection() {
    // getStructuredSelection() is not available in Mars
    return (IStructuredSelection) viewer.getSelection();
  }

  public int getProjectCount() {
    return input.size();
  }

  public TableViewer getViewer() {
    return viewer;
  }

  /**
   * @return the projects
   */
  @SuppressWarnings("unchecked")
  public List<GcpProject> getProjects() {
    return ImmutableList.copyOf(input);
  }

  public void setProjects(List<GcpProject> projects) {
    ISelection selection = viewer.getSelection();
    input.clear();
    clearStatusLink(); // otherwise revealing selection is off sometimes
    if (projects != null) {
      input.addAll(projects);
    }
    viewer.setSelection(selection);
  }

  /**
   * Set a search filter on the list. If empty or {@code null}, then removes any existing filters.
   */
  public void setFilter(final String searchText) {
    if (searchText == null || Strings.isNullOrEmpty(searchText)) {
      viewer.resetFilters();
      return;
    }
    final String[] searchTerms = searchText.split("\\s");
    ViewerFilter filter = new ViewerFilter() {
      @Override
      public boolean select(Viewer viewer, Object parentElement, Object element) {
        return matches(searchTerms, element, projectProperties);
      }
    };

    viewer.setFilters(new ViewerFilter[] {filter});
  }


  /**
   * @return true if the element's properties are matched by the given search terms.
   */
  @VisibleForTesting
  static boolean matches(String[] searchTerms, Object element, IValueProperty[] properties) {
    for (String searchTerm : searchTerms) {
      boolean seen = false;
      for (IValueProperty property : properties) {
        Object value = property.getValue(element);
        if (value instanceof String && ((String) value).contains(searchTerm)) {
          seen = true;
          break;
        }
      }
      if (!seen) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void setSelection(ISelection selection) {
    viewer.setSelection(selection);
  }


  public void addSelectionChangedListener(ISelectionChangedListener listener) {
    viewer.addPostSelectionChangedListener(listener);
  }

  public void removeSelectionChangedListener(ISelectionChangedListener listener) {
    viewer.removePostSelectionChangedListener(listener);
  }

  public void setStatusLink(String linkText, String tooltip) {
    statusLink.setText(linkText);
    setTooltip(tooltip);
    boolean hide = Strings.isNullOrEmpty(linkText);
    ((GridData) statusLink.getLayoutData()).exclude = hide;
    statusLink.setVisible(!hide);
    layout();
    IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
    if (!selection.isEmpty()) {
      viewer.reveal(selection.getFirstElement());
    }
  }

  private void setTooltip(String tooltip) {
    if (Strings.isNullOrEmpty(tooltip)) {
      statusLink.setToolTipText(null);
    } else {
      // & is not displayed in tooltip unless doubled
      statusLink.setToolTipText(tooltip.replace("&", "&&"));
    }
  }

  public void clearStatusLink() {
    setStatusLink("", "");
  }
}
