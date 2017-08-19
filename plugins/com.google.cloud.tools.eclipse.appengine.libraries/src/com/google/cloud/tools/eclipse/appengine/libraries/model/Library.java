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

package com.google.cloud.tools.eclipse.appengine.libraries.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

/**
 * A library that can be added to App Engine projects, e.g. App Engine Endpoints library.
 *
 */
public final class Library {
  public static final String CONTAINER_PATH_PREFIX =
      "com.google.cloud.tools.eclipse.appengine.libraries";

  private final String id;
  private String name;
  private String toolTip;
  private URI siteUri;
  private boolean export = true;
  private List<LibraryFile> libraryFiles = Collections.emptyList();
  private LibraryRecommendation recommendation = LibraryRecommendation.OPTIONAL;
  private String group;
  private String javaVersion="1.7";

  // IDs of other libraries that also need to be added to the build path with this library
  private List<String> libraryDependencies = new ArrayList<>();

  public Library(String id) {
    Preconditions.checkNotNull(id, "id null");
    Preconditions.checkArgument(!id.isEmpty(), "id empty");
    this.id = id;
  }

  @VisibleForTesting
  public Library(String id, List<LibraryFile> libraryFiles) {
    this.id = id;
    this.libraryFiles = libraryFiles;
  }
  
  public String getId() {
    return id;
  }

  public IPath getContainerPath() {
    return new Path(CONTAINER_PATH_PREFIX + "/" + id);
  }

  public String getName() {
    return name;
  }

  void setName(String name) {
    this.name = name;
  }
  
  /**
   * @return minimum Java version required for this library
   */
  public String getJavaVersion() {
    return javaVersion;
  }

  void setJavaVersion(String version) {
    this.javaVersion = version;
  }
  
  public String getToolTip() {
    return toolTip;
  }

  void setToolTip(String toolTip) {
    this.toolTip = toolTip;
  }

  public URI getSiteUri() {
    return siteUri;
  }

  void setSiteUri(URI siteUri) {
    this.siteUri = siteUri;
  }

  public List<LibraryFile> getLibraryFiles() {
    return new ArrayList<>(libraryFiles);
  }

  /**
   * @param libraryFiles artifacts associated with this library, cannot be <code>null</code>
   */
  void setLibraryFiles(List<LibraryFile> libraryFiles) {
    Preconditions.checkNotNull(libraryFiles);
    this.libraryFiles = new ArrayList<>(libraryFiles);
  }

  public boolean isExport() {
    return export;
  }

  void setExport(boolean export) {
    this.export = export;
  }

  public List<String> getLibraryDependencies() {
    return new ArrayList<>(libraryDependencies);
  }

  /**
   * @param libraryDependencies list of libraryIds that are dependencies of this library
   *     and should be added to the classpath, cannot be <code>null</code>
   */
  void setLibraryDependencies(List<String> libraryDependencies) {
    Preconditions.checkNotNull(libraryDependencies);
    this.libraryDependencies = new ArrayList<>(libraryDependencies);
  }

  /**
   * @param recommendation the level of recommendation for this library, cannot be <code>null</code>
   */
  void setRecommendation(LibraryRecommendation recommendation) {
    Preconditions.checkNotNull(recommendation);
    this.recommendation = recommendation;
  }

  public LibraryRecommendation getRecommendation() {
    return recommendation;
  }

  /**
   * @param group the collection to which this library belongs
   */
  void setGroup(String group) {
    this.group = group;
  }

  public String getGroup() {
    return group;
  }
  
  @Override
  /**
   * @return a string suitable fo rdebugging
   */
  public String toString() {
    return "Library: id=" + id + "; name=" + name;
  }
}
