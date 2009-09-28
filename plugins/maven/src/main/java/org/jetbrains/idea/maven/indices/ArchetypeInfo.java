package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.util.text.StringUtil;
import org.apache.maven.archetype.catalog.Archetype;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArchetypeInfo {
  public final String groupId;
  public final String artifactId;
  public final String version;
  public final String repository;
  public final String description;

  public ArchetypeInfo(Archetype archetype) {
    this(archetype.getGroupId(),
         archetype.getArtifactId(),
         archetype.getVersion(),
         archetype.getRepository(),
         archetype.getDescription());
  }

  public ArchetypeInfo(@NotNull String groupId,
                       @NotNull String artifactId,
                       @NotNull String version,
                       @Nullable String repository,
                       @Nullable String description) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
    this.repository = StringUtil.isEmptyOrSpaces(repository) ? null : repository;
    this.description = StringUtil.isEmptyOrSpaces(description) ? null : description;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ArchetypeInfo that = (ArchetypeInfo)o;

    if (groupId != null ? !groupId.equals(that.groupId) : that.groupId != null) return false;
    if (artifactId != null ? !artifactId.equals(that.artifactId) : that.artifactId != null) return false;
    if (version != null ? !version.equals(that.version) : that.version != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = groupId != null ? groupId.hashCode() : 0;
    result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    return result;
  }
}
