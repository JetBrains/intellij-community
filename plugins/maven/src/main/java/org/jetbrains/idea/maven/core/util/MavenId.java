package org.jetbrains.idea.maven.core.util;

import org.apache.maven.artifact.Artifact;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenId {
  @NonNls public String groupId;
  @NonNls public String artifactId;
  @NonNls public String version;

  @SuppressWarnings({"UnusedDeclaration"})
  public MavenId() {
  }

  public MavenId(@NonNls final String groupId, @NonNls final String artifactId, @NonNls final String version) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
  }

  public MavenId(Artifact artifact) {
    this(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
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
    return MessageFormat.format("{0}:{1}:{2}", groupId, artifactId, version);
  }

  public boolean matches(@NotNull final MavenId that) {
    return groupId.equals(that.groupId) && artifactId.equals(that.artifactId) &&
           (version == null || that.version == null || version.equals(that.version));
  }
}
