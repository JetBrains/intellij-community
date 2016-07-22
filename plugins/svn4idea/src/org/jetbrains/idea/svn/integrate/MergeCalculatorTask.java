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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.TransparentlyFailedValueI;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.history.*;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;
import org.jetbrains.idea.svn.mergeinfo.OneShotMergeInfoHelper;
import org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Konstantin Kolosovsky.
 */
public class MergeCalculatorTask extends BaseMergeTask
  implements Consumer<TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException>> {

  @NotNull private final AtomicReference<TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException>> myCopyData;

  @NotNull private final String myMergeTitle;
  @NotNull private final MergeChecker myMergeChecker;

  @Override
  public void consume(TransparentlyFailedValueI<SvnBranchPointsCalculator.WrapperInvertor, VcsException> value) {
    myCopyData.set(value);
  }

  public MergeCalculatorTask(@NotNull MergeContext mergeContext, @NotNull QuickMergeInteraction interaction) throws VcsException {
    super(mergeContext, interaction, "Calculating not merged revisions", Where.POOLED);

    myMergeTitle = "Merge from " + myMergeContext.getBranchName();
    // TODO: Previously it was configurable - either to use OneShotMergeInfoHelper or BranchInfo as merge checker, but later that logic
    // TODO: was commented (in 80ebdbfea5210f6c998e67ddf28ca9c670fa4efe on 5/28/2010).
    // TODO: Still check if we need to preserve such configuration or it is sufficient to always use OneShotMergeInfoHelper.
    myMergeChecker = new OneShotMergeInfoHelper(myMergeContext);
    ((OneShotMergeInfoHelper)myMergeChecker).prepare();
    myCopyData = new AtomicReference<>();
  }

  @Override
  public void run(ContinuationContext context) {
    SvnBranchPointsCalculator.WrapperInvertor copyPoint = getCopyPoint(context);

    if (copyPoint != null && myMergeContext.getWcInfo().getFormat().supportsMergeInfo()) {
      List<Pair<SvnChangeList, LogHierarchyNode>> afterCopyPointChangeLists =
        getChangeListsAfter(context, copyPoint.getTrue().getTargetRevision());
      List<CommittedChangeList> notMergedChangeLists = getNotMergedChangeLists(afterCopyPointChangeLists);

      if (!notMergedChangeLists.isEmpty()) {
        context.next(new ShowRevisionSelector(copyPoint, notMergedChangeLists));
      }
      else {
        finishWithError(context, "Everything is up-to-date", false);
      }
    }
  }

  @Nullable
  private SvnBranchPointsCalculator.WrapperInvertor getCopyPoint(@NotNull ContinuationContext context) {
    SvnBranchPointsCalculator.WrapperInvertor result = null;

    try {
      result = myCopyData.get().get();

      if (result == null) {
        finishWithError(context, "Merge start wasn't found", true);
      }
    }
    catch (VcsException e) {
      finishWithError(context, "Merge start wasn't found", Collections.singletonList(e));
    }

    return result;
  }

  @NotNull
  private LinkedList<Pair<SvnChangeList, LogHierarchyNode>> getChangeListsAfter(@NotNull ContinuationContext context, final long revision) {
    ChangeBrowserSettings settings = new ChangeBrowserSettings();
    settings.CHANGE_AFTER = Long.toString(revision);
    settings.USE_CHANGE_AFTER_FILTER = true;

    final LinkedList<Pair<SvnChangeList, LogHierarchyNode>> result = ContainerUtil.newLinkedList();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    try {
      ((SvnCommittedChangesProvider)myMergeContext.getVcs().getCommittedChangesProvider())
        .getCommittedChangesWithMergedRevisons(settings, new SvnRepositoryLocation(myMergeContext.getSourceUrl()), 0,
                                               new PairConsumer<SvnChangeList, LogHierarchyNode>() {

                                                 public void consume(@NotNull SvnChangeList changeList, LogHierarchyNode tree) {
                                                   indicator.checkCanceled();
                                                   if (revision < changeList.getNumber()) {
                                                     result.add(Pair.create(changeList, tree));
                                                   }
                                                 }
                                               });
    }
    catch (VcsException e) {
      finishWithError(context, "Checking revisions for merge fault", Collections.singletonList(e));
    }

    return result;
  }

  @NotNull
  private List<CommittedChangeList> getNotMergedChangeLists(@NotNull List<Pair<SvnChangeList, LogHierarchyNode>> changeLists) {
    ProgressManager.getInstance().getProgressIndicator().setText("Checking merge information...");

    String repositoryRelativeWorkingCopyRoot = SvnUtil.ensureStartSlash(
      SVNPathUtil.getRelativePath(myMergeContext.getWcInfo().getRepositoryRoot(), myMergeContext.getWcInfo().getRootUrl()));
    String repositoryRelativeSourceBranch =
      SvnUtil.ensureStartSlash(SVNPathUtil.getRelativePath(myMergeContext.getWcInfo().getRepositoryRoot(), myMergeContext.getSourceUrl()));

    return getNotMergedChangeLists(changeLists, repositoryRelativeWorkingCopyRoot, repositoryRelativeSourceBranch);
  }

  @NotNull
  private List<CommittedChangeList> getNotMergedChangeLists(@NotNull List<Pair<SvnChangeList, LogHierarchyNode>> changeLists,
                                                            @NotNull String workingCopyRoot,
                                                            @NotNull String sourceBranch) {
    List<CommittedChangeList> result = ContainerUtil.newArrayList();

    for (Pair<SvnChangeList, LogHierarchyNode> pair : changeLists) {
      SvnChangeList changeList = pair.getFirst();

      ProgressManager.getInstance().getProgressIndicator().setText2("Processing revision " + changeList.getNumber());

      if (SvnMergeInfoCache.MergeCheckResult.NOT_MERGED.equals(myMergeChecker.checkList(changeList)) &&
          !checkListForPaths(workingCopyRoot, sourceBranch, pair.getSecond())) {
        result.add(changeList);
      }
    }

    return result;
  }

  private class ShowRevisionSelector extends TaskDescriptor {

    @NotNull private final List<CommittedChangeList> myChangeLists;
    @NotNull private final SvnBranchPointsCalculator.WrapperInvertor myCopyPoint;

    private ShowRevisionSelector(@NotNull SvnBranchPointsCalculator.WrapperInvertor copyPoint,
                                 @NotNull List<CommittedChangeList> changeLists) {
      super("show revisions to merge", Where.AWT);

      myCopyPoint = copyPoint;
      myChangeLists = changeLists;
    }

    @Override
    public void run(ContinuationContext context) {
      QuickMergeInteraction.SelectMergeItemsResult result = myInteraction.selectMergeItems(myChangeLists, myMergeTitle, myMergeChecker);

      switch (result.getResultCode()) {
        case cancel:
          context.cancelEverything();
          break;
        case all:
          context.next(getMergeAllTasks());
          break;
        default:
          List<CommittedChangeList> lists = result.getSelectedLists();

          if (!lists.isEmpty()) {
            runChangeListsMerge(context, lists, myCopyPoint, myMergeTitle);
          }
          break;
      }
    }
  }

  // true if errors found
  static boolean checkListForPaths(@NotNull final String workingCopyRoot,
                                   @NotNull final String sourceBranch,
                                   @NotNull LogHierarchyNode node) {
    // TODO: Such filtering logic is not clear enough so far (and probably not correct for all cases - for instance when we perform merge
    // TODO: from branch1 to branch2 and have revision which contain merge changes from branch3 to branch1.
    // TODO: In this case paths of child log entries will not contain neither urls from branch1 nor from branch2 - and checkEntry() method
    // TODO: will return true => so such revision will not be used (and displayed) further.

    // TODO: Why do we check entries recursively - we have a revision - set of changes in the "merge from" branch? Why do we need to check
    // TODO: where they came from - we want avoid some circular merges or what? Does subversion itself perform such checks or not?
    boolean isLocalChange = ContainerUtil.or(node.getChildren(), new Condition<LogHierarchyNode>() {
      @Override
      public boolean value(@NotNull LogHierarchyNode child) {
        return checkForSubtree(child, workingCopyRoot, sourceBranch);
      }
    });

    return isLocalChange || checkForEntry(node.getMe(), workingCopyRoot, sourceBranch);
  }

  /**
   * TODO: Why parameters here are in [relativeBranch/sourceBranch, localURL/workingCopyRoot] order? - not as in other similar checkXxx()
   * TODO: methods? Check if this is correct, because currently it results that checkForEntry() from checkListForPaths() and
   * TODO: checkForSubtree() are called with swapped parameters.
   */
  // true if errors found
  private static boolean checkForSubtree(@NotNull LogHierarchyNode tree, @NotNull String relativeBranch, @NotNull String localURL) {
    final LinkedList<LogHierarchyNode> queue = new LinkedList<>();
    queue.addLast(tree);

    while (!queue.isEmpty()) {
      final LogHierarchyNode element = queue.removeFirst();
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
  private static boolean checkForEntry(@NotNull LogEntry entry, @NotNull String localURL, @NotNull String relativeBranch) {
    boolean atLeastOneUnderBranch = false;

    for (LogEntryPath path : entry.getChangedPaths().values()) {
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
