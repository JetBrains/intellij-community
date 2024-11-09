/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.model.RepositoryKind;
import org.jetbrains.idea.maven.server.MavenIndexerWrapper;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.reposearch.DependencySearchService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Index data class - present index state.
 * Contains index list and knows how to update it.
 */
@ApiStatus.Internal
public class MavenIndices implements Disposable {
  public static final String LOCAL_REPOSITORY_ID = "local";
  private static final Object ourDirectoryLock = new Object();

  private final MavenIndexerWrapper myIndexer;
  private final File myIndicesDir;
  private final Project myProject;

  private volatile @NotNull MavenIndexHolder myIndexHolder = new MavenIndexHolder(Collections.emptyList(), null);
  private volatile boolean indicesInit;
  private volatile boolean isDisposed;

  private final ReentrantLock updateIndicesLock = new ReentrantLock();

  public MavenIndices(MavenIndexerWrapper indexer, File indicesDir, Project project) {
    myIndexer = indexer;
    myIndicesDir = indicesDir;
    myProject = project;
  }


  public boolean isIndicesInit() {
    return indicesInit;
  }

  public boolean isNotInit() {
    return !indicesInit;
  }

  private static List<MavenIndex> getOldIndices(@NotNull RepositoryDiff<MavenIndex> localDiff,
                                                @NotNull RepositoryDiff<List<MavenIndex>> remoteDiff) {
    List<MavenIndex> oldIndices = new ArrayList<>(remoteDiff.oldIndices);
    if (localDiff.oldIndices != null) oldIndices.add(localDiff.oldIndices);
    return oldIndices;
  }

  @NotNull
  private static List<MavenIndexUtils.IndexPropertyHolder> readCurrentIndexFileProperty(@NotNull Path indicesDir) {
    Path[] indices = null;
    try {
      indices = Files.list(indicesDir).toArray(Path[]::new);
    }
    catch (IOException e) {
      return Collections.emptyList();
    }
    Arrays.sort(indices);

    ArrayList<MavenIndexUtils.IndexPropertyHolder> result = new ArrayList<>();
    for (Path each : indices) {
      if (!Files.isDirectory(each)) continue;

      try {
        MavenIndexUtils.IndexPropertyHolder propertyHolder = MavenIndexUtils.readIndexProperty(each);
        result.add(propertyHolder);
      }
      catch (Exception e) {
        try {
          Files.delete(each);
        }
        catch (IOException ex) {
          MavenLog.LOG.warn(ex);
        }
        MavenLog.LOG.warn(e);
      }
    }
    return result;
  }

  public List<MavenIndex> getIndices() {
    return myIndexHolder.getIndices();
  }

  @NotNull
  public MavenIndexHolder getIndexHolder() {
    if (isDisposed) {
      throw new AlreadyDisposedException("Index was already disposed");
    }
    return myIndexHolder;
  }

  @NotNull
  private static Path createNewIndexDir(Path parent) {
    return createNewDir(parent, "Index", 1000);
  }

  @NotNull
  static Path createNewDir(Path parent, String prefix, int max) {
    synchronized (ourDirectoryLock) {
      for (int i = 0; i < max; i++) {
        String name = prefix + i;
        Path f = parent.resolve(name);
        if (!Files.exists(f)) {
          boolean createSuccessFull;
          try {
            Files.createDirectories(f);
            createSuccessFull = true;
          }
          catch (IOException e) {
            createSuccessFull = false;
          }
          if (createSuccessFull) {
            return f;
          }
        }
      }
      throw new RuntimeException("No available dir found");
    }
  }

  @Override
  public void dispose() {
    isDisposed = true;
    closeIndices(myIndexHolder.getIndices());
    myIndexHolder = null;
  }

  public boolean isDisposed() {
    return isDisposed;
  }

  private static void closeIndices(@NotNull List<MavenIndex> indices) {
    List<MavenIndex> list = new ArrayList<>(indices);
    for (MavenIndex each : list) {
      try {
        each.close(false);
      }
      catch (Exception e) {
        MavenLog.LOG.error("indices dispose error", e);
      }
    }
  }

  @VisibleForTesting
  @NotNull
  static RepositoryDiff<MavenIndex> getLocalDiff(@NotNull MavenRepositoryInfo localRepo,
                                                 @NotNull RepositoryDiffContext context,
                                                 @Nullable MavenIndex currentLocalIndex) {
    if (currentLocalIndex != null && FileUtil.pathsEqual(localRepo.getUrl(), currentLocalIndex.getRepository().getUrl())) {
      return new RepositoryDiff<>(currentLocalIndex, null);
    }

    MavenIndex index = createMavenIndex(LOCAL_REPOSITORY_ID, localRepo.getUrl(), RepositoryKind.LOCAL);
    return new RepositoryDiff<>(index, currentLocalIndex);
  }

  @VisibleForTesting
  @NotNull
  static RepositoryDiff<List<MavenIndex>> getRemoteDiff(
    @NotNull Map<String, Set<String>> remoteRepositoryIdsByUrl,
    @NotNull List<MavenIndex> currentRemoteIndex,
    @NotNull RepositoryDiffContext context) {
    Map<String, MavenIndex> currentRemoteIndicesByUrls = currentRemoteIndex.stream()
      .collect(Collectors.toMap(i -> i.getRepository().getUrl(), Function.identity()));
    if (currentRemoteIndicesByUrls.keySet().equals(remoteRepositoryIdsByUrl.keySet())) {
      return new RepositoryDiff<>(currentRemoteIndex, Collections.emptyList());
    }

    List<MavenIndex> oldIndices = ContainerUtil
      .filter(currentRemoteIndex, i -> !remoteRepositoryIdsByUrl.containsKey(i.getRepository().getUrl()));

    List<MavenIndex> newMavenIndices = ContainerUtil.map(remoteRepositoryIdsByUrl
                                                           .entrySet(), e -> {
      MavenIndex oldIndex = currentRemoteIndicesByUrls.get(e.getKey());
      if (oldIndex != null) return oldIndex;
      String id = e.getValue().iterator().next();
      return createMavenIndex(id, e.getKey(), RepositoryKind.REMOTE);
    });

    return new RepositoryDiff<>(newMavenIndices, oldIndices);
  }


  private static void clearDependencySearchCache(@NotNull Project project) {
    try {
      DependencySearchService.getInstance(project).clearCache();
    }
    catch (AlreadyDisposedException ignored) {
    }
  }


  @NotNull
  private static MavenIndex createMavenIndex(@NotNull String id, @NotNull String repositoryPathOrUrl, RepositoryKind repositoryKind) {
    throw new UnsupportedOperationException();
  }

  static class RepositoryDiff<T> {
    final T newIndices;
    final T oldIndices;

    private RepositoryDiff(T newIndices,
                           T oldIndices) {
      this.newIndices = newIndices;
      this.oldIndices = oldIndices;
    }
  }


  //todo: move this logic and this file into MavenIndicesManager

  static class UpdateRepositoryResult {
    List<MavenIndex> mavenIndexreated;
  }

  static class RepositoryDiffContext {
    final @NotNull MavenIndexerWrapper indexer;
    final @NotNull File indicesDir;
    @Nullable List<MavenIndexUtils.IndexPropertyHolder> indexPropertyHolders;

    RepositoryDiffContext(@NotNull MavenIndexerWrapper indexer, @NotNull File dir) {
      this.indexer = indexer;
      indicesDir = dir;
    }
  }
}