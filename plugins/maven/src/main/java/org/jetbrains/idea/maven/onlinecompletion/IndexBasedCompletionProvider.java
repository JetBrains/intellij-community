// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.MavenIndex;
import org.jetbrains.idea.maven.indices.MavenSearchIndex;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.reposearch.DependencySearchProvider;
import org.jetbrains.idea.reposearch.RepositoryArtifactData;

import java.util.function.Consumer;


/**
 * This class is used as a solution to support completion from repositories, which do not support online completion
 */
public class IndexBasedCompletionProvider implements DependencySearchProvider {

  private final MavenIndex myIndex;
  private final MavenDependencyCompletionItem.Type resultingType;

  public IndexBasedCompletionProvider(@NotNull MavenIndex index) {
    myIndex = index;
    resultingType = myIndex.getKind() == MavenSearchIndex.Kind.LOCAL
                    ? MavenDependencyCompletionItem.Type.LOCAL
                    : MavenDependencyCompletionItem.Type.REMOTE;
  }


  @Override
  public void fulltextSearch(@NotNull String searchString, @NotNull Consumer<RepositoryArtifactData> consumer) {
    MavenId mavenId = new MavenId(searchString);
    search(consumer, mavenId);
  }

  @Override
  public void suggestPrefix(@Nullable String groupId, @Nullable String artifactId, @NotNull Consumer<RepositoryArtifactData> consumer) {
    search(consumer, new MavenId(groupId, artifactId, null));
  }

  private void search(@NotNull Consumer<RepositoryArtifactData> consumer, MavenId mavenId) {
    for (String groupId : myIndex.getGroupIds()) {
      if (mavenId.getGroupId() != null && !mavenId.getGroupId().isEmpty() && !StringUtil.startsWith(groupId, mavenId.getGroupId())) {
        continue;
      }
      for (String artifactId : myIndex.getArtifactIds(groupId)) {
        if (mavenId.getArtifactId() != null &&
            !mavenId.getArtifactId().isEmpty() &&
            !StringUtil.startsWith(artifactId, mavenId.getArtifactId())) {
          continue;
        }
        MavenRepositoryArtifactInfo info = new MavenRepositoryArtifactInfo(groupId, artifactId, myIndex.getVersions(groupId, artifactId));
        consumer.accept(info);
      }
    }
  }

  @Override
  public boolean isLocal() {
    return true;
  }

  public MavenSearchIndex getIndex() {
    return myIndex;
  }
}
