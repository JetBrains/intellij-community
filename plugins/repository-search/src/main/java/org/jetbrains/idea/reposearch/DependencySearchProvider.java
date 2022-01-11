package org.jetbrains.idea.reposearch;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

@ApiStatus.Experimental
public interface DependencySearchProvider {
  void fulltextSearch(@NotNull String searchString, @NotNull Consumer<RepositoryArtifactData> consumer);

  void suggestPrefix(@Nullable String groupId, @Nullable String artifactId, @NotNull Consumer<RepositoryArtifactData> consumer);

  boolean isLocal();
}
