package org.jetbrains.idea.maven.project;

import org.apache.maven.artifact.Artifact;

import java.io.Serializable;

public class MavenId implements Serializable {
  public static final String UNKNOWN_VALUE = "Unknown";

  public String groupId;
  public String artifactId;
  public String version;

  public MavenId(String groupId, String artifactId, String version) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
  }

  public MavenId(Artifact artifact) {
    this(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
  }

  public String displayString() {
    StringBuilder builder = new StringBuilder();

    append(builder, groupId);
    append(builder, artifactId);
    append(builder, version);

    return builder.toString();
  }

  public static void append(StringBuilder builder, String part) {
    if (builder.length() != 0) builder.append(':');
    builder.append(part);
  }

  @Override
  public String toString() {
    return displayString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenId projectId = (MavenId)o;

    if (groupId != null ? !groupId.equals(projectId.groupId) : projectId.groupId != null) return false;
    if (artifactId != null ? !artifactId.equals(projectId.artifactId) : projectId.artifactId != null) return false;
    if (version != null ? !version.equals(projectId.version) : projectId.version != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = (groupId != null ? groupId.hashCode() : 0);
    result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    return result;
  }
}
