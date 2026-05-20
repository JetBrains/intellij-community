package org.jetbrains.idea.reposearch;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo;

import static com.intellij.util.containers.ContainerUtil.emptyList;

@ApiStatus.Experimental
public class PoisonedMavenRepoArtifactInfo extends MavenRepoArtifactInfo implements RepositoryArtifactData {

  public static PoisonedMavenRepoArtifactInfo INSTANCE = new PoisonedMavenRepoArtifactInfo();

  private PoisonedMavenRepoArtifactInfo() { super("", "", emptyList()); }

  @Override
  public String getKey() {
    return RepositoryArtifactData.class.getCanonicalName();
  }

  @Override
  public @NotNull RepositoryArtifactData mergeWith(@NotNull RepositoryArtifactData another) {
    return INSTANCE;
  }
}