// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PathKt;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.server.*;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.reposearch.DependencySearchService;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Main api class for work with maven indices.
 * <p>
 * Get current index state, schedule update index list, check MavenId in index, add data to index.
 */
public final class MavenIndicesManager implements Disposable {
  private final @NotNull Project myProject;
  private final @NotNull MavenIndices myMavenIndices;

  private final MavenIndexServerDownloadListener myDownloadListener = new MavenIndexServerDownloadListener(this);
  private final MavenIndexerWrapper myIndexerWrapper;
  private final IndexFixer myIndexFixer = new IndexFixer();
  private final MavenIndexUpdateManager myIndexUpdateManager;


  public static MavenIndicesManager getInstance(@NotNull Project project) {
    return project.getService(MavenIndicesManager.class);
  }

  public MavenIndicesManager(@NotNull Project project) {
    myProject = project;
    myIndexerWrapper = MavenServerManager.getInstance().createIndexer(myProject);
    myIndexUpdateManager = new MavenIndexUpdateManager();
    myMavenIndices = myIndexerWrapper.getOrCreateIndices();

    initListeners();

    Disposer.register(this, myIndexUpdateManager);
  }

  @Override
  public void dispose() {
    if (MavenUtil.isMavenUnitTestModeEnabled()) {
      PathKt.delete(getIndicesDir());
    }
  }

  /**
   * Get current maven index state.
   * If index do not initialized yet, then run init async and return empty index state.
   *
   * @return MavenIndexHolder
   */
  @NotNull
  public MavenIndexHolder getIndex() {
    if (myMavenIndices.isNotInit() && !ApplicationManager.getApplication().isUnitTestMode()) {
      myIndexUpdateManager.scheduleUpdateIndicesList(myProject, null);
    }
    return myMavenIndices.getIndexHolder();
  }

  void updateIndicesListSync() {
    myMavenIndices.updateIndicesList(myProject);
  }

  public boolean isInit() {
    return myMavenIndices.isIndicesInit();
  }

