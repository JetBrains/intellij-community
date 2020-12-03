// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.indices.MavenIndex;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.indices.MavenRepositoryProvider;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.kpmsearch.PackageSearchService;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.reposearch.DependencySearchProvider;
import org.jetbrains.idea.reposearch.DependencySearchProvidersFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MavenCompletionProviderFactory implements DependencySearchProvidersFactory {

  @Override
  public boolean isApplicable(Project project) {
    return MavenProjectsManager.getInstance(project).isMavenizedProject();
  }

  @NotNull
  @Override
  public List<DependencySearchProvider> getProviders(Project project) {
    List<DependencySearchProvider> result = new ArrayList<>();
    result.add(new ProjectModulesCompletionProvider(project));

    addLocalIndex(project, result);
    addRemoteIndices(project, result);

    return result;
  }

  private static void addRemoteIndices(Project project, List<DependencySearchProvider> result) {
    List<MavenIndex> privateIndices =
      MavenIndicesManager.getInstance(project).ensureIndicesExist(collectRemoteRepositoriesIdsAndUrls(project));

    for (MavenIndex index : privateIndices) {
      result.add(new IndexBasedCompletionProvider(index));
    }
  }

  private static void addLocalIndex(Project project, List<DependencySearchProvider> result) {
    File localRepository = ReadAction
      .compute(() -> project.isDisposed() ? null : MavenProjectsManager.getInstance(project).getLocalRepository());
    MavenIndicesManager indicesManager = MavenIndicesManager.getInstance(project);
    MavenIndex localIndex = indicesManager.createIndexForLocalRepo(project, localRepository);
    if (localIndex != null) {
      result.add(new IndexBasedCompletionProvider(localIndex));
    }
  }

  private static Set<Pair<String, String>> collectRemoteRepositoriesIdsAndUrls(Project project) {
    Set<Pair<String, String>> result = new THashSet<>();
    Set<MavenRemoteRepository> remoteRepositories = new HashSet<>(MavenProjectsManager.getInstance(project).getRemoteRepositories());
    for (MavenRepositoryProvider repositoryProvider : MavenRepositoryProvider.EP_NAME.getExtensions()) {
      remoteRepositories.addAll(repositoryProvider.getRemoteRepositories(project));
    }
    for (MavenRemoteRepository each : remoteRepositories) {
      String id = each.getId();
      String url = each.getUrl();
      if ("central".equals(id) || url.contains("repo.maven.apache.org/maven2")) {
        continue;
      }
      result.add(Pair.create(id, url));
    }
    return result;
  }
}
