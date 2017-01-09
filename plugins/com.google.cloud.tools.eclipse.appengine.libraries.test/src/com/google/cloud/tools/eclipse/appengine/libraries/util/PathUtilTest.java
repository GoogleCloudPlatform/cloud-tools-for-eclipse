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

package com.google.cloud.tools.eclipse.appengine.libraries.util;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.apache.maven.artifact.Artifact;
import org.eclipse.core.runtime.IPath;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PathUtilTest {

  private static final String EXPECTED_DOWNLOAD_FOLDER =
      ".metadata/.plugins/com.google.cloud.tools.eclipse.appengine.libraries/downloads/groupId/artifactId/1.0.0";

  @Mock private Artifact artifact;

  @Test
  public void test() {
    when(artifact.getGroupId()).thenReturn("groupId");
    when(artifact.getArtifactId()).thenReturn("artifactId");
    when(artifact.getVersion()).thenReturn("1.0.0");
    IPath folder = PathUtil.bundleStateBasedMavenFolder(artifact);
    assertTrue(folder.toString().endsWith(EXPECTED_DOWNLOAD_FOLDER));
  }
}
