// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.onlinecompletion;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.indices.MavenIndex;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.reposearch.DependencySearchProvider;
import org.jetbrains.idea.reposearch.DependencySearchProvidersFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MavenCompletionProviderFactory implements DependencySearchProvidersFactory {
  @Override
  public @NotNull List<DependencySearchProvider> getProviders(@NotNull Project project) {
    if (!MavenIndicesManager.getInstance(project).isInit()) {
      return Collections.emptyList();
    }

    List<DependencySearchProvider> result = new ArrayList<>();
    result.add(new ProjectModulesCompletionProvider(project));

    addLocalIndex(project, result);
    addRemoteIndices(project, result);

    return result;
  }

  private static void addRemoteIndices(Project project, List<DependencySearchProvider> result) {
    List<MavenIndex> remoteIndices = MavenIndicesManager.getInstance(project).getIndex().getRemoteIndices();
    for (MavenIndex index : remoteIndices) {
      if (!index.getRepositoryId().toLowerCase(Locale.ROOT).contains("central")
          && !index.getRepositoryPathOrUrl().toLowerCase(Locale.ROOT).contains("repo.maven.apache.org/maven2")) {
        result.add(new IndexBasedCompletionProvider(index));
      }
    }
  }

  private static void addLocalIndex(Project project, List<DependencySearchProvider> result) {
    MavenIndex localIndex = MavenIndicesManager.getInstance(project).getIndex().getLocalIndex();
    if (localIndex != null) {
      result.add(new IndexBasedCompletionProvider(localIndex));
    }
  }
}
