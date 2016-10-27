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

package com.google.cloud.tools.eclipse.appengine.newproject.maven;

import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Widget;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class UserChoseTest {

  private UserChose instance = new UserChose();
  private KeyEvent event;

  @Before 
  public void setUp() {
	Event parent = Mockito.mock(Event.class);
	parent.widget = Mockito.mock(Widget.class);
	event = new KeyEvent(parent);
  }
  
  @Test
  public void testInitiallyFalse() {
	Assert.assertFalse(instance.userChosePackageName());
  }
  
  @Test
  public void testTabKey() {
	event.character = '\t';
	instance.keyReleased(event);
	Assert.assertFalse(instance.userChosePackageName());
	
	event.character = 'u';
	instance.keyReleased(event);
	Assert.assertTrue(instance.userChosePackageName());
	
	event.character = '\t';
	instance.keyReleased(event);
	Assert.assertTrue(instance.userChosePackageName());
  }  
  
}
