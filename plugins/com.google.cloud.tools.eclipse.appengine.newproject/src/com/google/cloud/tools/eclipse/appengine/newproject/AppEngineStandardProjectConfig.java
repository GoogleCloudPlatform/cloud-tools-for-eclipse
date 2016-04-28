package com.google.cloud.tools.eclipse.appengine.newproject;

import java.net.URI;

import org.eclipse.core.resources.IProject;

/**
 * Collects all data needed to create and configure an App Engine Standard Project.
 */
class AppEngineStandardProjectConfig {

  private String appEngineProjectId;
  private String eclipseProjectName;
  private String packageName;
  private String eclipseProjectDirectory;
  private URI eclipseProjectLocationUri;
  private IProject project;

  // todo does builder pattern make more sense here?
  public void setAppEngineProjectId(String id) {
    this.appEngineProjectId = id;
  }

  public String getAppEngineProjectId() {
    return this.appEngineProjectId;
  }
  
  public void setEclipseProjectName(String name) {
    this.eclipseProjectName = name;
  }

  public String getEclipseProjectName() {
    return this.eclipseProjectName;
  }
  
  public void setPackageName(String name) {
    this.packageName = name;
  }

  public String getPackageName() {
    return this.packageName;
  }
  
  public void setEclipseProjectDirectory(String path) {
    this.eclipseProjectDirectory = path;
  }
  
  public String getEclipseProjectDirectory() {
    return this.eclipseProjectDirectory;
  }
  
  /**
   * @param a file URI to a local directory, or null for the default location
   */
  public void setEclipseProjectLocationUri(URI uri) {
    this.eclipseProjectLocationUri = uri;
  }
  
  /**
   * @return a file URI to a local directory, or null for the default location
   */
  public URI getEclipseProjectLocationUri() {
    return this.eclipseProjectLocationUri;
  }

  public void setProject(IProject project) {
    this.project = project;
  }

  public IProject getProject() {
    return this.project;
  }

}
