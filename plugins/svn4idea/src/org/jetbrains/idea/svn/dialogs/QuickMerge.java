/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.CalledInAny;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.changes.committed.RunBackgroundable;
import com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.ChangeListsMergerFactory;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.history.*;
import org.jetbrains.idea.svn.integrate.IMerger;
import org.jetbrains.idea.svn.integrate.MergerFactory;
import org.jetbrains.idea.svn.integrate.SvnIntegrateChangesTask;
import org.jetbrains.idea.svn.integrate.WorkingCopyInfo;
import org.jetbrains.idea.svn.mergeinfo.BranchInfo;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;
import org.jetbrains.idea.svn.mergeinfo.OneShotMergeInfoHelper;
import org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class QuickMerge {
  private final Project myProject;
  private final String myBranchName;
  private final SvnBranchConfigurationNew myConfiguration;
  private final VirtualFile myRoot;
  private final WCInfo myWcInfo;
  private String mySourceUrl;
  private SvnVcs myVcs;
  private final String myTitle;

  public QuickMerge(Project project, String sourceUrl, WCInfo wcInfo, final String branchName, final SvnBranchConfigurationNew configuration,
                    final VirtualFile root) {
    myProject = project;
    myBranchName = branchName;
    myConfiguration = configuration;
    myRoot = root;
    myVcs = SvnVcs.getInstance(project);
    mySourceUrl = sourceUrl;
    myWcInfo = wcInfo;
    myTitle = "Merge from " + myBranchName;
  }

  private void correctSourceUrl(final Runnable continuation) {
    ProgressManager.getInstance().run(
      new Task.Backgroundable(myProject, "Checking branch", true, BackgroundFromStartOption.getInstance()) {
        public void run(@NotNull ProgressIndicator indicator) {
          final SVNURL branch = SvnBranchConfigurationManager.getInstance(myProject).getSvnBranchConfigManager().getWorkingBranchWithReload(myWcInfo.getUrl(), myRoot);
          //final SVNURL branch = myConfiguration.getWorkingBranch(myWcInfo.getUrl());
          if (branch != null && (! myWcInfo.getUrl().equals(branch))) {
            final String branchString = branch.toString();
            if (SVNPathUtil.isAncestor(branchString, myWcInfo.getRootUrl())) {
              final String subPath = SVNPathUtil.getRelativePath(branchString, myWcInfo.getRootUrl());
              mySourceUrl = SVNPathUtil.append(mySourceUrl, subPath);
            }
          }
        }

        @Override
        public void onSuccess() {
          continuation.run();
        }
      });
  }

  private boolean prompt(final String question) {
    return Messages.showOkCancelDialog(myProject, question, myTitle, Messages.getQuestionIcon()) == 0;
  }
  
  @CalledInAwt
  public void execute() {
    if (SVNPathUtil.isAncestor(mySourceUrl, myWcInfo.getRootUrl()) || SVNPathUtil.isAncestor(myWcInfo.getRootUrl(), mySourceUrl)) {
      showErrorBalloon("Cannot merge from self");
      return;
    }

    correctSourceUrl(new Runnable() {
      public void run() {
        if (! myWcInfo.getFormat().supportsMergeInfo()) {
          mergeAll();
          return;
        }

        final int result = Messages.showDialog(myProject, "Merge all?", myTitle,
                            new String[]{"Merge &all", "&Select revisions to merge", "Cancel"}, 0, Messages.getQuestionIcon());
        if (result == 2) return;
        if (result == 0) {
          mergeAll();
          return;
        }

        try {
          ProgressManager.getInstance().run(new MergeCalculator(myProject, myWcInfo, mySourceUrl, myBranchName));
        }
        catch (SVNException e) {
          showErrorBalloon(e.getMessage());
        }
      }
    });
  }

  @CalledInAny
  private void showErrorBalloon(final String s) {
    ChangesViewBalloonProblemNotifier.showMe(myProject, s, MessageType.ERROR);
  }

  // continuation... continuation.. hidden continuation..
  private void mergeAll() {
    // suppose we're in branch
    myVcs.getSvnBranchPointsCalculator().getFirstCopyPoint(myWcInfo.getRepositoryRoot(), mySourceUrl, myWcInfo.getRootUrl(),
      new Consumer<SvnBranchPointsCalculator.WrapperInvertor<SvnBranchPointsCalculator.BranchCopyData>>() {
        public void consume(SvnBranchPointsCalculator.WrapperInvertor<SvnBranchPointsCalculator.BranchCopyData> result) {
          if (result == null) {
            showErrorBalloon("Merge start wasn't found");
            return;
          }
          final boolean reintegrate = result.isInvertedSense();
          if (! prompt("You are going to reintegrate changes.\nThis will make " + mySourceUrl + " no longer usable for further work." +
                       "\nAre you sure?")) return;
          final MergerFactory mergerFactory = new MergerFactory() {
            public IMerger createMerger(SvnVcs vcs, File target, UpdateEventHandler handler, SVNURL currentBranchUrl) {
              return new BranchMerger(vcs, currentBranchUrl, myWcInfo.getUrl(), myWcInfo.getPath(), handler, reintegrate, myBranchName);
            }
          };

          final String title = "Merging all from " + myBranchName + (reintegrate ? " (reintegrate)" : "");
          doMerge(mergerFactory, title);
        }
      });
  }

  @CalledInAny
  private void doMerge(final MergerFactory factory, final String mergeTitle) {
    final SVNURL sourceUrlUrl;
    try {
      sourceUrlUrl = SVNURL.parseURIEncoded(mySourceUrl);
    } catch (SVNException e) {
      showErrorBalloon("Cannot merge: " + e.getMessage());
      return;
    }
    final SvnIntegrateChangesTask task = new SvnIntegrateChangesTask(SvnVcs.getInstance(myProject),
                                             new WorkingCopyInfo(myWcInfo.getPath(), true), factory, sourceUrlUrl, mergeTitle, false);
    RunBackgroundable.run(task);
  }

  private class MergeCalculator extends Task.Backgroundable {
    private final static String ourOneShotStrategy = "svn.quickmerge.oneShotStrategy";
    private final WCInfo myWcInfo;
    private final String mySourceUrl;
    private final String myBranchName;
    private boolean myIsReintegrate;

    private final List<CommittedChangeList> myNotMerged;
    private String myMergeTitle;
    private final MergeChecker myMergeChecker;

    private MergeCalculator(Project project, WCInfo wcInfo, String sourceUrl, String branchName) throws SVNException {
      super(project, "Calculating not merged revisions", true, BackgroundFromStartOption.getInstance());
      myWcInfo = wcInfo;
      mySourceUrl = sourceUrl;
      myBranchName = branchName;
      myNotMerged = new LinkedList<CommittedChangeList>();
      myMergeTitle = "Merge from " + branchName;
      if (Boolean.TRUE.equals(Boolean.getBoolean(ourOneShotStrategy))) {
        myMergeChecker = new OneShotMergeInfoHelper(myProject, myWcInfo, mySourceUrl);
        ((OneShotMergeInfoHelper) myMergeChecker).prepare();
      } else {
        myMergeChecker = new BranchInfo.MyMergeCheckerWrapper(myWcInfo.getPath(), new BranchInfo(myVcs, myWcInfo.getRepositoryRoot(),
                                                                                                 myWcInfo.getRootUrl(), mySourceUrl,
                                                                                                 mySourceUrl, myVcs.createWCClient()));
      }
    }

    public void run(@NotNull final ProgressIndicator indicator) {
      // branch is info holder
      final Consumer<CopyData> revisionsLoader = new Consumer<CopyData>() {
        public void consume(CopyData copyData) {
          if (copyData == null) {
            showErrorBalloon("Merge start wasn't found");
            return;
          }

          myIsReintegrate = !copyData.isTrunkSupposedCorrect();
          if (!myWcInfo.getFormat().supportsMergeInfo()) return;
          final long localLatest = !copyData.isTrunkSupposedCorrect() ? copyData.getCopyTargetRevision() : copyData.getCopySourceRevision();

          final SvnCommittedChangesProvider committedChangesProvider = (SvnCommittedChangesProvider)myVcs.getCommittedChangesProvider();
          final ChangeBrowserSettings settings = new ChangeBrowserSettings();
          settings.CHANGE_AFTER = Long.toString(localLatest);
          settings.USE_CHANGE_AFTER_FILTER = true;

          String local = SVNPathUtil.getRelativePath(myWcInfo.getRepositoryRoot(), myWcInfo.getRootUrl());
          final String relativeLocal = (local.startsWith("/") ? local : "/" + local);

          final LinkedList<Pair<SvnChangeList, TreeStructureNode<SVNLogEntry>>> list =
            new LinkedList<Pair<SvnChangeList, TreeStructureNode<SVNLogEntry>>>();
          try {
            committedChangesProvider.getCommittedChangesWithMergedRevisons(settings, new SvnRepositoryLocation(mySourceUrl), 0,
                       new PairConsumer<SvnChangeList, TreeStructureNode<SVNLogEntry>>() {
                         public void consume(SvnChangeList svnList, TreeStructureNode<SVNLogEntry> tree) {
                           indicator.checkCanceled();
                           if (localLatest >= svnList.getNumber()) return;
                           list.add(new Pair<SvnChangeList, TreeStructureNode<SVNLogEntry>>(svnList, tree));
                         }
                      });
          }
          catch (VcsException e) {
            AbstractVcsHelper.getInstance(myProject).showErrors(Collections.singletonList(e), "Checking revisions for merge fault");
          }

          // to do not go into file system while asking something on the net 
          for (Pair<SvnChangeList, TreeStructureNode<SVNLogEntry>> pair : list) {
            final SvnChangeList svnList = pair.getFirst();
            final SvnMergeInfoCache.MergeCheckResult checkResult = myMergeChecker.checkList(svnList);

            if (SvnMergeInfoCache.MergeCheckResult.NOT_MERGED.equals(checkResult)) {
              // additionally check for being 'local'
              final List<TreeStructureNode<SVNLogEntry>> children = pair.getSecond().getChildren();
              boolean localChange = false;
              for (TreeStructureNode<SVNLogEntry> child : children) {
                if (isLocalRevisionMergeIteration(child, relativeLocal, indicator)) {
                  localChange = true;
                  break;
                }
              }

              if (! localChange) {
                myNotMerged.add(svnList);
              }
            }
          }
        }
      };
      
      new FirstInBranch(myVcs, myWcInfo.getRepositoryRoot(), myWcInfo.getRootUrl(), mySourceUrl, revisionsLoader).run();
    }

    private boolean isLocalRevisionMergeIteration(final TreeStructureNode<SVNLogEntry> tree,
                                                  final String localURL,
                                                  ProgressIndicator indicator) {
      final LinkedList<TreeStructureNode<SVNLogEntry>> queue = new LinkedList<TreeStructureNode<SVNLogEntry>>();
      queue.addLast(tree);

      while (! queue.isEmpty()) {
        final TreeStructureNode<SVNLogEntry> element = queue.removeFirst();
        indicator.checkCanceled();
        
        final Map map = element.getMe().getChangedPaths();
        for (Object o : map.values()) {
          final SVNLogEntryPath path = (SVNLogEntryPath) o;
          if (SVNPathUtil.isAncestor(localURL, path.getPath())) {
            return true;
          }
          break;  // do not check all. first should match or fail
        }
        queue.addAll(element.getChildren());
      }
      return false;
    }

    /*private boolean isLocalRevisionMerge(final TreeStructureNode<SVNLogEntry> tree, final String localURL) {
      final Map map = tree.getMe().getChangedPaths();
      for (Object o : map.values()) {
        final SVNLogEntryPath path = (SVNLogEntryPath) o;
        if (SVNPathUtil.isAncestor(localURL, path.getPath())) return true;
      }
      for (TreeStructureNode<SVNLogEntry> node : tree.getChildren()) {
        final boolean subResult = isLocalRevisionMerge(node, localURL);
        if (subResult) return true;
      }
      return false;
    }*/

    @Override
    public void onCancel() {
      onSuccess();
    }

    private void askParametersAndMerge() {
      final ToBeMergedDialog dialog = new ToBeMergedDialog(myProject, myNotMerged, myMergeTitle, myMergeChecker);
      dialog.show();
      if (dialog.getExitCode() == DialogWrapper.CANCEL_EXIT_CODE) {
        return;
      }
      if (dialog.getExitCode() == ToBeMergedDialog.MERGE_ALL_CODE) {
        mergeAll();
      } else {
        final List<CommittedChangeList> lists = dialog.getSelected();
        if (lists.isEmpty()) return;
        final MergerFactory factory = new ChangeListsMergerFactory(lists);
        doMerge(factory, myMergeTitle);
      }
    }

    @Override
    public void onSuccess() {
      if (myNotMerged.isEmpty()) {
        ChangesViewBalloonProblemNotifier.showMe(myProject, "Everything is up-to-date", MessageType.WARNING);
        return;
      }
      askParametersAndMerge();
    }
  }
}
