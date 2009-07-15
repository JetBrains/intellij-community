package org.jetbrains.idea.maven.project;

import org.apache.maven.artifact.Artifact;

import java.io.Serializable;

public class MavenId implements Serializable {
  public static final String UNKNOWN_VALUE = "Unknown";

  private final String myGroupId;
  private final String myArtifactId;
  private final String myVersion;

  public MavenId(String groupId, String artifactId, String version) {
    this.myGroupId = groupId;
    this.myArtifactId = artifactId;
    this.myVersion = version;
  }

  public MavenId(Artifact artifact) {
    this(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
  }

  public String getGroupId() {
    return myGroupId;
  }

  public String getArtifactId() {
    return myArtifactId;
  }

  public String getVersion() {
    return myVersion;
  }

  public String getDisplayString() {
    StringBuilder builder = new StringBuilder();

    append(builder, myGroupId);
    append(builder, myArtifactId);
    append(builder, myVersion);

    return builder.toString();
  }

  public static void append(StringBuilder builder, String part) {
    if (builder.length() != 0) builder.append(':');
    builder.append(part);
  }

  @Override
  public String toString() {
    return getDisplayString();
  }

  public boolean equals(String groupId, String artifactId, String version) {
    if (myGroupId != null ? !myGroupId.equals(groupId) : groupId != null) return false;
    if (myArtifactId != null ? !myArtifactId.equals(artifactId) : artifactId != null) return false;
    if (myVersion != null ? !myVersion.equals(version) : version != null) return false;
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenId other = (MavenId)o;
    return equals(other.getGroupId(), other.myArtifactId, other.myVersion);
  }

  @Override
  public int hashCode() {
    int result;
    result = (myGroupId != null ? myGroupId.hashCode() : 0);
    result = 31 * result + (myArtifactId != null ? myArtifactId.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    return result;
  }
}
