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
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.util.Consumer;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.history.LogHierarchyNode;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnCommittedChangesProvider;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;
import org.jetbrains.idea.svn.mergeinfo.OneShotMergeInfoHelper;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache.MergeCheckResult;

public class MergeCalculatorTask extends BaseMergeTask {

  @NotNull private final SvnBranchPointsCalculator.WrapperInvertor myCopyPoint;
  @NotNull private final OneShotMergeInfoHelper myMergeChecker;
  @NotNull private final List<SvnChangeList> myNotMergedChangeLists;
  @NotNull private final Consumer<MergeCalculatorTask> myCallback;

  public MergeCalculatorTask(@NotNull QuickMerge mergeProcess,
                             @NotNull SvnBranchPointsCalculator.WrapperInvertor copyPoint,
                             @NotNull Consumer<MergeCalculatorTask> callback) {
    super(mergeProcess, "Calculating not merged revisions", Where.POOLED);
    myCopyPoint = copyPoint;
    myCallback = callback;
    myNotMergedChangeLists = newArrayList();
    // TODO: Previously it was configurable - either to use OneShotMergeInfoHelper or BranchInfo as merge checker, but later that logic
    // TODO: was commented (in 80ebdbfea5210f6c998e67ddf28ca9c670fa4efe on 5/28/2010).
    // TODO: Still check if we need to preserve such configuration or it is sufficient to always use OneShotMergeInfoHelper.
    myMergeChecker = new OneShotMergeInfoHelper(myMergeContext);
  }

  @NotNull
  public MergeChecker getMergeChecker() {
    return myMergeChecker;
  }

  @NotNull
  public List<SvnChangeList> getChangeLists() {
    return myNotMergedChangeLists;
  }

  @Override
  public void run() throws VcsException {
    myMergeChecker.prepare();
    myNotMergedChangeLists.addAll(getNotMergedChangeLists(getChangeListsAfter(myCopyPoint.getTrue().getTargetRevision())));

    if (!myNotMergedChangeLists.isEmpty()) {
      myCallback.consume(this);
    }
    else {
      end("Everything is up-to-date", false);
    }
  }

  @NotNull
  private List<Pair<SvnChangeList, LogHierarchyNode>> getChangeListsAfter(final long revision) {
    ChangeBrowserSettings settings = new ChangeBrowserSettings();
    settings.CHANGE_AFTER = Long.toString(revision);
    settings.USE_CHANGE_AFTER_FILTER = true;

    List<Pair<SvnChangeList, LogHierarchyNode>> result = newArrayList();
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    try {
      ((SvnCommittedChangesProvider)myMergeContext.getVcs().getCommittedChangesProvider())
        .getCommittedChangesWithMergedRevisons(settings, new SvnRepositoryLocation(myMergeContext.getSourceUrl()), 0,
                                               (changeList, tree) -> {
                                                 indicator.checkCanceled();
                                                 if (revision < changeList.getNumber()) {
                                                   result.add(Pair.create(changeList, tree));
                                                 }
                                               });
    }
    catch (VcsException e) {
      end("Checking revisions for merge fault", e);
    }

    return result;
  }

  @NotNull
  private List<SvnChangeList> getNotMergedChangeLists(@NotNull List<Pair<SvnChangeList, LogHierarchyNode>> changeLists) {
    List<SvnChangeList> result = newArrayList();
    ProgressManager.getInstance().getProgressIndicator().setText("Checking merge information...");

    for (Pair<SvnChangeList, LogHierarchyNode> pair : changeLists) {
      SvnChangeList changeList = pair.getFirst();

      ProgressManager.getInstance().getProgressIndicator().setText2("Processing revision " + changeList.getNumber());

      if (MergeCheckResult.NOT_MERGED.equals(myMergeChecker.checkList(changeList)) && !myMergeChecker.checkListForPaths(pair.getSecond())) {
        result.add(changeList);
      }
    }

    return result;
  }
}
