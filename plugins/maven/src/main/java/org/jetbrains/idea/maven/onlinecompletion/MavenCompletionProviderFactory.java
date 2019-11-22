// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.indices.MavenIndex;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.indices.MavenRepositoryProvider;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.File;
import java.util.*;

public class MavenCompletionProviderFactory implements DependencyCompletionProviderFactory {

  @Override
  public boolean isApplicable(Project project) {
    return MavenProjectsManager.getInstance(project).isMavenizedProject();
  }

  @NotNull
  @Override
  public List<DependencyCompletionProvider> getProviders(Project project) {
    List<DependencyCompletionProvider> result = new ArrayList<>();
    result.add(new ProjectModulesCompletionProvider(project));

    addLocalIndex(project, result);
    addRemoteIndices(project, result);

    return result;
  }

  private static void addRemoteIndices(Project project, List<DependencyCompletionProvider> result) {
    List<MavenIndex> privateIndices =
      MavenIndicesManager.getInstance().ensureIndicesExist(project, collectRemoteRepositoriesIdsAndUrls(project));

    for (MavenIndex index : privateIndices) {
      result.add(new IndexBasedCompletionProvider(index));
    }
  }

  private static void addLocalIndex(Project project, List<DependencyCompletionProvider> result) {
    File localRepository = ReadAction
      .compute(() -> project.isDisposed() ? null : MavenProjectsManager.getInstance(project).getLocalRepository());
    MavenIndicesManager indicesManager = MavenIndicesManager.getInstance();
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
