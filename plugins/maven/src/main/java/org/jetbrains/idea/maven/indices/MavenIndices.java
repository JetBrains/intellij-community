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
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.server.MavenIndexerWrapper;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.reposearch.DependencySearchService;

import java.io.File;
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
  private final MavenSearchIndex.IndexListener myListener;

  private volatile @NotNull MavenIndexHolder myIndexHolder = new MavenIndexHolder(Collections.emptyList(), null);
  private volatile boolean indicesInit;
  private volatile boolean isDisposed;

  private final ReentrantLock updateIndicesLock = new ReentrantLock();

  public MavenIndices(MavenIndexerWrapper indexer, File indicesDir, MavenSearchIndex.IndexListener listener) {
    myIndexer = indexer;
    myIndicesDir = indicesDir;
    myListener = listener;
  }

  void updateIndicesList(@NotNull Project project) {
    if (isDisposed) return;
    updateIndicesLock.lock();
    try {
      Map<String, Set<String>> remoteRepositoryIdsByUrl = MavenIndexUtils.getRemoteRepositoryIdsByUrl(project);
      MavenIndexUtils.RepositoryInfo localRepository = MavenIndexUtils.getLocalRepository(project);
      if (localRepository == null || project.isDisposed()) {
        return;
      }

      if (myIndexHolder.isEquals(remoteRepositoryIdsByUrl.keySet(), localRepository.url)) return;

      MavenLog.LOG.debug("start update indices " + myIndexHolder);

      MavenIndex localIndex = myIndexHolder.getLocalIndex();
      List<MavenIndex> remoteIndices = myIndexHolder.getRemoteIndices();

      if (isDisposed) return;
      RepositoryDiffContext context = new RepositoryDiffContext(myIndexer, myListener, myIndicesDir);

      RepositoryDiff<MavenIndex> localDiff = getLocalDiff(localRepository, context, localIndex);
      RepositoryDiff<List<MavenIndex>> remoteDiff = getRemoteDiff(remoteRepositoryIdsByUrl, remoteIndices, context);

      myIndexHolder = new MavenIndexHolder(remoteDiff.newIndices, localDiff.newIndices);
      MavenLog.LOG.debug("new indices " + myIndexHolder);

      if (isDisposed) closeIndices(myIndexHolder.getIndices());

      indicesInit = true;

      closeIndices(getOldIndices(localDiff, remoteDiff));
      updateDependencySearchProviders(project);
    }
    catch (AlreadyDisposedException e) {
      myIndexHolder = new MavenIndexHolder(Collections.emptyList(), null);
    }
    finally {
      updateIndicesLock.unlock();
    }
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
  private static List<MavenIndexUtils.IndexPropertyHolder> readCurrentIndexFileProperty(@NotNull File indicesDir) {
    File[] indices = indicesDir.listFiles();
    if (indices == null) return Collections.emptyList();
    Arrays.sort(indices);

    ArrayList<MavenIndexUtils.IndexPropertyHolder> result = new ArrayList<>();
    for (File each : indices) {
      if (!each.isDirectory()) continue;

      try {
        MavenIndexUtils.IndexPropertyHolder propertyHolder = MavenIndexUtils.readIndexProperty(each);
        result.add(propertyHolder);
      }
      catch (Exception e) {
        FileUtil.delete(each);
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
    return myIndexHolder;
  }

  @NotNull
  private static File createNewIndexDir(File parent) {
    return createNewDir(parent, "Index", 1000);
  }

  @NotNull
  static File createNewDir(File parent, String prefix, int max) {
    synchronized (ourDirectoryLock) {
      for (int i = 0; i < max; i++) {
        String name = prefix + i;
        File f = new File(parent, name);
        if (!f.exists()) {
          boolean createSuccessFull = f.mkdirs();
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
  }

  private static void closeIndices(@NotNull List<MavenIndex> indices) {
    for (MavenIndex each : indices) {
      try {
        each.finalClose(false);
      }
      catch (Exception e) {
        MavenLog.LOG.error("indices dispose error", e);
      }
    }
  }

  public static void updateOrRepair(@NotNull MavenSearchIndex index, boolean fullUpdate,
                                    @Nullable MavenGeneralSettings settings, MavenProgressIndicator progress)
    throws MavenProcessCanceledException {
    index.updateOrRepair(fullUpdate, settings, progress);
  }

  @VisibleForTesting
  @NotNull
  static RepositoryDiff<MavenIndex> getLocalDiff(@NotNull MavenIndexUtils.RepositoryInfo localRepo,
                                                 @NotNull RepositoryDiffContext context,
                                                 @Nullable MavenIndex currentLocalIndex) {
    if (currentLocalIndex != null && FileUtil.pathsEqual(localRepo.url, currentLocalIndex.getRepositoryPathOrUrl())) {
      return new RepositoryDiff<>(currentLocalIndex, null);
    }

    List<MavenIndexUtils.IndexPropertyHolder> indexPropertyHolders = readCurrentIndexFileProperty(context.indicesDir);
    context.indexPropertyHolders = indexPropertyHolders;

    MavenIndex index = indexPropertyHolders.stream()
      .filter(iph -> iph.kind == MavenSearchIndex.Kind.LOCAL && FileUtil.pathsEqual(iph.repositoryPathOrUrl, localRepo.url))
      .findFirst()
      .map(iph -> createMavenIndex(iph, context))
      .orElseGet(() -> {
        MavenIndexUtils.IndexPropertyHolder propertyHolder = new MavenIndexUtils.IndexPropertyHolder(
          createNewIndexDir(context.indicesDir), MavenSearchIndex.Kind.LOCAL, Collections.singleton(LOCAL_REPOSITORY_ID), localRepo.url
        );
        return createMavenIndex(propertyHolder, context);
      });
    if (index == null) return new RepositoryDiff<>(currentLocalIndex, null);

    return new RepositoryDiff<>(index, currentLocalIndex);
  }

  @VisibleForTesting
  @NotNull
  static RepositoryDiff<List<MavenIndex>> getRemoteDiff(
    @NotNull Map<String, Set<String>> remoteRepositoryIdsByUrl,
    @NotNull List<MavenIndex> currentRemoteIndex,
    @NotNull RepositoryDiffContext context) {
    Map<String, MavenIndex> currentRemoteIndicesByUrls = currentRemoteIndex.stream()
      .collect(Collectors.toMap(i -> i.getRepositoryPathOrUrl(), Function.identity()));
    if (currentRemoteIndicesByUrls.keySet().equals(remoteRepositoryIdsByUrl.keySet())) {
      return new RepositoryDiff<>(currentRemoteIndex, Collections.emptyList());
    }

    List<MavenIndexUtils.IndexPropertyHolder> indexPropertyHolders = context.indexPropertyHolders;
    indexPropertyHolders = indexPropertyHolders != null ? indexPropertyHolders : readCurrentIndexFileProperty(context.indicesDir);
    Map<String, MavenIndexUtils.IndexPropertyHolder> propertyHolderMapByUrl = indexPropertyHolders.stream()
      .filter(iph -> iph.kind == MavenSearchIndex.Kind.REMOTE)
      .collect(Collectors.toMap(iph -> iph.repositoryPathOrUrl, Function.identity(), (i1, i2) -> i1));

    List<MavenIndex> oldIndices = ContainerUtil
      .filter(currentRemoteIndex, i -> !remoteRepositoryIdsByUrl.containsKey(i.getRepositoryPathOrUrl()));

    List<MavenIndex> newMavenIndices = remoteRepositoryIdsByUrl
      .entrySet()
      .stream()
      .map(e -> createMavenIndex(currentRemoteIndicesByUrls.get(e.getKey()), propertyHolderMapByUrl.get(e.getKey()), e, context))
      .filter(i -> i != null)
      .collect(Collectors.toList());

    return new RepositoryDiff<>(newMavenIndices, oldIndices);
  }

  private static MavenIndex createMavenIndex(@Nullable MavenIndex index,
                                             @Nullable MavenIndexUtils.IndexPropertyHolder propertyHolder,
                                             @NotNull Map.Entry<String, Set<String>> remoteEntry,
                                             @NotNull RepositoryDiffContext context) {
    if (index != null) return index;
    if (propertyHolder != null) {
      index = createMavenIndex(propertyHolder, context);
    }
    if (index != null) return index;

    propertyHolder = new MavenIndexUtils.IndexPropertyHolder(
      createNewIndexDir(context.indicesDir), MavenSearchIndex.Kind.REMOTE, remoteEntry.getValue(), remoteEntry.getKey()
    );
    return createMavenIndex(propertyHolder, context);
  }

  private static void updateDependencySearchProviders(@NotNull Project project) {
    try {
      DependencySearchService.getInstance(project).updateProviders();
    }
    catch (AlreadyDisposedException ignored) {}
  }

  @Nullable
  private static MavenIndex createMavenIndex(@NotNull MavenIndexUtils.IndexPropertyHolder propertyHolder,
                                             @NotNull RepositoryDiffContext context) {
    try {
      return new MavenIndex(context.indexer, propertyHolder, context.listener);
    }
    catch (Exception e) {
      FileUtil.delete(propertyHolder.dir);
      MavenLog.LOG.warn(e);
    }
    return null;
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

  static class RepositoryDiffContext {
    final @NotNull MavenIndexerWrapper indexer;
    final @NotNull MavenSearchIndex.IndexListener listener;
    final @NotNull File indicesDir;
    @Nullable List<MavenIndexUtils.IndexPropertyHolder> indexPropertyHolders;

    RepositoryDiffContext(@NotNull MavenIndexerWrapper indexer,
                          MavenSearchIndex.@NotNull IndexListener listener, @NotNull File dir) {
      this.indexer = indexer;
      this.listener = listener;
      indicesDir = dir;
    }
  }
}