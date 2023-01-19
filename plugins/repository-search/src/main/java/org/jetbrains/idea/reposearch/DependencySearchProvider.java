package org.jetbrains.idea.reposearch;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface DependencySearchProvider {
  CompletableFuture<List<RepositoryArtifactData>> fulltextSearch(@NotNull String searchString);

  CompletableFuture<List<RepositoryArtifactData>> suggestPrefix(@Nullable String groupId, @Nullable String artifactId);

  boolean isLocal();
}
