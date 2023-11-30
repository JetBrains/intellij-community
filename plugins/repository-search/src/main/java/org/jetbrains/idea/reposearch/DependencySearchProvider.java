package org.jetbrains.idea.reposearch;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface DependencySearchProvider {
  CompletableFuture<List<RepositoryArtifactData>> fulltextSearch(@NotNull String searchString);

  CompletableFuture<List<RepositoryArtifactData>> suggestPrefix(@NotNull String groupId, @NotNull String artifactId);

  boolean isLocal();
}
