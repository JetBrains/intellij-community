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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.PairConsumer;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnCommittedChangesProvider;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.jetbrains.idea.svn.history.TreeStructureNode;
import org.jetbrains.idea.svn.mergeinfo.OneShotMergeInfoHelper;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.util.ArrayList;
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
  private final Integer myTestBunchSize;
  @NotNull private final MergeContext myMergeContext;

  public LoadRecentBranchRevisions(@NotNull MergeContext mergeContext, long first) {
    this(mergeContext, first, -1);
  }

  public LoadRecentBranchRevisions(@NotNull MergeContext mergeContext, long first, int bunchSize) {
    super("Loading recent " + mergeContext.getBranchName() + " revisions", Where.POOLED);
    myMergeContext = mergeContext;
    myFirst = first;
    // for test purposes!!!
    myTestBunchSize = Integer.getInteger(PROP_BUNCH_SIZE);
    if (myTestBunchSize != null) {
      myBunchSize = myTestBunchSize.intValue();
    } else {
      myBunchSize = bunchSize > 0 ? bunchSize : BUNCH_SIZE;
    }
  }

  public boolean isLastLoaded() {
    return myLastLoaded;
  }

  @Override
  public void run(ContinuationContext context) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    final SvnCommittedChangesProvider committedChangesProvider =
      (SvnCommittedChangesProvider)myMergeContext.getVcs().getCommittedChangesProvider();
    final ChangeBrowserSettings settings = new ChangeBrowserSettings();
    if (myFirst > 0){
      settings.CHANGE_BEFORE = String.valueOf(myFirst);
      settings.USE_CHANGE_BEFORE_FILTER = true;
    }

    String local = SVNPathUtil.getRelativePath(myMergeContext.getWcInfo().getRepositoryRoot(), myMergeContext.getWcInfo().getRootUrl());
    final String relativeLocal = (local.startsWith("/") ? local : "/" + local);
    String relativeBranch = SVNPathUtil.getRelativePath(myMergeContext.getWcInfo().getRepositoryRoot(), myMergeContext.getSourceUrl());
    relativeBranch = (relativeBranch.startsWith("/") ? relativeBranch : "/" + relativeBranch);

    ProgressManager.progress2(
      SvnBundle.message("progress.text2.collecting.history", myMergeContext.getSourceUrl() + (myFirst > 0 ? ("@" + myFirst) : "")));
    final List<Pair<SvnChangeList, TreeStructureNode<SVNLogEntry>>> list = new ArrayList<Pair<SvnChangeList, TreeStructureNode<SVNLogEntry>>>();
    try {
      committedChangesProvider.getCommittedChangesWithMergedRevisons(settings, new SvnRepositoryLocation(myMergeContext.getSourceUrl()),
                                                                     myBunchSize + (myFirst > 0 ? 2 : 1),
                                                                     new PairConsumer<SvnChangeList, TreeStructureNode<SVNLogEntry>>() {
                                                                       public void consume(SvnChangeList svnList, TreeStructureNode<SVNLogEntry> tree) {
                                                                         indicator.setText2(SvnBundle.message("progress.text2.processing.revision", svnList.getNumber()));
                                                                         list.add(Pair.create(svnList, tree));
                                                                       }
                                                                     });
    } catch (VcsException e) {
      context.handleException(e, true);
      return;
    }
    myCommittedChangeLists = new ArrayList<CommittedChangeList>();
    for (Pair<SvnChangeList, TreeStructureNode<SVNLogEntry>> pair : list) {
      // do not take first since it's equal
      if (myFirst > 0 && myFirst == pair.getFirst().getNumber()) continue;
      // TODO: Currently path filtering with QuickMerge.checkListForPaths is not applied as it removes some necessary revisions
      // TODO: (merge revisions) from list. Check if that filtering was really necessary here - in "Quick merge".
//      if (! QuickMerge.checkListForPaths(relativeLocal, relativeBranch, pair)) {
        myCommittedChangeLists.add(pair.getFirst());
//      }
    }

    try {
      myHelper = new OneShotMergeInfoHelper(myMergeContext);
      ProgressManager.progress2("Calculating not merged revisions");
      myHelper.prepare();
    }
    catch (VcsException e) {
      context.handleException(e, true);
    }
    myLastLoaded = myCommittedChangeLists.size() < myBunchSize + 1;
    if (myCommittedChangeLists.size() > myBunchSize){
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
