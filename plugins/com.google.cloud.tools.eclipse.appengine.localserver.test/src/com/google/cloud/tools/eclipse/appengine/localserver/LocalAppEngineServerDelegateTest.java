package com.google.cloud.tools.eclipse.appengine.localserver;


import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.wst.server.core.IModule;
import org.junit.Assert;
import org.junit.Test;

import com.google.cloud.tools.eclipse.appengine.localserver.server.LocalAppEngineServerDelegate;

public class LocalAppEngineServerDelegateTest {

  private LocalAppEngineServerDelegate delegate = new LocalAppEngineServerDelegate();
  private IProgressMonitor monitor = new NullProgressMonitor();

  @Test
  public void testModifyModules() throws CoreException {
    delegate.modifyModules(null, null, monitor);
  }
  
  @Test
  public void testCanModifyModules() throws CoreException {
    IModule[] remove = new IModule[0];
    IModule[] add = new IModule[0];
    Assert.assertEquals(Status.OK_STATUS, delegate.canModifyModules(add, remove));
  }

}
