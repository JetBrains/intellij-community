/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.utils.library;

import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.jarRepository.RepositoryArtifactDescription;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.util.Pair;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is deprecated and is to be removed in future releases
 * Use {@link JarRepositoryManager} instead
 */
@Deprecated
public class RepositoryAttachHandler {

  @Nullable
  public static NewLibraryConfiguration chooseLibraryAndDownload(final @NotNull Project project, final @Nullable String initialFilter, JComponent parentComponent) {
    return JarRepositoryManager.chooseLibraryAndDownload(project, initialFilter, parentComponent);
  }

  @Nullable
  public static NewLibraryConfiguration resolveAndDownload(final Project project,
                                                           final String coord,
                                                           boolean attachJavaDoc,
                                                           boolean attachSources,
                                                           @Nullable final String copyTo,
                                                           List<MavenRepositoryInfo> repositories) {
    final ArrayList<RemoteRepositoryDescription> repos =
      repositories.stream().map(info -> toRemoteRepositoryDescription(info)).collect(Collectors.toCollection(ArrayList::new));
    return JarRepositoryManager.resolveAndDownload(project, coord, attachSources, attachJavaDoc, true, copyTo, repos);
  }

  @NotNull
  public static List<OrderRoot> resolveAndDownloadImpl(final Project project,
                                         final String coord,
                                         boolean attachJavaDoc,
                                         boolean attachSources,
                                         @Nullable final String copyTo,
                                         List<MavenRepositoryInfo> repositories,
                                         ProgressIndicator indicator) {
    final ArrayList<RemoteRepositoryDescription> repos =
      repositories.stream().map(info -> toRemoteRepositoryDescription(info)).collect(Collectors.toCollection(ArrayList::new));
    return new ArrayList<>(JarRepositoryManager.loadDependencies(
      project, new RepositoryLibraryProperties(coord, true), attachSources, attachJavaDoc, copyTo, repos
    ));
  }

  public static void searchArtifacts(final Project project, String coord, final PairProcessor<Collection<Pair<MavenArtifactInfo, MavenRepositoryInfo>>, Boolean> resultProcessor) {
    JarRepositoryManager.searchArtifacts(
      project, coord, (pairs) -> resultProcessor.process(convert(pairs), Boolean.FALSE)
    );
  }

  public static void searchRepositories(final Project project, final Collection<String> nexusUrls, final Processor<Collection<MavenRepositoryInfo>> resultProcessor) {
    JarRepositoryManager.searchRepositories(
      project, nexusUrls, descriptions -> resultProcessor.process(convertRepositoryList(descriptions))
    );
  }

  public static MavenId getMavenId(@NotNull String coord) {
    final String[] parts = coord.split(":");
    return new MavenId(parts.length > 0 ? parts[0] : null,
                       parts.length > 1 ? parts[1] : null,
                       parts.length > 2 ? parts[2] : null);
  }


  private static Collection<Pair<MavenArtifactInfo, MavenRepositoryInfo>> convert(Collection<Pair<RepositoryArtifactDescription, RemoteRepositoryDescription>> in) {
    return in.stream().map(p -> Pair.create(toMavenArtifactInfo(p.first), toMavenRepositoryInfo(p.second))).collect(Collectors.toCollection(ArrayList::new));
  }

  @NotNull
  private static MavenArtifactInfo toMavenArtifactInfo(RepositoryArtifactDescription ad) {
    return new MavenArtifactInfo(
      ad.getGroupId(), ad.getArtifactId(), ad.getVersion(), ad.getPackaging(), ad.getClassifier(), ad.getClassNames(), ad.getRepositoryId()
    );
  }

  @NotNull
  private static MavenRepositoryInfo toMavenRepositoryInfo(RemoteRepositoryDescription repo) {
    return new MavenRepositoryInfo(repo.getId(), repo.getName(), repo.getUrl());
  }
  @NotNull
  private static RemoteRepositoryDescription toRemoteRepositoryDescription(MavenRepositoryInfo repo) {
    return new RemoteRepositoryDescription(repo.getId(), repo.getName(), repo.getUrl());
  }

  @NotNull
  private static Collection<MavenRepositoryInfo> convertRepositoryList(Collection<RemoteRepositoryDescription> repos) {
    if (repos.isEmpty()) {
      return Collections.emptyList();
    }
    Collection<MavenRepositoryInfo> result = new ArrayList<>();
    for (RemoteRepositoryDescription description : repos) {
      result.add(toMavenRepositoryInfo(description));
    }
    return result;
  }
}
