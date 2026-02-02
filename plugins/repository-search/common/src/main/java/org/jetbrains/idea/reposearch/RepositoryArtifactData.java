package org.jetbrains.idea.reposearch;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface RepositoryArtifactData {
  String getKey();

  @NotNull
  RepositoryArtifactData mergeWith(@NotNull RepositoryArtifactData another);
}