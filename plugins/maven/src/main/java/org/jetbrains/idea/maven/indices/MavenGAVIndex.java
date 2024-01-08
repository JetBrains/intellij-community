// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;


import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.model.RepositoryKind;

import java.util.Collection;
import java.util.Set;

public interface MavenGAVIndex extends MavenRepositoryIndex {
  @NotNull Collection<@NotNull String> getGroupIds();

  @NotNull Set<@NotNull String> getArtifactIds(@NotNull String groupId);

  @NotNull Set<@NotNull String> getVersions(@NotNull String groupId, @NotNull String artifactId);

  boolean hasGroupId(@NotNull String groupId);

  boolean hasArtifactId(@NotNull String groupId, @NotNull String artifactId);

  boolean hasVersion(@NotNull String groupId, @NotNull String artifactId, @NotNull String version);
}
