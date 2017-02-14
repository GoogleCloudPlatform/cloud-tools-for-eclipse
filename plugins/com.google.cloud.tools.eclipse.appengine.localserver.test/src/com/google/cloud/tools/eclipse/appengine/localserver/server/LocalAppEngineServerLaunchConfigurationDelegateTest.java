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

package com.google.cloud.tools.eclipse.appengine.localserver.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.appengine.api.devserver.DefaultRunConfiguration;
import java.util.Arrays;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.wst.server.core.IServer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LocalAppEngineServerLaunchConfigurationDelegateTest {

  @Mock
  ILaunchConfiguration launchConfiguration;
  @Mock private IServer server;
  @Mock private LocalAppEngineServerBehaviour serverBehavior;

  @Before
  public void setUp() {
    when(server.loadAdapter(any(Class.class), any(IProgressMonitor.class)))
        .thenReturn(serverBehavior);
  }

  @Test
  public void testDeterminePageLocation() {
    when(server.getHost()).thenReturn("192.168.1.1");
    when(serverBehavior.getServerPort()).thenReturn(8085);

    String url = LocalAppEngineServerLaunchConfigurationDelegate.determinePageLocation(server);
    assertEquals("http://192.168.1.1:8085", url);
  }

  @Test
  public void testComparePorts() {
    assertTrue(LocalAppEngineServerLaunchConfigurationDelegate.equalPorts(null, null, 1));
    assertFalse(LocalAppEngineServerLaunchConfigurationDelegate.equalPorts(null, null, 0));
    assertTrue(LocalAppEngineServerLaunchConfigurationDelegate.equalPorts(1, 1, -1));
    assertTrue(LocalAppEngineServerLaunchConfigurationDelegate.equalPorts(1, null, 1));
    assertFalse(LocalAppEngineServerLaunchConfigurationDelegate.equalPorts(1, null, 2));
    assertFalse(LocalAppEngineServerLaunchConfigurationDelegate.equalPorts(null, 1, 2));
    assertTrue(LocalAppEngineServerLaunchConfigurationDelegate.equalPorts(null, 1, 1));
    assertFalse(LocalAppEngineServerLaunchConfigurationDelegate.equalPorts(0, 1, 1));
    assertFalse(LocalAppEngineServerLaunchConfigurationDelegate.equalPorts(1, 0, 1));
    assertFalse(LocalAppEngineServerLaunchConfigurationDelegate.equalPorts(0, null, 1));
    assertFalse(LocalAppEngineServerLaunchConfigurationDelegate.equalPorts(null, 0, 1));
    assertFalse(LocalAppEngineServerLaunchConfigurationDelegate.equalPorts(0, 0, 1));
  }


  private static final String attributeName = "appEngineDevServerPort";

  @Test
  public void testGetPortAttribute_launchAttribute() throws CoreException {
    when(launchConfiguration.getAttribute(eq(attributeName), eq(-1))).thenReturn(65535);
    assertEquals(65535, LocalAppEngineServerLaunchConfigurationDelegate
        .getPortAttribute(attributeName, 0, launchConfiguration, server));
    verify(launchConfiguration, times(1)).getAttribute(eq(attributeName), anyInt());
    verify(server, never()).getAttribute(eq(attributeName), anyInt());
  }

  @Test
  public void testGetPortAttribute_noLaunchAttribute() throws CoreException {
    when(launchConfiguration.getAttribute(eq(attributeName), eq(-1))).thenReturn(-1);
    when(server.getAttribute(eq(attributeName), eq(0))).thenReturn(65535);
    assertEquals(65535, LocalAppEngineServerLaunchConfigurationDelegate
        .getPortAttribute(attributeName, 0, launchConfiguration, server));
    verify(launchConfiguration, times(1)).getAttribute(eq(attributeName), anyInt());
    verify(server, times(1)).getAttribute(eq(attributeName), eq(0));
  }

  @Test
  public void testGetPortAttribute_noLaunchOrServerAttributes() throws CoreException {
    when(launchConfiguration.getAttribute(eq(attributeName), eq(-1))).thenReturn(-1);
    when(server.getAttribute(eq(attributeName), eq(65535))).thenReturn(65535);
    assertEquals(65535, LocalAppEngineServerLaunchConfigurationDelegate
        .getPortAttribute(attributeName, 65535, launchConfiguration, server));
    verify(launchConfiguration, times(1)).getAttribute(eq(attributeName), anyInt());
    verify(server, times(1)).getAttribute(eq(attributeName), anyInt());
  }

  @Test
  public void testGenerateRunConfiguration() throws CoreException {
    when(launchConfiguration.getAttribute(anyString(), anyString()))
        .thenAnswer(AdditionalAnswers.returnsSecondArg());
    when(launchConfiguration.getAttribute(anyString(), anyInt()))
        .thenAnswer(AdditionalAnswers.returnsSecondArg());
    when(server.getAttribute(anyString(), anyInt()))
        .thenAnswer(AdditionalAnswers.returnsSecondArg());

    DefaultRunConfiguration config = new LocalAppEngineServerLaunchConfigurationDelegate()
        .generateServerRunConfiguration(launchConfiguration, server);
    assertNull(config.getHost());
    assertEquals((Integer) LocalAppEngineServerBehaviour.DEFAULT_SERVER_PORT, config.getPort());
    assertEquals((Integer) LocalAppEngineServerBehaviour.DEFAULT_ADMIN_PORT, config.getAdminPort());
    assertNull(config.getApiPort());
    assertNull(config.getJvmFlags());
    verify(server, atLeastOnce()).getHost();
    verify(launchConfiguration, atLeastOnce()).getAttribute(anyString(), anyInt());
    verify(server, atLeastOnce()).getAttribute(anyString(), anyInt());
  }

  @Test
  public void testGenerateRunConfiguration_withHost() throws CoreException {
    when(launchConfiguration.getAttribute(anyString(), anyString()))
        .thenAnswer(AdditionalAnswers.returnsSecondArg());
    when(server.getHost()).thenReturn("example.com");
    DefaultRunConfiguration config = new LocalAppEngineServerLaunchConfigurationDelegate()
        .generateServerRunConfiguration(launchConfiguration, server);
    assertEquals("example.com", config.getHost());
    verify(server, atLeastOnce()).getHost();
  }

  @Test
  public void testGenerateRunConfiguration_withServerPort() throws CoreException {
    when(launchConfiguration.getAttribute(anyString(), anyString()))
        .thenAnswer(AdditionalAnswers.returnsSecondArg());
    when(launchConfiguration
        .getAttribute(eq(LocalAppEngineServerBehaviour.SERVER_PORT_ATTRIBUTE_NAME), anyInt()))
            .thenReturn(9999);

    DefaultRunConfiguration config = new LocalAppEngineServerLaunchConfigurationDelegate()
        .generateServerRunConfiguration(launchConfiguration, server);

    assertNotNull(config.getPort());
    assertEquals(9999, (int) config.getPort());
    verify(launchConfiguration, times(1))
        .getAttribute(eq(LocalAppEngineServerBehaviour.SERVER_PORT_ATTRIBUTE_NAME), anyInt());
    verify(server, never()).getAttribute(anyString(), anyInt());
  }

  @Test
  public void testGenerateRunConfiguration_withAdminPort() throws CoreException {
    when(launchConfiguration.getAttribute(anyString(), anyString()))
        .thenAnswer(AdditionalAnswers.returnsSecondArg());
    when(launchConfiguration
        .getAttribute(eq(LocalAppEngineServerBehaviour.ADMIN_PORT_ATTRIBUTE_NAME), anyInt()))
            .thenReturn(9999);

    DefaultRunConfiguration config = new LocalAppEngineServerLaunchConfigurationDelegate()
        .generateServerRunConfiguration(launchConfiguration, server);

    assertNotNull(config.getAdminPort());
    assertEquals(9999, (int) config.getAdminPort());
    verify(launchConfiguration, times(1))
        .getAttribute(eq(LocalAppEngineServerBehaviour.ADMIN_PORT_ATTRIBUTE_NAME), anyInt());
    verify(server, never()).getAttribute(anyString(), anyInt());
  }

  @Test
  public void testGenerateRunConfiguration_withVMArgs() throws CoreException {
    when(launchConfiguration.getAttribute(eq(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS),
        anyString())).thenReturn("a b 'c d'");

    DefaultRunConfiguration config = new LocalAppEngineServerLaunchConfigurationDelegate()
        .generateServerRunConfiguration(launchConfiguration, server);

    assertNotNull(config.getJvmFlags());
    assertEquals(Arrays.asList("a", "b", "c d"), config.getJvmFlags());
    verify(launchConfiguration, times(1))
        .getAttribute(eq(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS), anyString());
  }
}
