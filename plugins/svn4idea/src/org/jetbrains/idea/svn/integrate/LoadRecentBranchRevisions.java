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
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.history.LogHierarchyNode;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnCommittedChangesProvider;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.jetbrains.idea.svn.mergeinfo.OneShotMergeInfoHelper;

import java.util.List;

/**
* Created with IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 3/30/13
* Time: 7:40 PM
*/
public class LoadRecentBranchRevisions extends TaskDescriptor {
  public static final String PROP_BUNCH_SIZE = "idea.svn.quick.merge.bunch.size";
  private final static int BUNCH_SIZE = 100;
  private int myBunchSize;
  private long myFirst;
  private boolean myLastLoaded;
  private OneShotMergeInfoHelper myHelper;
  private List<CommittedChangeList> myCommittedChangeLists;
  @NotNull private final MergeContext myMergeContext;

  public LoadRecentBranchRevisions(@NotNull MergeContext mergeContext, long first) {
    this(mergeContext, first, -1);
  }

  public LoadRecentBranchRevisions(@NotNull MergeContext mergeContext, long first, int bunchSize) {
    super("Loading recent " + mergeContext.getBranchName() + " revisions", Where.POOLED);
    myMergeContext = mergeContext;
    myFirst = first;

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
  public void run(ContinuationContext context) {
    List<Pair<SvnChangeList, LogHierarchyNode>> changeLists = null;

    try {
      changeLists = getChangeListsBefore(myFirst);
    }
    catch (VcsException e) {
      context.handleException(e, true);
    }

    if (changeLists != null) {
      initialize(context, changeLists);
    }
  }

  @NotNull
  private List<Pair<SvnChangeList, LogHierarchyNode>> getChangeListsBefore(long revision) throws VcsException {
    ChangeBrowserSettings settings = new ChangeBrowserSettings();
    if (revision > 0) {
      settings.CHANGE_BEFORE = String.valueOf(revision);
      settings.USE_CHANGE_BEFORE_FILTER = true;
    }

    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    ProgressManager.progress2(
      SvnBundle.message("progress.text2.collecting.history", myMergeContext.getSourceUrl() + (revision > 0 ? ("@" + revision) : "")));
    final List<Pair<SvnChangeList, LogHierarchyNode>> result = ContainerUtil.newArrayList();

    ((SvnCommittedChangesProvider)myMergeContext.getVcs().getCommittedChangesProvider())
      .getCommittedChangesWithMergedRevisons(settings, new SvnRepositoryLocation(myMergeContext.getSourceUrl()),
                                             myBunchSize + (revision > 0 ? 2 : 1),
                                             new PairConsumer<SvnChangeList, LogHierarchyNode>() {
                                               public void consume(SvnChangeList svnList, LogHierarchyNode tree) {
                                                 indicator.setText2(
                                                   SvnBundle.message("progress.text2.processing.revision", svnList.getNumber()));
                                                 result.add(Pair.create(svnList, tree));
                                               }
                                             });

    return result;
  }

  @NotNull
  private List<CommittedChangeList> getNotMergedChangeLists(@NotNull List<Pair<SvnChangeList, LogHierarchyNode>> changeLists) {
    List<CommittedChangeList> result = ContainerUtil.newArrayList();

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

  private void initialize(@NotNull ContinuationContext context, @NotNull List<Pair<SvnChangeList, LogHierarchyNode>> changeLists) {
    myCommittedChangeLists = getNotMergedChangeLists(changeLists);

    try {
      myHelper = new OneShotMergeInfoHelper(myMergeContext);
      ProgressManager.progress2("Calculating not merged revisions");
      myHelper.prepare();
    }
    catch (VcsException e) {
      context.handleException(e, true);
    }

    myLastLoaded = myCommittedChangeLists.size() < myBunchSize + 1;
    if (myCommittedChangeLists.size() > myBunchSize) {
      myCommittedChangeLists = myCommittedChangeLists.subList(0, myBunchSize);
    }
  }

  public OneShotMergeInfoHelper getHelper() {
    return myHelper;
  }

  public List<CommittedChangeList> getCommittedChangeLists() {
    return myCommittedChangeLists;
  }
}
