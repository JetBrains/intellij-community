// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PathKt;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.server.MavenServerConnector;
import org.jetbrains.idea.maven.server.MavenServerDownloadListener;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.statistics.MavenIndexUsageCollector;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.reposearch.DependencySearchService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Main api class for work with maven indices.
 * <p>
 * Get current index state, schedule update index list, check MavenId in index, add data to index.
 */
public final class MavenIndicesManager implements Disposable {

  @Topic.AppLevel
  public static final Topic<MavenIndexerListener> INDEXER_TOPIC =
    new Topic<>(MavenIndexerListener.class.getSimpleName(), MavenIndexerListener.class);

  public interface MavenIndexerListener {
    void indexUpdated(Set<File> added, Set<File> failedToAdd);
  }

  private final @NotNull Project myProject;
  private final @NotNull MavenIndices myMavenIndices;

  private final MavenIndexServerDownloadListener myDownloadListener = new MavenIndexServerDownloadListener(this);
  private final IndexFixer myIndexFixer = new IndexFixer();
  private final MavenIndexUpdateManager myIndexUpdateManager;


  public static MavenIndicesManager getInstance(@NotNull Project project) {
    return project.getService(MavenIndicesManager.class);
  }

  public MavenIndicesManager(@NotNull Project project) {
    myProject = project;
    myIndexUpdateManager = new MavenIndexUpdateManager();
    myMavenIndices = MavenSystemIndicesManager.getInstance().getOrCreateIndices(project);

    initListeners();

    Disposer.register(this, myIndexUpdateManager);
  }

  @Override
  public void dispose() {
    myIndexFixer.stop();
    deleteIndicesDirInUnitTests();
  }

