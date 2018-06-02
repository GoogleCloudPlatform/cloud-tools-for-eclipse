/*
 * Copyright 2018 Google LLC
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

package com.google.cloud.tools.eclipse.appengine.facets.ui.navigator;

import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.appengine.api.AppEngineException;
import com.google.cloud.tools.eclipse.appengine.facets.AppEngineStandardFacet;
import com.google.cloud.tools.eclipse.appengine.facets.WebProjectUtil;
import com.google.cloud.tools.eclipse.appengine.facets.ui.navigator.model.AppEngineResourceElement;
import com.google.cloud.tools.eclipse.appengine.facets.ui.navigator.model.AppEngineStandardProjectElement;
import com.google.cloud.tools.eclipse.appengine.facets.ui.navigator.model.CronDescriptor;
import com.google.cloud.tools.eclipse.appengine.facets.ui.navigator.model.DatastoreIndexesDescriptor;
import com.google.cloud.tools.eclipse.appengine.facets.ui.navigator.model.DenialOfServiceDescriptor;
import com.google.cloud.tools.eclipse.appengine.facets.ui.navigator.model.DispatchRoutingDescriptor;
import com.google.cloud.tools.eclipse.appengine.facets.ui.navigator.model.TaskQueuesDescriptor;
import com.google.cloud.tools.eclipse.test.util.project.TestProjectCreator;
import com.google.common.base.Strings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.function.Consumer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jst.common.project.facet.core.JavaFacet;
import org.eclipse.jst.j2ee.web.project.facet.WebFacetUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AppEngineContentProviderTest {

  @Rule
  public TestProjectCreator projectCreator =
      new TestProjectCreator().withFacets(JavaFacet.VERSION_1_7);

  @Mock private Consumer<Collection<Object>> refresher;
  private AppEngineContentProvider fixture;

  @Before
  public void setUp() {
    fixture = new AppEngineContentProvider(refresher);
  }

  @After
  public void tearDown() {
    fixture.dispose();
  }

  @Test
  public void testGetChildren_AppEngineStandardProject() {
    projectCreator.withFacets(AppEngineStandardFacet.JRE7, WebFacetUtils.WEB_25);

    createAppEngineWebXml(null);
    Object[] children = fixture.getChildren(projectCreator.getFacetedProject());
    assertNotNull(children);
    assertEquals(1, children.length);
    assertTrue(children[0] instanceof AppEngineStandardProjectElement);
  }

  @Test
  public void testGetChildren_AppEngineStandardProjectElement_noService_noChildren()
      throws AppEngineException {
    createAppEngineWebXml(null);
    AppEngineStandardProjectElement projectElement =
        AppEngineStandardProjectElement.create(projectCreator.getProject());

    Object[] children = fixture.getChildren(projectElement);
    assertNotNull(children);
    assertEquals(0, children.length);
  }

  @Test
  public void testGetChildren_AppEngineStandardProjectElement_noDefault_cronIgnored()
      throws AppEngineException {
    createAppEngineWebXml("service"); // $NON-NLS-1$
    createEmptyCronXml();
    AppEngineStandardProjectElement projectElement =
        AppEngineStandardProjectElement.create(projectCreator.getProject());

    Object[] children = fixture.getChildren(projectElement);
    assertNotNull(children);
    assertEquals(0, children.length);
  }

  @Test
  public void testGetChildren_AppEngineStandardProjectElement_default_cron()
      throws AppEngineException {
    createAppEngineWebXml("default"); // $NON-NLS-1$
    createEmptyCronXml();
    AppEngineStandardProjectElement projectElement =
        AppEngineStandardProjectElement.create(projectCreator.getProject());

    Object[] children = fixture.getChildren(projectElement);
    assertNotNull(children);
    assertEquals(1, children.length);
    assertTrue(children[0] instanceof CronDescriptor);

    Object[] grandchildren = fixture.getChildren(children[0]);
    assertNotNull(grandchildren);
    assertEquals("should have no grandchildren", 0, grandchildren.length);
  }

  @Test
  public void testGetChildren_AppEngineStandardProjectElement_default_dispatch()
      throws AppEngineException {
    createAppEngineWebXml("default"); // $NON-NLS-1$
    createEmptyDispatchXml();
    AppEngineStandardProjectElement projectElement =
        AppEngineStandardProjectElement.create(projectCreator.getProject());

    Object[] children = fixture.getChildren(projectElement);
    assertNotNull(children);
    assertEquals(1, children.length);
    assertTrue(children[0] instanceof DispatchRoutingDescriptor);

    Object[] grandchildren = fixture.getChildren(children[0]);
    assertNotNull(grandchildren);
    assertEquals("should have no grandchildren", 0, grandchildren.length);
  }

  @Test
  public void testGetChildren_AppEngineStandardProjectElement_default_datastoreIndexes()
      throws AppEngineException {
    createAppEngineWebXml("default"); // $NON-NLS-1$
    createEmptyDatastoreIndexesXml();
    AppEngineStandardProjectElement projectElement =
        AppEngineStandardProjectElement.create(projectCreator.getProject());

    Object[] children = fixture.getChildren(projectElement);
    assertNotNull(children);
    assertEquals(1, children.length);
    assertTrue(children[0] instanceof DatastoreIndexesDescriptor);

    Object[] grandchildren = fixture.getChildren(children[0]);
    assertNotNull(grandchildren);
    assertEquals("should have no grandchildren", 0, grandchildren.length);
  }

  @Test
  public void testGetChildren_AppEngineStandardProjectElement_default_denialOfService()
      throws AppEngineException {
    createAppEngineWebXml("default"); // $NON-NLS-1$
    createEmptyDosXml();
    AppEngineStandardProjectElement projectElement =
        AppEngineStandardProjectElement.create(projectCreator.getProject());

    Object[] children = fixture.getChildren(projectElement);
    assertNotNull(children);
    assertEquals(1, children.length);
    assertTrue(children[0] instanceof DenialOfServiceDescriptor);

    Object[] grandchildren = fixture.getChildren(children[0]);
    assertNotNull(grandchildren);
    assertEquals("should have no grandchildren", 0, grandchildren.length);
  }

  @Test
  public void testGetChildren_AppEngineStandardProjectElement_default_taskQueue()
      throws AppEngineException {
    createAppEngineWebXml("default"); // $NON-NLS-1$
    createEmptyQueueXml();
    AppEngineStandardProjectElement projectElement =
        AppEngineStandardProjectElement.create(projectCreator.getProject());

    Object[] children = fixture.getChildren(projectElement);
    assertNotNull(children);
    assertEquals(1, children.length);
    assertTrue(children[0] instanceof TaskQueuesDescriptor);

    Object[] grandchildren = fixture.getChildren(children[0]);
    assertNotNull(grandchildren);
    assertEquals("should have no grandchildren", 0, grandchildren.length);
  }

  @Test
  public void testHasChildren_AppEngineStandardProject() {
    createAppEngineWebXml(null);
    assertTrue(fixture.hasChildren(projectCreator.getProject()));
    assertTrue(fixture.hasChildren(projectCreator.getFacetedProject()));
  }

  @Test
  public void testHasChildren_AppEngineStandardProjectElement_noConfigs() {
    AppEngineStandardProjectElement projectElement = mock(AppEngineStandardProjectElement.class);
    when(projectElement.getConfigurations()).thenReturn(new AppEngineResourceElement[0]);
    assertFalse(fixture.hasChildren(projectElement));
  }

  @Test
  public void testHasChildren_AppEngineStandardProjectElement_withConfigs() {
    AppEngineStandardProjectElement projectElement = mock(AppEngineStandardProjectElement.class);
    when(projectElement.getConfigurations()).thenReturn(new AppEngineResourceElement[1]);
    assertTrue(fixture.hasChildren(projectElement));
  }

  @Test
  public void testDynamicChanges() throws CoreException {
    projectCreator.withFacets(AppEngineStandardFacet.JRE7, WebFacetUtils.WEB_25);
    StructuredViewer viewer = mock(StructuredViewer.class);
    fixture.inputChanged(viewer, null, null); // installs resource-changed listener

    createAppEngineWebXml(null);
    verify(refresher, atLeastOnce()).accept(anyObject());
    Object[] children = fixture.getChildren(projectCreator.getFacetedProject());
    assertNotNull(children);
    assertEquals(1, children.length);
    assertTrue(children[0] instanceof AppEngineStandardProjectElement);
    AppEngineStandardProjectElement projectElement = (AppEngineStandardProjectElement) children[0];
    children = fixture.getChildren(projectElement);
    assertNotNull(children);
    assertEquals(0, children.length);

    IFile cronXml = createEmptyCronXml();
    verify(refresher, atLeastOnce()).accept(anyObject());
    children = fixture.getChildren(projectCreator.getFacetedProject());
    assertNotNull(children);
    assertEquals(1, children.length);
    assertTrue(children[0] == projectElement);
    children = fixture.getChildren(projectElement);
    assertNotNull(children);
    assertEquals(1, children.length);
    assertThat(children, hasItemInArray(instanceOf(CronDescriptor.class)));

    createEmptyDatastoreIndexesXml();
    verify(refresher, atLeastOnce()).accept(anyObject());
    children = fixture.getChildren(projectCreator.getFacetedProject());
    assertNotNull(children);
    assertEquals(1, children.length);
    assertTrue(children[0] == projectElement);
    children = fixture.getChildren(projectElement);
    assertNotNull(children);
    assertEquals(2, children.length);
    assertThat(children, hasItemInArray(instanceOf(CronDescriptor.class)));
    assertThat(children, hasItemInArray(instanceOf(DatastoreIndexesDescriptor.class)));

    cronXml.delete(true, null);
    verify(refresher, atLeastOnce()).accept(anyObject());
    children = fixture.getChildren(projectCreator.getFacetedProject());
    assertNotNull(children);
    assertEquals(1, children.length);
    assertTrue(children[0] == projectElement);
    children = fixture.getChildren(projectElement);
    assertNotNull(children);
    assertEquals(1, children.length);
    assertThat(children, hasItemInArray(instanceOf(DatastoreIndexesDescriptor.class)));
  }

  private IFile createInWebInf(IPath path, String contents) {
    try {
      // createFileInWebInf() does not overwrite files
      IFile previous = WebProjectUtil.findInWebInf(projectCreator.getProject(), path);
      if (previous != null && previous.exists()) {
        previous.delete(true, null);
      }
      return WebProjectUtil.createFileInWebInf(
          projectCreator.getProject(),
          path,
          new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)),
          null);
    } catch (CoreException ex) {
      fail(ex.toString());
      /*NOTREACHED*/
      return null;
    }
  }

  private IFile createEmptyCronXml() {
    return createInWebInf(
        new Path("cron.xml"), // $NON-NLS-1$
        "<cronentries/>"); // $NON-NLS-1$
  }

  private IFile createEmptyDispatchXml() {
    return createInWebInf(
        new Path("dispatch.xml"), // $NON-NLS-1$
        "<dispatch-entries/>"); // $NON-NLS-1$
  }

  private IFile createEmptyDosXml() {
    return createInWebInf(
        new Path("dos.xml"), // $NON-NLS-1$
        "<blacklistentries/>"); // $NON-NLS-1$
  }

  private IFile createEmptyQueueXml() {
    return createInWebInf(
        new Path("queue.xml"), // $NON-NLS-1$
        "<queue-entries/>"); // $NON-NLS-1$
  }

  private IFile createEmptyDatastoreIndexesXml() {
    return createInWebInf(
        new Path("datastore-indexes.xml"), // $NON-NLS-1$
        "<datastore-indexes/>"); // $NON-NLS-1$
  }

  private IFile createAppEngineWebXml(String serviceId) {
    String contents =
        Strings.isNullOrEmpty(serviceId)
            ? "<appengine-web-app xmlns='http://appengine.google.com/ns/1.0'/>" // $NON-NLS-1$
            : "<appengine-web-app xmlns='http://appengine.google.com/ns/1.0'><service>" // $NON-NLS-1$
                + serviceId
                + "</service></appengine-web-app>"; // $NON-NLS-1$
    return createInWebInf(new Path("appengine-web.xml"), contents); // $NON-NLS-1$
  }
}
