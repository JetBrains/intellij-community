// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.model.RepositoryKind;

import java.nio.file.Path;
import java.util.Set;

public interface MavenSearchIndex extends MavenRepositoryIndex {

  @Topic.AppLevel
  Topic<IndexListener> INDEX_IS_BROKEN =
    new Topic<>("Maven Index Broken Listener", IndexListener.class);

  default @NlsSafe String getRepositoryId() {
    return getRepository().getId();
  }

  default @Nullable Path getRepositoryFile() {
    MavenRepositoryInfo repository = getRepository();
    if (repository.getKind() == RepositoryKind.LOCAL) {
      return Path.of(repository.getUrl());
    }
    return null;
  }

  default @NlsSafe String getRepositoryUrl() {
    MavenRepositoryInfo repository = getRepository();
    if (repository.getKind() == RepositoryKind.REMOTE) {
      return repository.getUrl();
    }
    return null;
  }

  default @NlsSafe String getRepositoryPathOrUrl() {
    return getRepository().getUrl();
  }

  @NlsSafe
  String getFailureMessage();

  Set<MavenArtifactInfo> search(String pattern, int maxResult);

  interface IndexListener {
    void indexIsBroken(@NotNull MavenSearchIndex index);
  }
}