  private void initListeners() {

    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(MavenServerConnector.DOWNLOAD_LISTENER_TOPIC, myDownloadListener);

    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(MavenIndex.INDEX_IS_BROKEN, new MavenSearchIndexListener(this));

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      MavenProjectsManager.getInstance(myProject).addProjectsTreeListener(new MavenProjectsTree.Listener() {
        @Override
        public void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
          DependencySearchService.getInstance(myProject).clearCache();
        }

        @Override
        public void projectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                    @Nullable NativeMavenProjectHolder nativeMavenProject) {
          DependencySearchService.getInstance(myProject).clearCache();
        }
      }, this);
      return;
    }

    MavenRepositoryProvider.EP_NAME.addChangeListener(() -> scheduleUpdateIndicesList(null), this);
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);

    projectsManager.addProjectsTreeListener(new MavenProjectsTree.Listener() {
      @Override
      public void projectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                  @Nullable NativeMavenProjectHolder nativeMavenProject) {
        scheduleUpdateIndicesList(null);
      }
    }, this);
  }

  @NotNull
  Path getIndicesDir() {
    return MavenIndexerWrapper.getIndicesDir();
  }

  public void addArchetype(@NotNull MavenArchetype archetype) {
    MavenArchetypeManager.addArchetype(archetype, getUserArchetypesFile());
  }

  public boolean hasLocalGroupId(@NotNull String groupId) {
    MavenIndex localIndex = getIndex().getLocalIndex();
    return localIndex != null && localIndex.hasGroupId(groupId);
  }

  public boolean hasLocalArtifactId(@Nullable String groupId, @Nullable String artifactId) {
    MavenIndex localIndex = getIndex().getLocalIndex();
    return localIndex != null && localIndex.hasArtifactId(groupId, artifactId);
  }

  public boolean hasLocalVersion(@Nullable String groupId, @Nullable String artifactId, @Nullable String version) {
    MavenIndex localIndex = getIndex().getLocalIndex();
    return localIndex != null && localIndex.hasVersion(groupId, artifactId, version);
  }

  /**
   * Add artifact info to index async.
   *
   */
  public Promise<Void> addArtifactIndexAsync(@Nullable MavenId mavenId, @NotNull File artifactFile) {
    if (myMavenIndices.isNotInit()) return Promises.rejectedPromise();
    MavenIndex localIndex = myMavenIndices.getIndexHolder().getLocalIndex();
    if (localIndex == null) {
      return Promises.rejectedPromise();
    }
    AsyncPromise<Void> result = new AsyncPromise<>();
    AppExecutorUtil.getAppExecutorService().execute(() -> {
      myIndexFixer.fixIndex(mavenId, artifactFile, localIndex).processed(result);
    });
    return result;
  }

  /**
   * Schedule update all indices content async.
   */
  public void scheduleUpdateContentAll() {
    myIndexUpdateManager.scheduleUpdateContent(myProject,
                                               ContainerUtil.map(myMavenIndices.getIndices(), MavenIndex::getRepositoryPathOrUrl));
  }

  /**
   * Schedule update indices content async.
   */
  public CompletableFuture<?> scheduleUpdateContent(@NotNull List<MavenIndex> indices) {
    return myIndexUpdateManager.scheduleUpdateContent(myProject, ContainerUtil.map(indices, MavenIndex::getRepositoryPathOrUrl));
  }

  /**
   * Schedule update indices list {@link MavenIndices} async.
   *
   * @param consumer - consumer for new indices.
   */
  public void scheduleUpdateIndicesList(@Nullable Consumer<? super List<MavenIndex>> consumer) {
    myIndexUpdateManager.scheduleUpdateIndicesList(myProject, consumer);
  }

  public MavenIndexUpdateManager.IndexUpdatingState getUpdatingState(@NotNull MavenSearchIndex index) {
    return myIndexUpdateManager.getUpdatingState(index);
  }

  @NotNull
  private Path getUserArchetypesFile() {
    return getIndicesDir().resolve("UserArchetypes.xml");
  }

  private final class IndexFixer {
    private final ConcurrentLinkedQueue<Pair<File, AsyncPromise<Void>>> queueToAdd = new ConcurrentLinkedQueue<>();
    private final MergingUpdateQueue myMergingUpdateQueue;
    private final AddToIndexRunnable taskConsumer = new AddToIndexRunnable();

    private IndexFixer() {
      myMergingUpdateQueue = new MergingUpdateQueue(
        this.getClass().getName(), 1000, true, MergingUpdateQueue.ANY_COMPONENT, MavenIndicesManager.this, null, false
      ).usePassThroughInUnitTestMode();
    }

    public Promise<Void> fixIndex(@Nullable MavenId mavenId, @NotNull File file, @NotNull MavenIndex localIndex) {
      if (mavenId != null) {
        if (mavenId.getGroupId() == null || mavenId.getArtifactId() == null || mavenId.getVersion() == null) {
          return Promises.rejectedPromise();
        }
        if (localIndex.hasVersion(mavenId.getGroupId(), mavenId.getArtifactId(), mavenId.getVersion())) return Promises.rejectedPromise();
      }
      AsyncPromise<Void> result = new AsyncPromise<>();

      queueToAdd.add(new Pair<>(file, result));
      myMergingUpdateQueue.queue(new Update(IndexFixer.this) {
        @Override
        public void run() {
          ((Runnable)taskConsumer).run();
        }

        @Override
        public boolean isDisposed() {
          return myMavenIndices.isDisposed();
        }
      });
      return result;
    }

    private class AddToIndexRunnable implements Runnable {

      @Override
      public void run() {
        MavenIndex localIndex = myMavenIndices.getIndexHolder().getLocalIndex();
        if (localIndex == null) return;
        Pair<File, AsyncPromise<Void>> elementToAdd;
        List<Pair<File, AsyncPromise<Void>>> retryElements = new ArrayList<>();
        Set<File> addedFiles = new TreeSet<>();
        while ((elementToAdd = queueToAdd.poll()) != null) {
          if (addedFiles.contains(elementToAdd.first)) {
            elementToAdd.second.setResult(null);
            continue;
          }

          boolean added = localIndex.tryAddArtifact(elementToAdd.first);
          if (added) {
            addedFiles.add(elementToAdd.first);
            elementToAdd.second.setResult(null);
          }
          else {
            retryElements.add(elementToAdd);
          }
        }
        if (!retryElements.isEmpty() && retryElements.size() < 10_000) queueToAdd.addAll(retryElements);
      }
    }
  }

  private static class MavenIndexServerDownloadListener implements MavenServerDownloadListener {
    private final MavenIndicesManager myManager;

    private MavenIndexServerDownloadListener(MavenIndicesManager manager) {
      myManager = manager;
    }

    @Override
    public void artifactDownloaded(File file, String relativePath) {
      myManager.addArtifactIndexAsync(null, file);
    }
  }

  private static class MavenSearchIndexListener implements MavenSearchIndex.IndexListener {
    private final MavenIndicesManager myManager;

    private MavenSearchIndexListener(MavenIndicesManager manager) {
      myManager = manager;
    }

    @Override
    public void indexIsBroken(@NotNull MavenSearchIndex index) {
      if (index instanceof MavenIndex) {
        myManager.myIndexUpdateManager.scheduleUpdateContent(myManager.myProject, List.of(index.getRepositoryPathOrUrl()), false);
      }
    }
  }

  @TestOnly
  public void waitForBackgroundTasksInTests() {
    myIndexUpdateManager.waitForBackgroundTasksInTests();
  }
}
