package org.jetbrains.idea.maven.core.util;

import com.intellij.openapi.util.Comparing;
import org.apache.maven.artifact.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenConstants;

public class MavenId implements Comparable<MavenId>{
  public String groupId;
  public String artifactId;
  public String version;
  public String type;
  public String classifier;
  private String baseVersion;

  @SuppressWarnings({"UnusedDeclaration"})
  public MavenId() {
  }

  public MavenId(String groupId, String artifactId, String version) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
  }

  public MavenId(Artifact artifact) {
    this(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    type = artifact.getType();
    classifier = artifact.getClassifier();
    baseVersion = artifact.getBaseVersion();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MavenId projectId = (MavenId)o;

    if (artifactId != null ? !artifactId.equals(projectId.artifactId) : projectId.artifactId != null) return false;
    if (groupId != null ? !groupId.equals(projectId.groupId) : projectId.groupId != null) return false;
    //noinspection RedundantIfStatement
    if (version != null ? !version.equals(projectId.version) : projectId.version != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (groupId != null ? groupId.hashCode() : 0);
    result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    return result;
  }

  public String toString() {
    String result = groupId + ":" + artifactId;

    if (type != null && !MavenConstants.JAR_TYPE.equals(type)) result += ":" + type;
    if (classifier != null) result += ":" + classifier;

    String selectedVersion = baseVersion == null ? version : baseVersion;
    if (selectedVersion != null) result += ":" + selectedVersion;

    return result;
  }

  public boolean matches(@NotNull final MavenId that) {
    return Comparing.equal(groupId, that.groupId)
           && Comparing.equal(artifactId, that.artifactId)
           && (version == null || that.version == null || version.equals(that.version));
  }

  public int compareTo(final MavenId that) {
    return toString().compareTo(that.toString());
  }
}
