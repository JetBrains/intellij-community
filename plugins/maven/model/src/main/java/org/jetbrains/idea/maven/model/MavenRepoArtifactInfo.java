// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

@ApiStatus.Experimental
public class MavenRepoArtifactInfo implements MavenCoordinate {

  private final String groupId;
  private final String artifactId;
  public final MavenDependencyCompletionItem[] items;

  public MavenRepoArtifactInfo(String groupId, String artifactId, MavenDependencyCompletionItem[] items) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.items = items;
  }

  public MavenRepoArtifactInfo(String groupId,
                                     String artifactId,
                                     Collection<String> versions) {
    this(
      groupId,
      artifactId,
      versions.stream()
        .map(v -> new MavenDependencyCompletionItem(groupId, artifactId, v))
        .toArray(MavenDependencyCompletionItem[]::new)
    );
  }

  @Override
  public String getGroupId() {
    return groupId;
  }

  @Override
  public String getArtifactId() {
    return artifactId;
  }

  @Override
  public String getVersion() {
    return items.length > 0 ? items[0].getVersion() : null;
  }

  public MavenDependencyCompletionItem[] getItems() {
    return items;
  }

  @Override
  @NonNls
  public String toString() {
    return "maven(" + groupId + ":" + artifactId + ":" + getVersion() + " " + items.length + " total)";
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    MavenRepoArtifactInfo info = (MavenRepoArtifactInfo)o;
    return Objects.equals(groupId, info.groupId) &&
           Objects.equals(artifactId, info.artifactId) &&
           Objects.deepEquals(items, info.items);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, artifactId, Arrays.hashCode(items));
  }
}