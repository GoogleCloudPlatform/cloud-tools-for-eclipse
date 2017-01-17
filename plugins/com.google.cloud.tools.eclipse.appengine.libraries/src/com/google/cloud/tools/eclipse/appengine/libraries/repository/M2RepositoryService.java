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

package com.google.cloud.tools.eclipse.appengine.libraries.repository;

import com.google.cloud.tools.eclipse.appengine.libraries.model.LibraryFile;
import com.google.cloud.tools.eclipse.appengine.libraries.model.MavenCoordinates;
import com.google.cloud.tools.eclipse.appengine.libraries.util.PathUtil;
import com.google.cloud.tools.eclipse.util.io.FileDownloader;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import org.apache.maven.artifact.Artifact;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * Implementation of {@link ILibraryRepositoryService} that relies on M2Eclipse to download the
 * artifacts and store them in the local Maven repository pointed to by M2Eclipse's M2_REPO variable.
 */
@Component
public class M2RepositoryService implements ILibraryRepositoryService {

  private MavenHelper mavenHelper;

  @Override
  public Artifact resolveArtifact(LibraryFile libraryFile, IProgressMonitor monitor) throws CoreException {
    MavenCoordinates mavenCoordinates = libraryFile.getMavenCoordinates();
    return mavenHelper.resolveArtifact(mavenCoordinates, monitor);
  }

  @Override
  public IPath resolveSourceArtifact(LibraryFile libraryFile, String versionHint, IProgressMonitor monitor) throws CoreException {
    MavenCoordinates mavenCoordinates = libraryFile.getMavenCoordinates();
    MavenCoordinates sourceCoordinates = new MavenCoordinates(mavenCoordinates);
    if (!Strings.isNullOrEmpty(versionHint)) {
      sourceCoordinates.setVersion(versionHint);
    }
    sourceCoordinates.setClassifier("sources");
    if (libraryFile.getSourceUri() == null) {
      return new Path(mavenHelper.resolveArtifact(sourceCoordinates, monitor).getFile().getAbsolutePath());
    } else {
      return getDownloadedSourceLocation(sourceCoordinates, getSourceUrlFromUri(libraryFile.getSourceUri()), monitor);
    }
  }

  private URL getSourceUrlFromUri(URI sourceUri) {
    try {
      if (sourceUri == null) {
        return null;
      } else {
        return sourceUri.toURL();
      }
    } catch (MalformedURLException | IllegalArgumentException e) {
      // should not cause error in the resolution process, we'll disregard it
      return null;
    }
  }

  private IPath getDownloadedSourceLocation(MavenCoordinates mavenCoordinates, URL sourceUrl,
                                            IProgressMonitor monitor) {
    try {
      IPath downloadFolder = PathUtil.bundleStateBasedMavenFolder(mavenCoordinates);
      return new FileDownloader(downloadFolder).download(sourceUrl, monitor);
    } catch (IOException e) {
      // source file is failed to download, this is not an error
      return null;
    }
  }

  @Activate
  protected void activate() {
    mavenHelper = new MavenHelper();
  }

  /*
   * To make sure that mavenHelper is not null in production the activate() method must be called.
   */
  @VisibleForTesting
  void setMavenHelper(MavenHelper mavenHelper) {
    this.mavenHelper = mavenHelper;
  }
}
