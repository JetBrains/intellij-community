package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.model.MavenArtifact;

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

  @Nullable
  public static DependencyConflictId create(@NotNull MavenArtifact dep) {
    return create(dep.getGroupId(), dep.getArtifactId(), dep.getType(), dep.getClassifier());
  }

  @Nullable
  public static DependencyConflictId create(String groupId, String artifactId, String type, String classifier) {
    if (StringUtil.isEmpty(groupId)) return null;
    if (StringUtil.isEmpty(artifactId)) return null;

    return new DependencyConflictId(groupId, artifactId, type, classifier);
  }

  @NotNull
  public String getGroupId() {
    return groupId;
  }

  @NotNull
  public String getArtifactId() {
    return artifactId;
  }

  @NotNull
  public String getType() {
    return type;
  }

  @Nullable
  public String getClassifier() {
    return classifier;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DependencyConflictId)) return false;

    DependencyConflictId id = (DependencyConflictId)o;

    if (!artifactId.equals(id.artifactId)) return false;
    if (classifier != null ? !classifier.equals(id.classifier) : id.classifier != null) return false;
    if (!groupId.equals(id.groupId)) return false;
    if (!type.equals(id.type)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = groupId.hashCode();
    result = 31 * result + artifactId.hashCode();
    result = 31 * result + type.hashCode();
    result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
    return result;
  }
}
