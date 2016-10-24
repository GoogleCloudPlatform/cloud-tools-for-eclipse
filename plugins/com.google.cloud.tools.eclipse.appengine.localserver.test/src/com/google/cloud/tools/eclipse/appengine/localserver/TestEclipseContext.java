/*
 * Copyright 2016 Google Inc.
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

package com.google.cloud.tools.eclipse.appengine.localserver;

import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.junit.rules.ExternalResource;
import org.osgi.framework.FrameworkUtil;

public class TestEclipseContext extends ExternalResource {

  private Class<?> testClass;
  private IEclipseContext eclipseContext;

  public TestEclipseContext(Class<?> testClass) {
    this.testClass = testClass;
  }

  @Override
  protected void before() throws Throwable {
    eclipseContext = EclipseContextFactory.createServiceContext(FrameworkUtil.getBundle(testClass).getBundleContext());
  }

  @Override
  protected void after() {
    if (eclipseContext != null) {
      eclipseContext.dispose();
    }
  }

  public <T> void set(Class<T> clazz, T value) {
    eclipseContext.set(clazz, value);
  }

  public IEclipseContext getContext() {
    return eclipseContext;
  }
}
