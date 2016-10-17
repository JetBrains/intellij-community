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
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
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
import static java.lang.Math.min;
import static org.jetbrains.idea.svn.SvnBundle.message;

public class LoadRecentBranchRevisions extends BaseMergeTask {
  public static final String PROP_BUNCH_SIZE = "idea.svn.quick.merge.bunch.size";
  private final static int BUNCH_SIZE = 100;

  private final int myBunchSize;
  private final long myFirst;
  private boolean myLastLoaded;
  @NotNull private final OneShotMergeInfoHelper myMergeChecker;
  @NotNull private final List<CommittedChangeList> myCommittedChangeLists;

  public LoadRecentBranchRevisions(@NotNull QuickMerge mergeProcess) {
    this(mergeProcess, -1, -1);
  }

  public LoadRecentBranchRevisions(@NotNull QuickMerge mergeProcess, long first, int bunchSize) {
    super(mergeProcess, "Loading recent " + mergeProcess.getMergeContext().getBranchName() + " revisions", Where.POOLED);
    myFirst = first;
    myCommittedChangeLists = newArrayList();
    myMergeChecker = new OneShotMergeInfoHelper(myMergeContext);

    Integer testBunchSize = Integer.getInteger(PROP_BUNCH_SIZE);
    myBunchSize = testBunchSize != null ? testBunchSize.intValue() : (bunchSize > 0 ? bunchSize : BUNCH_SIZE);
  }

  public boolean isLastLoaded() {
    return myLastLoaded;
  }

  /**
   * TODO: Try to unify collecting and filtering change lists with similar logic in MergeCalculatorTask.
   */
  @Override
  public void run() throws VcsException {
    ProgressManager.progress2("Calculating not merged revisions");
    myMergeChecker.prepare();

    List<CommittedChangeList> notMergedChangeLists = getNotMergedChangeLists(getChangeListsBefore(myFirst));
    myLastLoaded = notMergedChangeLists.size() < myBunchSize + 1;
    myCommittedChangeLists.addAll(notMergedChangeLists.subList(0, min(myBunchSize, notMergedChangeLists.size())));
  }

  @NotNull
  private List<Pair<SvnChangeList, LogHierarchyNode>> getChangeListsBefore(long revision) throws VcsException {
    ChangeBrowserSettings settings = new ChangeBrowserSettings();
    if (revision > 0) {
      settings.CHANGE_BEFORE = String.valueOf(revision);
      settings.USE_CHANGE_BEFORE_FILTER = true;
    }

    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    ProgressManager
      .progress2(message("progress.text2.collecting.history", myMergeContext.getSourceUrl() + (revision > 0 ? ("@" + revision) : "")));
    List<Pair<SvnChangeList, LogHierarchyNode>> result = newArrayList();

    ((SvnCommittedChangesProvider)myMergeContext.getVcs().getCommittedChangesProvider())
      .getCommittedChangesWithMergedRevisons(settings, new SvnRepositoryLocation(myMergeContext.getSourceUrl()),
                                             myBunchSize + (revision > 0 ? 2 : 1),
                                             (list, tree) -> {
                                               indicator.setText2(message("progress.text2.processing.revision", list.getNumber()));
                                               result.add(Pair.create(list, tree));
                                             });
    return result;
  }

  @NotNull
  private List<CommittedChangeList> getNotMergedChangeLists(@NotNull List<Pair<SvnChangeList, LogHierarchyNode>> changeLists) {
    List<CommittedChangeList> result = newArrayList();

    for (Pair<SvnChangeList, LogHierarchyNode> pair : changeLists) {
      // do not take first since it's equal
      if (myFirst <= 0 || myFirst != pair.getFirst().getNumber()) {
        // TODO: Currently path filtering from MergeCalculatorTask.checkListForPaths is not applied as it removes some necessary revisions
        // TODO: (i.e. merge revisions) from list. Check if that filtering is really necessary for "Quick Manual Select" option.
        result.add(pair.getFirst());
      }
    }

    return result;
  }

  @NotNull
  public MergeChecker getMergeChecker() {
    return myMergeChecker;
  }

  @NotNull
  public List<CommittedChangeList> getChangeLists() {
    return myCommittedChangeLists;
  }
}
