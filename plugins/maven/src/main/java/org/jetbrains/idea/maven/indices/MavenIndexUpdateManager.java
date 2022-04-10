// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenRehighlighter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Internal api class for update maven indices state.
 *
 * Contains logic for schedule async tasks for update index list or index.
 */
@ApiStatus.Internal
public final class MavenIndexUpdateManager implements Disposable {
  private final Object myUpdatingIndicesLock = new Object();
  private final List<String> myWaitingIndicesUrl = new ArrayList<>();
  private final BackgroundTaskQueue myUpdatingQueue = new BackgroundTaskQueue(null, IndicesBundle.message("maven.indices.updating"));
  private final MergingUpdateQueue myUpdateQueueList = new MergingUpdateQueue(
    getClass().getName(), 1000, true, null, this, null, false
  ).usePassThroughInUnitTestMode();
  private volatile String myCurrentUpdateIndexUrl;

  CompletableFuture<?> scheduleUpdateContent(@NotNull Project project, List<String> indicesUrl) {
    return scheduleUpdateContent(project, indicesUrl, true);
  }

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
        if (localIndex.getUpdateTimestamp() == -1) {
          scheduleUpdateContent(project, List.of(localIndex.getRepositoryPathOrUrl()));
        }
      }
      if (consumer != null) {
        consumer.consume(indexHolder.getIndices());
      }
    }));
  }

  CompletableFuture<?> scheduleUpdateContent(@NotNull Project project, List<String> indicesUrls, final boolean fullUpdate) {
    final List<String> toSchedule = new ArrayList<>();

    synchronized (myUpdatingIndicesLock) {
      for (String each : indicesUrls) {
        if (myWaitingIndicesUrl.contains(each)) continue;
        toSchedule.add(each);
      }

      myWaitingIndicesUrl.addAll(toSchedule);
    }
    if (toSchedule.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    final CompletableFuture<?> promise = new CompletableFuture<>();
    myUpdatingQueue.run(new Task.Backgroundable(project, IndicesBundle.message("maven.indices.updating"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          indicator.setIndeterminate(false);
          doUpdateIndicesContent(project, toSchedule, fullUpdate, new MavenProgressIndicator(project, indicator, null));
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

  private void doUpdateIndicesContent(@NotNull Project project,
                                      @NotNull List<String> indicesUrl,
                                      boolean fullUpdate,
                                      @NotNull MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {

    List<MavenIndex> indices = ContainerUtil.filter(
      MavenIndicesManager.getInstance(project).getIndex().getIndices(),
      index -> indicesUrl.contains(index.getRepositoryPathOrUrl())
    );

    List<String> remainingWaitingUrl = new ArrayList<>(indicesUrl);

    try {
      for (MavenSearchIndex each : indices) {
        if (indicator.isCanceled()) return;

        indicator.setText(IndicesBundle.message("maven.indices.updating.index",
                                                each.getRepositoryId(),
                                                each.getRepositoryPathOrUrl()));

        synchronized (myUpdatingIndicesLock) {
          remainingWaitingUrl.remove(each.getRepositoryPathOrUrl());
          myWaitingIndicesUrl.remove(each.getRepositoryPathOrUrl());
          myCurrentUpdateIndexUrl = each.getRepositoryPathOrUrl();
        }

        try {
          MavenIndices.updateOrRepair(each, fullUpdate, fullUpdate ? getMavenSettings(project, indicator) : null, indicator);
          MavenRehighlighter.rehighlight(project);
        }
        finally {
          synchronized (myUpdatingIndicesLock) {
            myCurrentUpdateIndexUrl = null;
          }
        }
      }
    }
    finally {
      synchronized (myUpdatingIndicesLock) {
        myWaitingIndicesUrl.removeAll(remainingWaitingUrl);
      }
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

  IndexUpdatingState getUpdatingState(@NotNull MavenSearchIndex index) {
    synchronized (myUpdatingIndicesLock) {
      if (Objects.equals(myCurrentUpdateIndexUrl, index.getRepositoryPathOrUrl())) return IndexUpdatingState.UPDATING;
      if (myWaitingIndicesUrl.contains(index.getRepositoryPathOrUrl())) return IndexUpdatingState.WAITING;
      return IndexUpdatingState.IDLE;
    }
  }

  public enum IndexUpdatingState {
    IDLE, WAITING, UPDATING
  }
}