  private void deleteIndicesDirInUnitTests() {
    if (MavenUtil.isMavenUnitTestModeEnabled()) {
      if (!myMavenIndices.isDisposed()) {
        var localIndex = myMavenIndices.getIndexHolder().getLocalIndex();
        if (localIndex instanceof MavenIndexImpl impl) {
          impl.closeAndClean();
        }
      }
      Path dir = MavenSystemIndicesManager.getInstance().getIndicesDir();
      try {
        PathKt.delete(dir);
      }
      catch (Exception e) {
        // if some files haven't been deleted in the index directory, report them
        try (Stream<Path> stream = Files.walk(dir)) {
          var files = stream.map(Path::toString).toList();
          var message = files.isEmpty()
                        ? "Failed to delete the index directory"
                        : "Failed to delete files in the index directory: " + String.join(", ", files);
          throw new RuntimeException(message, e);
        }
        catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }
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
    myMavenIndices.updateRepositoriesList();
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
        public void projectsUpdated(List<? extends Pair<MavenProject, MavenProjectChanges>> updated, List<? extends MavenProject> deleted) {
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

  public static void addArchetype(@NotNull MavenArchetype archetype) {
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
   */
  public boolean scheduleArtifactIndexing(@Nullable MavenId mavenId, @NotNull File artifactFile) {

    if (myMavenIndices.isNotInit()) return false;
    try {
      MavenIndex localIndex = myMavenIndices.getIndexHolder().getLocalIndex();
      if (localIndex == null) {
        return false;
      }
      if (mavenId != null) {
        if (mavenId.getGroupId() == null || mavenId.getArtifactId() == null || mavenId.getVersion() == null) {
          return false;
        }
        if (localIndex.hasVersion(mavenId.getGroupId(), mavenId.getArtifactId(), mavenId.getVersion())) return false;
      }

      AppExecutorUtil.getAppExecutorService().execute(() -> {
        myIndexFixer.fixIndex(artifactFile);
      });
    }
    catch (AlreadyDisposedException ignore) {
      return false;
    }
    return true;
  }

  /**
   * Schedule update all indices content async.
   */
  public void scheduleUpdateContentAll() {
    myIndexUpdateManager.scheduleUpdateContent(myProject,
                                               IndicesContentUpdateRequest.explicit(
                                                 ContainerUtil.map(myMavenIndices.getIndices(), MavenIndex::getRepository)));
  }

  /**
   * Schedule update indices content async.
   */
  public CompletableFuture<?> scheduleUpdateContent(@NotNull List<MavenIndex> indices, boolean explicit) {
    IndicesContentUpdateRequest request =
      new IndicesContentUpdateRequest(ContainerUtil.map(indices, MavenIndex::getRepository), explicit, true, explicit);
    return myIndexUpdateManager.scheduleUpdateContent(myProject, request);
  }

  /**
   * Schedule update indices list {@link MavenIndices} async.
   *
   * @param consumer - consumer for new indices.
   */
  public void scheduleUpdateIndicesList(@Nullable Consumer<? super List<MavenIndex>> consumer) {
    myIndexUpdateManager.scheduleUpdateIndicesList(myProject, consumer);
  }

  @NotNull
  public Set<MavenArtifactInfo> searchForClass(String patternForQuery) {
    return this.getIndex()
      .getIndices().stream()
      .flatMap(i -> i.search(patternForQuery, 50).stream())
      .collect(Collectors.toSet());
  }


  @NotNull
  private static Path getUserArchetypesFile() {
    return MavenSystemIndicesManager.getInstance().getIndicesDir().resolve("UserArchetypes.xml");
  }

  private final class IndexFixer {
    private final ConcurrentLinkedQueue<File> queueToAdd = new ConcurrentLinkedQueue<>();
    private final MergingUpdateQueue myMergingUpdateQueue;
    private final AddToIndexRunnable taskConsumer = new AddToIndexRunnable();
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private IndexFixer() {
      myMergingUpdateQueue = new MergingUpdateQueue(
        this.getClass().getName(), 1000, true, MergingUpdateQueue.ANY_COMPONENT, MavenIndicesManager.this, null, false
      ).usePassThroughInUnitTestMode();
    }

    public void fixIndex(@NotNull File file) {
      MavenIndexUsageCollector.ADD_ARTIFACT_FROM_POM.log(myProject);
      if (stopped.get()) return;

      queueToAdd.add(file);
      myMergingUpdateQueue.queue(new Update(this) {
        @Override
        public void run() {
          taskConsumer.run();
        }

        @Override
        public boolean isDisposed() {
          return myMavenIndices.isDisposed();
        }
      });
    }

    public void stop() {
      stopped.set(true);
    }

    private class AddToIndexRunnable implements Runnable {

      @Override
      public void run() {
        MavenIndex localIndex = myMavenIndices.getIndexHolder().getLocalIndex();
        if (localIndex == null) return;

        Set<File> addedFiles = new TreeSet<>();
        Set<File> failedToAddFiles = new TreeSet<>();

        synchronized (queueToAdd) {
          if (stopped.get()) return;
          if (queueToAdd.isEmpty()) return;

          Set<File> filesToAddNow = new TreeSet<>();
          File elementToAdd;
          while ((elementToAdd = queueToAdd.poll()) != null) {
            filesToAddNow.add(elementToAdd);
          }
          if (filesToAddNow.isEmpty()) return;

          Set<File> retryElements = new TreeSet<>();
          var addArtifactResponses = localIndex.tryAddArtifacts(filesToAddNow);
          for (var addArtifactResponse : addArtifactResponses) {
            var file = addArtifactResponse.artifactFile();
            var added = addArtifactResponse.indexedMavenId() != null;
            if (added) {
              addedFiles.add(file);
            }
            else {
              retryElements.add(file);
            }
          }

          if (!retryElements.isEmpty()) {
            if (retryElements.size() < 10_000) {
              queueToAdd.addAll(retryElements);
            }
            else {
              MavenLog.LOG.error("Failed to index artifacts: " + retryElements.size());
              failedToAddFiles.addAll(retryElements);
            }
          }
        }

        fireUpdated(addedFiles, failedToAddFiles);
      }

      private void fireUpdated(Set<File> added, Set<File> failedToAdd) {
        if (stopped.get()) return;

        if (!added.isEmpty() || !failedToAdd.isEmpty()) {
          ApplicationManager.getApplication().getMessageBus().syncPublisher(INDEXER_TOPIC).indexUpdated(added, failedToAdd);
        }
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
      myManager.scheduleArtifactIndexing(null, file);
    }
  }

  private static class MavenSearchIndexListener implements MavenSearchIndex.IndexListener {
    private final MavenIndicesManager myManager;

    private MavenSearchIndexListener(MavenIndicesManager manager) {
      myManager = manager;
    }

    @Override
    public void indexIsBroken(@NotNull MavenSearchIndex index) {
      if (index instanceof MavenUpdatableIndex) {
        myManager.myIndexUpdateManager.scheduleUpdateContent(myManager.myProject,
                                                             IndicesContentUpdateRequest.explicit(List.of(index.getRepository())));
      }
    }
  }

  @TestOnly
  public void waitForBackgroundTasksInTests() {
    myIndexUpdateManager.waitForBackgroundTasksInTests();
  }
}
