// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenRehighlighter;
import org.jetbrains.idea.reposearch.DependencySearchService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenIndexUpdateManager implements Disposable {

  private final Object myUpdatingIndicesLock = new Object();
  private final List<MavenSearchIndex> myWaitingIndices = new ArrayList<>();
  private final BackgroundTaskQueue myUpdatingQueue = new BackgroundTaskQueue(null, IndicesBundle.message("maven.indices.updating"));
  private final MergingUpdateQueue myUpdateQueueList = new MergingUpdateQueue(
    getClass().getName(), 1000, true, MergingUpdateQueue.ANY_COMPONENT, this, null, false
  ).usePassThroughInUnitTestMode();
  private volatile MavenSearchIndex myUpdatingIndex;

  public Promise<Void> scheduleUpdateContent(@Nullable Project project, List<MavenIndex> indices) {
    return scheduleUpdateContent(project, indices, true);
  }

  @Override
  public void dispose() {
    myUpdatingQueue.clear();
  }

  Promise<Void> scheduleUpdateContent(@Nullable Project project, List<MavenIndex> indices, final boolean fullUpdate) {
    final List<MavenSearchIndex> toSchedule = new ArrayList<>();

    synchronized (myUpdatingIndicesLock) {
      for (MavenSearchIndex each : indices) {
        if (myWaitingIndices.contains(each)) continue;
        toSchedule.add(each);
      }

      myWaitingIndices.addAll(toSchedule);
    }
    if (toSchedule.isEmpty()) {
      return Promises.resolvedPromise();
    }

    final AsyncPromise<Void> promise = new AsyncPromise<>();
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
          promise.setResult(null);
        }
      }
    });
    return promise;
  }

  public void scheduleUpdateIndicesList(@NotNull Project project, @Nullable Consumer<? super List<MavenIndex>> consumer) {
    myUpdateQueueList.queue(Update.create(this, () -> {
      MavenIndicesManager indicesManager = MavenIndicesManager.getInstance(project);
      indicesManager.updateIndicesListSync();
      MavenIndexHolder indexHolder = indicesManager.getIndex();

      DependencySearchService.getInstance(project).updateProviders();
      MavenIndex localIndex = indexHolder.getLocalIndex();
      if (localIndex != null) {
        if (localIndex.getUpdateTimestamp() == -1) {
          scheduleUpdateContent(project, Collections.singletonList(localIndex));
        }
      }
      if (consumer != null) {
        consumer.consume(indexHolder.getIndices());
      }
    }));
  }

  private void doUpdateIndicesContent(final Project projectOrNull,
                                      List<MavenSearchIndex> indices,
                                      boolean fullUpdate,
                                      MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    MavenLog.LOG.assertTrue(!fullUpdate || projectOrNull != null);

    List<MavenSearchIndex> remainingWaiting = new ArrayList<>(indices);

    try {
      for (MavenSearchIndex each : indices) {
        if (indicator.isCanceled()) return;

        indicator.setText(IndicesBundle.message("maven.indices.updating.index",
                                                each.getRepositoryId(),
                                                each.getRepositoryPathOrUrl()));

        synchronized (myUpdatingIndicesLock) {
          remainingWaiting.remove(each);
          myWaitingIndices.remove(each);
          myUpdatingIndex = each;
        }

        try {
          MavenIndices.updateOrRepair(each, fullUpdate, fullUpdate ? getMavenSettings(projectOrNull, indicator) : null, indicator);
          if (projectOrNull != null) {
            MavenRehighlighter.rehighlight(projectOrNull);
          }
        }
        finally {
          synchronized (myUpdatingIndicesLock) {
            myUpdatingIndex = null;
          }
        }
      }
    }
    finally {
      synchronized (myUpdatingIndicesLock) {
        myWaitingIndices.removeAll(remainingWaiting);
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

  public IndexUpdatingState getUpdatingState(@NotNull MavenSearchIndex index) {
    synchronized (myUpdatingIndicesLock) {
      if (myUpdatingIndex == index) return IndexUpdatingState.UPDATING;
      if (myWaitingIndices.contains(index)) return IndexUpdatingState.WAITING;
      return IndexUpdatingState.IDLE;
    }
  }

  public enum IndexUpdatingState {
    IDLE, WAITING, UPDATING
  }
}
