// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ZipperUpdater;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.integrate.Merger;
import org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED;
import static com.intellij.openapi.vcs.changes.committed.VcsConfigurationChangeListener.BRANCHES_CHANGED;

public class MergeInfoUpdatesListener {
  private static final int DELAY = 300;

  private final Project myProject;
  private final MessageBusConnection myConnection;
  private List<RootsAndBranches> myMergeInfoRefreshActions;
  private final ZipperUpdater myUpdater;

  public MergeInfoUpdatesListener(@NotNull Project project, final MessageBusConnection connection) {
    myConnection = connection;
    myProject = project;
    myUpdater = new ZipperUpdater(DELAY, project);
  }

  public void addPanel(final RootsAndBranches action) {
    if (myMergeInfoRefreshActions == null) {
      myMergeInfoRefreshActions = new ArrayList<>();
      myMergeInfoRefreshActions.add(action);

      myConnection.subscribe(BRANCHES_CHANGED, (project, vcsRoot) -> callReloadMergeInfo());
      final Consumer<Boolean> reloadConsumer = aBoolean -> {
        if (Boolean.TRUE.equals(aBoolean)) {
          callReloadMergeInfo();
        }
      };
      final Runnable reloadRunnable = () -> callReloadMergeInfo();
      myConnection.subscribe(SvnVcs.WC_CONVERTED, reloadRunnable);
      myConnection.subscribe(RootsAndBranches.REFRESH_REQUEST, reloadRunnable);

      myConnection.subscribe(SvnVcs.ROOTS_RELOADED, reloadConsumer);

      myConnection.subscribe(VCS_CONFIGURATION_CHANGED, () -> callReloadMergeInfo());

      myConnection.subscribe(CommittedChangesTreeBrowser.ITEMS_RELOADED, new CommittedChangesTreeBrowser.CommittedChangesReloadListener() {
        @Override
        public void itemsReloaded() {
          reloadRunnable.run();
        }
        @Override
        public void emptyRefresh() {
        }
      });

      myConnection
        .subscribe(SvnMergeInfoCache.SVN_MERGE_INFO_CACHE, () -> doForEachInitialized(rootsAndBranches -> rootsAndBranches.fireRepaint()));

      myConnection.subscribe(Merger.COMMITTED_CHANGES_MERGED_STATE,
                             list -> doForEachInitialized(rootsAndBranches -> rootsAndBranches.refreshByLists(list)));
    } else {
      myMergeInfoRefreshActions.add(action);
    }
  }

  private void doForEachInitialized(final Consumer<RootsAndBranches> consumer) {
    myUpdater.queue(() -> {
      for (final RootsAndBranches action : myMergeInfoRefreshActions) {
        if (action.strategyInitialized()) {
          if (ApplicationManager.getApplication().isDispatchThread()) {
            consumer.consume(action);
          }
          else {
            ApplicationManager.getApplication().invokeLater(() -> consumer.consume(action));
          }
        }
      }
    });
  }

  private void callReloadMergeInfo() {
    doForEachInitialized(rootsAndBranches -> {
      rootsAndBranches.reloadPanels();
      rootsAndBranches.refresh();
    });
  }

  public void removePanel(final RootsAndBranches action) {
    if (myMergeInfoRefreshActions != null) {
      myMergeInfoRefreshActions.remove(action);
    }
  }
}
