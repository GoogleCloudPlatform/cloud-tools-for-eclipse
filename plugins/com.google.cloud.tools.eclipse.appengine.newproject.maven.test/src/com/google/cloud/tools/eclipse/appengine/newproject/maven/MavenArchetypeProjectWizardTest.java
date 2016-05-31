package com.google.cloud.tools.eclipse.appengine.newproject.maven;

import static org.junit.Assert.assertNotNull;

import org.eclipse.swt.widgets.Display;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MavenArchetypeProjectWizardTest {

  private MavenArchetypeProjectWizard wizard;

  @Before
  public void setUp() {
    assertNotNull(Display.getDefault());

    wizard = new MavenArchetypeProjectWizard();
    wizard.addPages();
  }
  
  @Test
  public void testCanFinish() {
    Assert.assertFalse(wizard.canFinish());
  }

  @Test
  public void testOnePage() {
    Assert.assertEquals(1, wizard.getPageCount());
  }
  
  @Test
  public void testGetPageByName() {
    Assert.assertNotNull(wizard.getPage("basicNewProjectPage"));
  }
  
}
