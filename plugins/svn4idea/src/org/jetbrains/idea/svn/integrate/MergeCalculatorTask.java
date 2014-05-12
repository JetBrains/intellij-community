/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.TransparentlyFailedValueI;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.ChangeListsMergerFactory;
import org.jetbrains.idea.svn.dialogs.MergeContext;
import org.jetbrains.idea.svn.dialogs.QuickMergeContentsVariants;
import org.jetbrains.idea.svn.dialogs.SvnBranchPointsCalculator;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnCommittedChangesProvider;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.jetbrains.idea.svn.history.TreeStructureNode;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;
import org.jetbrains.idea.svn.mergeinfo.OneShotMergeInfoHelper;
import org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Konstantin Kolosovsky.
 */
public class MergeCalculatorTask extends BaseMergeTask implements
                                                       Consumer<TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException>> {
  private final static String ourOneShotStrategy = "svn.quickmerge.oneShotStrategy";
  private final
  AtomicReference<TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException>>
    myCopyData;
  private boolean myIsReintegrate;

  private final List<CommittedChangeList> myNotMerged;
  private String myMergeTitle;
  private final MergeChecker myMergeChecker;

  @Override
  public void consume(TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException> value) {
    myCopyData.set(value);
  }

  public MergeCalculatorTask(@NotNull MergeContext mergeContext, @NotNull QuickMergeInteraction interaction) throws VcsException {
    super(mergeContext, interaction, "Calculating not merged revisions", Where.POOLED);
    myNotMerged = new LinkedList<CommittedChangeList>();
    myMergeTitle = "Merge from " + myMergeContext.getBranchName();
    //      if (Boolean.TRUE.equals(Boolean.getBoolean(ourOneShotStrategy))) {
    myMergeChecker = new OneShotMergeInfoHelper(myMergeContext);
    ((OneShotMergeInfoHelper)myMergeChecker).prepare();
/*      } else {
      myMergeChecker = new BranchInfo.MyMergeCheckerWrapper(myWcInfo.getPath(), new BranchInfo(myVcs, myWcInfo.getRepositoryRoot(),
                                                                                               myWcInfo.getRootUrl(), mySourceUrl,
                                                                                               mySourceUrl, myVcs.createWCClient()));
    }*/
    myCopyData = new AtomicReference<TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException>>();
  }

  //"Calculating not merged revisions"
  @Override
  public void run(ContinuationContext context) {
    SvnBranchPointsCalculator.WrapperInvertor copyDataValue = null;
    try {
      copyDataValue = myCopyData.get().get();
    }
    catch (VcsException e) {
      finishWithError(context, "Merge start wasn't found", Collections.singletonList(e));
      return;
    }
    if (copyDataValue == null) {
      finishWithError(context, "Merge start wasn't found", true);
      return;
    }

    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    myIsReintegrate = copyDataValue.isInvertedSense();
    if (!myMergeContext.getWcInfo().getFormat().supportsMergeInfo()) return;
    final SvnBranchPointsCalculator.BranchCopyData data = copyDataValue.getTrue();
    final long sourceLatest = data.getTargetRevision();

    final SvnCommittedChangesProvider committedChangesProvider =
      (SvnCommittedChangesProvider)myMergeContext.getVcs().getCommittedChangesProvider();
    final ChangeBrowserSettings settings = new ChangeBrowserSettings();
    settings.CHANGE_AFTER = Long.toString(sourceLatest);
    settings.USE_CHANGE_AFTER_FILTER = true;

    String local = SVNPathUtil.getRelativePath(myMergeContext.getWcInfo().getRepositoryRoot(), myMergeContext.getWcInfo().getRootUrl());
    final String relativeLocal = (local.startsWith("/") ? local : "/" + local);
    String relativeBranch = SVNPathUtil.getRelativePath(myMergeContext.getWcInfo().getRepositoryRoot(), myMergeContext.getSourceUrl());
    relativeBranch = (relativeBranch.startsWith("/") ? relativeBranch : "/" + relativeBranch);

    final LinkedList<Pair<SvnChangeList, TreeStructureNode<SVNLogEntry>>> list =
      new LinkedList<Pair<SvnChangeList, TreeStructureNode<SVNLogEntry>>>();
    try {
      committedChangesProvider.getCommittedChangesWithMergedRevisons(settings, new SvnRepositoryLocation(myMergeContext.getSourceUrl()), 0,
                                                                     new PairConsumer<SvnChangeList, TreeStructureNode<SVNLogEntry>>() {
                                                                       public void consume(SvnChangeList svnList,
                                                                                           TreeStructureNode<SVNLogEntry> tree) {
                                                                         indicator.checkCanceled();
                                                                         if (sourceLatest >= svnList.getNumber()) return;
                                                                         list.add(
                                                                           Pair.create(svnList,
                                                                                       tree)
                                                                         );
                                                                       }
                                                                     }
      );
    }
    catch (VcsException e) {
      finishWithError(context, "Checking revisions for merge fault", Collections.singletonList(e));
    }

    indicator.setText("Checking merge information...");
    // to do not go into file system while asking something on the net
    for (Pair<SvnChangeList, TreeStructureNode<SVNLogEntry>> pair : list) {
      final SvnChangeList svnList = pair.getFirst();
      final SvnMergeInfoCache.MergeCheckResult checkResult = myMergeChecker.checkList(svnList);
      indicator.setText2("Processing revision " + svnList.getNumber());

      if (SvnMergeInfoCache.MergeCheckResult.NOT_MERGED.equals(checkResult)) {
        // additionally check for being 'local'
        boolean localChange = checkListForPaths(relativeLocal, relativeBranch, pair);

        if (!localChange) {
          myNotMerged.add(svnList);
        }
      }
    }

    if (myNotMerged.isEmpty()) {
      finishWithError(context, "Everything is up-to-date", false);
      return;
    }
    context.next(new ShowRevisionSelector(copyDataValue));
  }

  private class ShowRevisionSelector extends TaskDescriptor {
    private final SvnBranchPointsCalculator.WrapperInvertor myCopyPoint;

    private ShowRevisionSelector(SvnBranchPointsCalculator.WrapperInvertor copyPoint) {
      super("show revisions to merge", Where.AWT);
      myCopyPoint = copyPoint;
    }

    @Override
    public void run(ContinuationContext context) {
      final QuickMergeInteraction.SelectMergeItemsResult result = myInteraction.selectMergeItems(myNotMerged, myMergeTitle, myMergeChecker);
      if (QuickMergeContentsVariants.cancel == result.getResultCode()) {
        context.cancelEverything();
        return;
      }
      if (QuickMergeContentsVariants.all == result.getResultCode()) {
        insertMergeAll(context);
      }
      else {
        final List<CommittedChangeList> lists = result.getSelectedLists();
        if (lists.isEmpty()) return;
        final MergerFactory factory = new ChangeListsMergerFactory(lists) {
          @Override
          public IMerger createMerger(SvnVcs vcs, File target, UpdateEventHandler handler, SVNURL currentBranchUrl, String branchName) {
            return new GroupMerger(vcs, lists, target, handler, currentBranchUrl, branchName, false, false, false);
          }
        };
        context.next(new LocalChangesPromptTask(myMergeContext, myInteraction, false, lists, myCopyPoint), new MergeTask(myMergeContext,
                                                                                                                         myInteraction,
                                                                                                                         factory,
                                                                                                                         myMergeTitle
        ));
      }
    }
  }

  // true if errors found
  static boolean checkListForPaths(String relativeLocal,
                                   String relativeBranch, Pair<SvnChangeList, TreeStructureNode<SVNLogEntry>> pair) {
    // TODO: Such filtering logic is not clear enough so far (and probably not correct for all cases - for instance when we perform merge
    // TODO: from branch1 to branch2 and have revision which contain merge changes from branch3 to branch1.
    // TODO: In this case paths of child log entries will not contain neither urls from branch1 nor from branch2 - and checkEntry() method
    // TODO: will return true => so such revision will not be used (and displayed) further.

    // TODO: Why do we check entries recursively - we have a revision - set of changes in the "merge from" branch? Why do we need to check
    // TODO: where they came from - we want avoid some circular merges or what? Does subversion itself perform such checks or not?
    final List<TreeStructureNode<SVNLogEntry>> children = pair.getSecond().getChildren();
    boolean localChange = false;
    for (TreeStructureNode<SVNLogEntry> child : children) {
      if (checkForSubtree(child, relativeLocal, relativeBranch)) {
        localChange = true;
        break;
      }
    }
    if (!localChange) {
      // check self
      return checkForEntry(pair.getSecond().getMe(), relativeLocal, relativeBranch);
    }
    return localChange;
  }

  // true if errors found
  private static boolean checkForSubtree(final TreeStructureNode<SVNLogEntry> tree,
                                         String relativeBranch, final String localURL) {
    final LinkedList<TreeStructureNode<SVNLogEntry>> queue = new LinkedList<TreeStructureNode<SVNLogEntry>>();
    queue.addLast(tree);

    while (!queue.isEmpty()) {
      final TreeStructureNode<SVNLogEntry> element = queue.removeFirst();
      ProgressManager.checkCanceled();

      if (checkForEntry(element.getMe(), localURL, relativeBranch)) return true;
      queue.addAll(element.getChildren());
    }
    return false;
  }

  // true if errors found
  // checks if either some changed path is in current branch => treat as local change
  // or if no changed paths in current branch, checks if at least one path in "merge from" branch
  // NOTE: this fails for "merge-source" log entries from other branches - when all changed paths are from some
  // third branch - this logic treats such log entry as local.
  private static boolean checkForEntry(final SVNLogEntry entry, final String localURL, String relativeBranch) {
    boolean atLeastOneUnderBranch = false;
    final Map map = entry.getChangedPaths();
    for (Object o : map.values()) {
      final SVNLogEntryPath path = (SVNLogEntryPath)o;
      if (SVNPathUtil.isAncestor(localURL, path.getPath())) {
        return true;
      }
      if (!atLeastOneUnderBranch && SVNPathUtil.isAncestor(relativeBranch, path.getPath())) {
        atLeastOneUnderBranch = true;
      }
    }
    return !atLeastOneUnderBranch;
  }
}
