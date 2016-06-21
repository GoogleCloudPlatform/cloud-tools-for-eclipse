package com.google.cloud.tools.eclipse.appengine.localserver;

import org.eclipse.jdt.core.IClasspathEntry;
import org.junit.Assert;
import org.junit.Test;

// Must Run as JUnit Plug-in Test in Eclipse.
public class ServletClasspathProviderTest {

  private ServletClasspathProvider provider = new ServletClasspathProvider();
  
  @Test
  public void testResolveClasspathContainer() {
     IClasspathEntry[] result = provider.resolveClasspathContainer(null, null);
     Assert.assertEquals(2, result.length);
     Assert.assertTrue("servlet", result[0].getPath().toString().endsWith("servlet-api.jar"));
     Assert.assertTrue("jsp", result[1].getPath().toString().endsWith("jsp-api.jar"));
  }

}
