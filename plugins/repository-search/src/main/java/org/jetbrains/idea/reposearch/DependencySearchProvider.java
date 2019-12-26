package org.jetbrains.idea.reposearch;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public interface DependencySearchProvider {

  void fulltextSearch(@NotNull String searchString,
                      @NotNull Consumer<RepositoryArtifactData> consumer);

  void suggestPrefix(@NotNull String groupId, @NotNull String artifactId,
                      @NotNull Consumer<RepositoryArtifactData> consumer);

  boolean isLocal();
}
