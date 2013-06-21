package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;

/**
 * See org.apache.maven.artifact.Artifact#getDependencyConflictId()
 *
 * @author Sergey Evdokimov
 */
public class DependencyConflictId {
  private final String groupId;
  private final String artifactId;
  private final String type;
  private final String classifier;

  public DependencyConflictId(@NotNull String groupId, @NotNull String artifactId, @Nullable String type, @Nullable String classifier) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.type = StringUtil.isEmpty(type) ? "jar" : type;
    this.classifier = classifier;
  }

  @Nullable
  public static DependencyConflictId create(@NotNull MavenDomDependency dep) {
    String groupId = dep.getGroupId().getStringValue();
    if (StringUtil.isEmpty(groupId)) return null;

    String artifactId = dep.getArtifactId().getStringValue();
    if (StringUtil.isEmpty(artifactId)) return null;

    //noinspection ConstantConditions
    return new DependencyConflictId(groupId, artifactId, dep.getType().getStringValue(), dep.getClassifier().getStringValue());
  }

  public boolean isValid() {
    return StringUtil.isNotEmpty(groupId) && StringUtil.isNotEmpty(artifactId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DependencyConflictId)) return false;

    DependencyConflictId id = (DependencyConflictId)o;

    if (artifactId != null ? !artifactId.equals(id.artifactId) : id.artifactId != null) return false;
    if (classifier != null ? !classifier.equals(id.classifier) : id.classifier != null) return false;
    if (groupId != null ? !groupId.equals(id.groupId) : id.groupId != null) return false;
    if (!type.equals(id.type)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = groupId != null ? groupId.hashCode() : 0;
    result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
    result = 31 * result + type.hashCode();
    result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
    return result;
  }
}
