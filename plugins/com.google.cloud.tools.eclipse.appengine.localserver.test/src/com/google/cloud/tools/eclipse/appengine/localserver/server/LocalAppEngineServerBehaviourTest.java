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
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Predicate;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.wst.server.core.IServer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LocalAppEngineServerBehaviourTest {
  @Mock
  private Predicate<Integer> alwaysTrue;
  @Mock
  private Predicate<Integer> alwaysFalse;
  @Mock
  private Predicate<Integer> portProber;

  @Mock
  private IServer server;
  private LocalAppEngineServerBehaviour serverBehavior = new LocalAppEngineServerBehaviour();

  @Before
  public void setUp() {
    when(alwaysTrue.apply(any(Integer.class))).thenReturn(true);
    when(alwaysFalse.apply(any(Integer.class))).thenReturn(false);
  }

  @Test
  public void testCheckPort_nullWith0Default() throws CoreException {
    LocalAppEngineServerBehaviour.checkPort(null, 0, portProber);
    verify(portProber, never()).apply(any(Integer.class));
  }

  @Test
  public void testCheckPort_nullWithNotInUseDefault() throws CoreException {
    LocalAppEngineServerBehaviour.checkPort(null, 1, alwaysFalse);
    verify(alwaysFalse, times(1)).apply(any(Integer.class));
  }

  @Test
  public void testCheckPort_nullWithInUseDefault() throws CoreException {
    try {
      LocalAppEngineServerBehaviour.checkPort(null, 1, alwaysTrue);
    } catch (CoreException ex) {
      assertEquals("Port 1 is in use.", ex.getMessage());
      verify(alwaysTrue, times(1)).apply(any(Integer.class));
    }
  }

  @Test
  public void testCheckPort_0portWith0Default() throws CoreException {
    LocalAppEngineServerBehaviour.checkPort(0, 0, portProber);
    verify(portProber, never()).apply(any(Integer.class));
  }

  @Test
  public void testCheckPort_portWithNotInUse() throws CoreException {
    LocalAppEngineServerBehaviour.checkPort(65535, 1, alwaysFalse);
    verify(alwaysFalse, times(1)).apply(any(Integer.class));
  }

  @Test
  public void testCheckPort_portWithInUse() throws CoreException {
    try {
      LocalAppEngineServerBehaviour.checkPort(65535, 1, alwaysTrue);
    } catch (CoreException ex) {
      assertEquals("Port 65535 is in use.", ex.getMessage());
      verify(alwaysTrue, times(1)).apply(any(Integer.class));
    }
  }

  @Test
  public void testCheckPort_portOutOfBounds() throws CoreException {
    try {
    LocalAppEngineServerBehaviour.checkPort(-1, 1, portProber);
    } catch (CoreException ex) {
      assertEquals("Port must be between 0 and 65535.", ex.getMessage());
      verify(portProber, never()).apply(any(Integer.class));
    }
  }


  private static final String[] serverOutputWithDefaultModule1 = new String[] {
      "WARNING  2016-11-03 21:11:21,930 devappserver2.py:785] DEFAULT_VERSION_HOSTNAME will not be set correctly with --port=0",
      "INFO     2016-11-03 21:11:21,956 api_server.py:205] Starting API server at: http://localhost:52892",
      "INFO     2016-11-03 21:11:21,959 dispatcher.py:197] Starting module \"default\" running at: http://localhost:55948",
      "INFO     2016-11-03 21:11:21,959 dispatcher.py:197] Starting module \"second\" running at: http://localhost:8081",
      "INFO     2016-11-03 21:11:21,959 admin_server.py:116] Starting admin server at: http://localhost:43679",
      "Nov 03, 2016 9:11:23 PM com.google.appengine.tools.development.SystemPropertiesManager setSystemProperties"
  };

  private static final String[] serverOutputWithDefaultModule2 = new String[] {
      "WARNING  2016-11-03 21:11:21,930 devappserver2.py:785] DEFAULT_VERSION_HOSTNAME will not be set correctly with --port=0",
      "INFO     2016-11-03 21:11:21,956 api_server.py:205] Starting API server at: http://localhost:52892",
      "INFO     2016-11-03 21:11:21,959 dispatcher.py:197] Starting module \"first\" running at: http://localhost:55948",
      "INFO     2016-11-03 21:11:21,959 dispatcher.py:197] Starting module \"default\" running at: http://localhost:8081",
      "INFO     2016-11-03 21:11:21,959 admin_server.py:116] Starting admin server at: http://localhost:43679",
      "Nov 03, 2016 9:11:23 PM com.google.appengine.tools.development.SystemPropertiesManager setSystemProperties"
  };

  private static final String[] serverOutputWithNoDefaultModule = new String[] {
      "WARNING  2016-11-03 21:11:21,930 devappserver2.py:785] DEFAULT_VERSION_HOSTNAME will not be set correctly with --port=0",
      "INFO     2016-11-03 21:11:21,956 api_server.py:205] Starting API server at: http://localhost:52892",
      "INFO     2016-11-03 21:11:21,959 dispatcher.py:197] Starting module \"first\" running at: http://localhost:8181",
      "INFO     2016-11-03 21:11:21,959 dispatcher.py:197] Starting module \"second\" running at: http://localhost:8182",
      "INFO     2016-11-03 21:11:21,959 dispatcher.py:197] Starting module \"third\" running at: http://localhost:8183",
      "INFO     2016-11-03 21:11:21,959 admin_server.py:116] Starting admin server at: http://localhost:43679",
      "Nov 03, 2016 9:11:23 PM com.google.appengine.tools.development.SystemPropertiesManager setSystemProperties"
  };

  @Test
  public void testExtractServerPortFromOutput_firstModuleIsDefault() throws CoreException {
    setUpServerPort(0);
    simulateOutputParsing(serverOutputWithDefaultModule1);
    assertEquals(55948, serverBehavior.getServerPort());
  }

  @Test
  public void testExtractServerPortFromOutput_secondModuleIsDefault() throws CoreException {
    setUpServerPort(0);
    simulateOutputParsing(serverOutputWithDefaultModule2);
    assertEquals(8081, serverBehavior.getServerPort());
  }

  @Test
  public void testExtractServerPortFromOutput_noDefaultModule() throws CoreException {
    setUpServerPort(0);
    simulateOutputParsing(serverOutputWithNoDefaultModule);
    assertEquals(8181, serverBehavior.getServerPort());
  }

  @Test
  public void testExtractServerPortFromOutput_defaultModuleDoesNotOverrideUserSpecifiedPort()
      throws CoreException {
    setUpServerPort(12345);
    simulateOutputParsing(serverOutputWithDefaultModule1);
    assertEquals(12345, serverBehavior.getServerPort());
  }

  @Test
  public void testExtractModuleUrlFromOutput_firstModuleIsDefault() throws CoreException {
    simulateOutputParsing(serverOutputWithDefaultModule1);
    assertEquals("http://localhost:55948", serverBehavior.getServiceUrl("default"));
    assertEquals("http://localhost:8081", serverBehavior.getServiceUrl("second"));
  }

  @Test
  public void testExtractModuleUrlFromOutput_noDefaultModule() throws CoreException {
    simulateOutputParsing(serverOutputWithNoDefaultModule);
    assertNull(serverBehavior.getServiceUrl("default"));
    assertEquals("http://localhost:8181", serverBehavior.getServiceUrl("first"));
    assertEquals("http://localhost:8182", serverBehavior.getServiceUrl("second"));
    assertEquals("http://localhost:8183", serverBehavior.getServiceUrl("third"));
  }

  @Test
  public void testExtractAdminPortFromOutput() throws CoreException {
    setUpServerPort(9080);
    setUpAdminPort(0);
    simulateOutputParsing(serverOutputWithDefaultModule1);
    assertEquals(43679, serverBehavior.adminPort);
  }

  private void setUpServerPort(int port) {
    serverBehavior.serverPort = port;
  }

  private void setUpAdminPort(int port) {
    serverBehavior.adminPort = port;
  }

  private void simulateOutputParsing(String[] output) {
    LocalAppEngineServerBehaviour.DevAppServerOutputListener outputListener =
        serverBehavior.new DevAppServerOutputListener();
    for (String line : output) {
      outputListener.onOutputLine(line);
    }
  }
}
