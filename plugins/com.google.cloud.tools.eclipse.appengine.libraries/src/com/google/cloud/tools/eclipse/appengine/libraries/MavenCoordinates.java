package com.google.cloud.tools.eclipse.appengine.libraries;

import com.google.common.base.Preconditions;

/**
 * Describes a Maven artifact.
 */
public class MavenCoordinates {

  public static final String LATEST_VERSION = "LATEST";
  public static final String JAR_TYPE = "jar";
  public static final String MAVEN_CENTRAL_REPO = "central";

  private String repository = MAVEN_CENTRAL_REPO;
  private String groupId;
  private String artifactId;
  private String version = LATEST_VERSION;
  private String type = JAR_TYPE;
  private String classifier;

  /**
   * @param repository the URI or the identifier of the repository used to download the artifact from. It is treated
   * as an URI if it starts with <code>&lt;protocol&gt;://</code>. Cannot be <code>null</code>.
   * @param groupId the Maven group ID, cannot be <code>null</code>
   * @param artifactId the Maven artifact ID, cannot be <code>null</code>
   */
  public MavenCoordinates(String groupId, String artifactId) {
    Preconditions.checkNotNull(groupId, "groupId null");
    Preconditions.checkNotNull(artifactId, "artifactId null");
    Preconditions.checkArgument(!groupId.isEmpty(), "groupId empty");
    Preconditions.checkArgument(!artifactId.isEmpty(), "artifactId empty");

    this.groupId = groupId;
    this.artifactId = artifactId;
  }

  /**
   * @return the Maven version of the artifact, defaults to special value {@link MavenCoordinates#LATEST_VERSION}, never
   * <code>null</code>
   */
  public String getVersion() {
    return version;
  }

  /**
   * @param version the Maven version of the artifact, defaults to special value {@link MavenCoordinates#LATEST_VERSION},
   * cannot be <code>null</code> or empty string.
   */
  public void setVersion(String version) {
    Preconditions.checkNotNull(version, "version is null");
    Preconditions.checkArgument(!version.isEmpty(), "version is empty");
    this.version = version;
  }

  /**
   * @return the Maven packaging type, defaults to <code>jar</code>, never <code>null</code>
   */
  public String getType() {
    return type;
  }

  /**
   * @param type the Maven packaging type, defaults to <code>jar</code>, cannot be <code>null</code> or empty 
   * string.
   */
  public void setType(String type) {
    Preconditions.checkNotNull(type, "type is null");
    Preconditions.checkArgument(!type.isEmpty(), "type is empty");
    this.type = type;
  }

  /**
   * @return the Maven classifier or <code>null</code> if it was not set
   */
  public String getClassifier() {
    return classifier;
  }

  /**
   * @param classifier the Maven classifier, defaults to null.
   */
  public void setClassifier(String classifier) {
    this.classifier = classifier;
  }

  /**
   * @return the URI or the identifier of the repository used to download the artifact from, never <code>null</code>
   */
  public String getRepository() {
    return repository;
  }

  /**
   * @param repository the URI or the identifier of the repository used to download the artifact from, cannot be
   * <code>null</code> or empty string.
   */
  public void setRepository(String repository) {
    Preconditions.checkNotNull(repository, "repository null");
    Preconditions.checkArgument(!repository.isEmpty(), "repository is empty");
    this.repository = repository;
  }

  /**
   * @return the Maven group ID, never <code>null</code>
   */
  public String getGroupId() {
    return groupId;
  }

  /**
   * @return the Maven artifact ID, never <code>null</code>
   */
  public String getArtifactId() {
    return artifactId;
  }
}
