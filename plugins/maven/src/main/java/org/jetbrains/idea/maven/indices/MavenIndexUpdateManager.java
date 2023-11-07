// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;

import com.intellij.model.SideEffectGuard;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenRehighlighter;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Internal api class for update maven indices state.
 * <p>
 * Contains logic for schedule async tasks for update index list or index.
 */
@ApiStatus.Internal
public final class MavenIndexUpdateManager implements Disposable {
  private final Object myUpdatingIndicesLock = new Object();
  private final BackgroundTaskQueue myUpdatingQueue = new BackgroundTaskQueue(null, IndicesBundle.message("maven.indices.updating"));
  private final MergingUpdateQueue myUpdateQueueList = new MergingUpdateQueue(
    getClass().getName(), 1000, true, null, this, null, false
  ).usePassThroughInUnitTestMode();
  private volatile String myCurrentUpdateIndexUrl;

  @Override
  public void dispose() {
    myUpdatingQueue.clear();
  }

  void scheduleUpdateIndicesList(@NotNull Project project, @Nullable Consumer<? super List<MavenIndex>> consumer) {
    myUpdateQueueList.queue(Update.create(this, () -> {
      MavenIndicesManager indicesManager = MavenIndicesManager.getInstance(project);
      indicesManager.updateIndicesListSync();
      if (project.isDisposed()) {
        return;
      }

      MavenIndexHolder indexHolder = indicesManager.getIndex();
      MavenIndex localIndex = indexHolder.getLocalIndex();
      if (localIndex != null) {
        if (localIndex.getUpdateTimestamp() == -1 && Registry.is("maven.auto.update.local.index")) {
          scheduleUpdateContent(project, IndicesContentUpdateRequest.background(List.of(localIndex.getRepository())));
        }
      }
      if (consumer != null) {
        consumer.consume(indexHolder.getIndices());
      }
    }));
  }

  CompletableFuture<?> scheduleUpdateContent(@NotNull Project project, IndicesContentUpdateRequest request) {
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.PROJECT_MODEL);

    if (request.getShowProgress()) {
      return runIndexUpdateWithProgress(project, request);
    }
    else {
      return runIndexUpdateInBackgroundWithoutProgress(project, request);
    }
  }

  private static CompletableFuture<?> runIndexUpdateInBackgroundWithoutProgress(Project project,
                                                                                IndicesContentUpdateRequest request) {
    MavenProgressIndicator indicator =
      new MavenProgressIndicator(null, null);
    try {
      doUpdateIndicesContent(project, request, indicator);
    }
    catch (MavenProcessCanceledException e) {
      return CompletableFuture.failedFuture(e);
    }
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<?> runIndexUpdateWithProgress(@NotNull Project project,
                                                          IndicesContentUpdateRequest request) {
    final CompletableFuture<?> promise = new CompletableFuture<>();
    myUpdatingQueue.run(new Task.Backgroundable(project, IndicesBundle.message("maven.indices.updating"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          indicator.setIndeterminate(false);
          doUpdateIndicesContent(project, request, new MavenProgressIndicator(null, indicator, null));
        }
        catch (MavenProcessCanceledException ignore) {
        }
        finally {
          promise.complete(null);
        }
      }
    });
    return promise;
  }

  private static void doUpdateIndicesContent(@NotNull Project project,
                                             @NotNull IndicesContentUpdateRequest request,
                                             @NotNull MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {


    MavenSystemIndicesManager indicesManager = MavenSystemIndicesManager.getInstance();
    for (MavenRepositoryInfo repo : request.getIndicesToUpdate()) {
      if (indicator.isCanceled()) return;
      indicesManager.updateIndexContentSync(repo, request.getFull(), request.getExplicit(), indicator);

      indicator.setText(IndicesBundle.message("maven.indices.updating.index",
                                              repo.getId(),
                                              repo.getUrl()));
    }
    MavenRehighlighter.rehighlight(project);
  }


  @TestOnly
  void waitForBackgroundTasksInTests() {
    while (!myUpdatingQueue.isEmpty()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    try {
      myUpdateQueueList.waitForAllExecuted(1, TimeUnit.MINUTES);
    }
    catch (ExecutionException | InterruptedException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private static MavenGeneralSettings getMavenSettings(@NotNull final Project project, @NotNull MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    MavenGeneralSettings settings;

    settings = ReadAction
      .compute(() -> project.isDisposed() ? null : MavenProjectsManager.getInstance(project).getGeneralSettings().clone());

    if (settings == null) {
      // project was closed
      indicator.cancel();
      indicator.checkCanceled();
    }

    return settings;
  }

  public enum IndexUpdatingState {
    IDLE, WAITING, UPDATING
  }
}
