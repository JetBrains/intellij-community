package org.jetbrains.idea.maven.core.util;

import org.apache.maven.artifact.Artifact;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.text.ParseException;

public class MavenId implements Comparable<MavenId>{
  public String groupId;
  public String artifactId;
  public String version;
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
    this.baseVersion = artifact.getBaseVersion();
    this.classifier = artifact.getClassifier();
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
    String selectedVersion = baseVersion == null ? version : baseVersion;

    String result = selectedVersion != null
                    ? MessageFormat.format("{0}:{1}:{2}", groupId, artifactId, selectedVersion)
                    : MessageFormat.format("{0}:{1}", groupId, artifactId);
    
    return classifier == null ? result : result + ":" + classifier;
  }

  public static MavenId parse(final String text) throws ParseException {
    final int colon1 = text.indexOf(":");
    if (colon1 <= 0) {
      throw new ParseException (text, 0);
    }
    final String groupId = text.substring(0, colon1);
    final String artifactId;
    final String version;
    final int colon2 = text.indexOf(":", colon1 + 1);
    if (colon2 <= 0) {
      artifactId = text.substring(colon1 + 1);
      version = null;
    }
    else {
      artifactId = text.substring(colon1 + 1, colon2);
      version = text.substring(colon2 + 1);
      final int colon3 = text.indexOf(":", colon2 + 1);
      if (colon3 > 0) {
        throw new ParseException (text, colon3);
      }
    }
    return new MavenId(groupId, artifactId, version);
  }

  public boolean matches(@NotNull final MavenId that) {
    return nullAwareEqual(groupId, that.groupId)
           && nullAwareEqual(artifactId, that.artifactId)
           && (version == null || that.version == null || version.equals(that.version));
  }

  private boolean nullAwareEqual(Object o1, Object o2) {
    if (o1 == null) return o2 == null;
    return o1.equals(o2);
  }

  public int compareTo(final MavenId that) {
    return toString().compareTo(that.toString());
  }
}
