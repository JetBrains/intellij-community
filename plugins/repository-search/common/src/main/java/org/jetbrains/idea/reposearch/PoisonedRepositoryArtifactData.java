package org.jetbrains.idea.reposearch;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class PoisonedRepositoryArtifactData implements RepositoryArtifactData {

  public static PoisonedRepositoryArtifactData INSTANCE = new PoisonedRepositoryArtifactData();

  private PoisonedRepositoryArtifactData() { }

  @Override
  public String getKey() {
    return RepositoryArtifactData.class.getCanonicalName();
  }

  @Override
  public @NotNull RepositoryArtifactData mergeWith(@NotNull RepositoryArtifactData another) {
    return INSTANCE;
  }
}