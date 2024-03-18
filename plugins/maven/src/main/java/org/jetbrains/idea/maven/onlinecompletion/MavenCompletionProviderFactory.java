// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.onlinecompletion;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.indices.MavenGAVIndex;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
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
    result.add(new IndexBasedCompletionProvider(project));
    return result;
  }
}
