/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ZipperUpdater;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.committed.VcsConfigurationChangeListener;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.integrate.Merger;
import org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache;

import java.util.ArrayList;
import java.util.List;

public class MergeInfoUpdatesListener {
  private final static int DELAY = 300;

  private final Project myProject;
  private final MessageBusConnection myConnection;
  private List<RootsAndBranches> myMergeInfoRefreshActions;
  private final ZipperUpdater myUpdater;

  public MergeInfoUpdatesListener(final Project project, final MessageBusConnection connection) {
    myConnection = connection;
    myProject = project;
    myUpdater = new ZipperUpdater(DELAY, myProject);
  }

  public void addPanel(final RootsAndBranches action) {
    if (myMergeInfoRefreshActions == null) {
      myMergeInfoRefreshActions = new ArrayList<>();
      myMergeInfoRefreshActions.add(action);

      myConnection.subscribe(VcsConfigurationChangeListener.BRANCHES_CHANGED, new VcsConfigurationChangeListener.Notification() {
        public void execute(final Project project, final VirtualFile vcsRoot) {
          callReloadMergeInfo();
        }
      });
      final Consumer<Boolean> reloadConsumer = new Consumer<Boolean>() {
        @Override
        public void consume(Boolean aBoolean) {
          if (Boolean.TRUE.equals(aBoolean)) {
            callReloadMergeInfo();
          }
        }
      };
      final Runnable reloadRunnable = new Runnable() {
        @Override
        public void run() {
          callReloadMergeInfo();
        }
      };
      myConnection.subscribe(SvnVcs.WC_CONVERTED, reloadRunnable);
      myConnection.subscribe(RootsAndBranches.REFRESH_REQUEST, reloadRunnable);

      myConnection.subscribe(SvnVcs.ROOTS_RELOADED, reloadConsumer);

      ProjectLevelVcsManager.getInstance(myProject).addVcsListener(new VcsListener() {
        public void directoryMappingChanged() {
          callReloadMergeInfo();
        }
      });

      myConnection.subscribe(CommittedChangesTreeBrowser.ITEMS_RELOADED, new CommittedChangesTreeBrowser.CommittedChangesReloadListener() {
        public void itemsReloaded() {
          reloadRunnable.run();
        }
        public void emptyRefresh() {
        }
      });

      myConnection.subscribe(SvnMergeInfoCache.SVN_MERGE_INFO_CACHE, new SvnMergeInfoCache.SvnMergeInfoCacheListener() {
        public void copyRevisionUpdated() {
          doForEachInitialized(new Consumer<RootsAndBranches>() {
            public void consume(final RootsAndBranches rootsAndBranches) {
              rootsAndBranches.fireRepaint();
            }
          });
        }
      });

      myConnection.subscribe(Merger.COMMITTED_CHANGES_MERGED_STATE, new Merger.CommittedChangesMergedStateChanged() {
        public void event(final List<CommittedChangeList> list) {
          doForEachInitialized(new Consumer<RootsAndBranches>() {
            public void consume(RootsAndBranches rootsAndBranches) {
              rootsAndBranches.refreshByLists(list);
            }
          });
        }
      });
    } else {
      myMergeInfoRefreshActions.add(action);
    }
  }

  private void doForEachInitialized(final Consumer<RootsAndBranches> consumer) {
    myUpdater.queue(new Runnable() {
      public void run() {
        for (final RootsAndBranches action : myMergeInfoRefreshActions) {
          if (action.strategyInitialized()) {
            if (ApplicationManager.getApplication().isDispatchThread()) {
              consumer.consume(action);
            } else {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  consumer.consume(action);
                }
              });
            }
          }
        }
      }
    });
  }
  
  private void callReloadMergeInfo() {
    doForEachInitialized(new Consumer<RootsAndBranches>() {
      public void consume(final RootsAndBranches rootsAndBranches) {
        rootsAndBranches.reloadPanels();
        rootsAndBranches.refresh();
      }
    });
  }

  public void removePanel(final RootsAndBranches action) {
    if (myMergeInfoRefreshActions != null) {
      myMergeInfoRefreshActions.remove(action);
    }
  }
}
