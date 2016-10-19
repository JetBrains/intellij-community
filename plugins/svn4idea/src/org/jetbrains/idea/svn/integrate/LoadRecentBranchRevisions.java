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
import static java.lang.Math.min;

public class LoadRecentBranchRevisions extends BaseMergeTask {
  public static final String PROP_BUNCH_SIZE = "idea.svn.quick.merge.bunch.size";
  private final static int BUNCH_SIZE = 100;

  private boolean myLastLoaded;
  @NotNull private final Consumer<LoadRecentBranchRevisions> myCallback;
  @NotNull private final OneShotMergeInfoHelper myMergeChecker;
  @NotNull private final List<SvnChangeList> myCommittedChangeLists;

  public LoadRecentBranchRevisions(@NotNull QuickMerge mergeProcess, @NotNull Consumer<LoadRecentBranchRevisions> callback) {
    super(mergeProcess, "Loading recent " + mergeProcess.getMergeContext().getBranchName() + " revisions", Where.POOLED);
    myCallback = callback;
    myCommittedChangeLists = newArrayList();
    myMergeChecker = new OneShotMergeInfoHelper(myMergeContext);
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

    Pair<List<SvnChangeList>, Boolean> loadResult = loadChangeLists(myMergeContext, -1, getBunchSize(-1));
    myCommittedChangeLists.addAll(loadResult.first);
    myLastLoaded = loadResult.second;

    myCallback.consume(this);
  }

  @NotNull
  public static Pair<List<SvnChangeList>, Boolean> loadChangeLists(@NotNull MergeContext mergeContext, long beforeRevision, int size)
    throws VcsException {
    List<SvnChangeList> changeLists = getNotMergedChangeLists(getChangeListsBefore(mergeContext, beforeRevision, size), beforeRevision);

    return Pair.create(
      changeLists.subList(0, min(size, changeLists.size())),
      changeLists.size() < size + 1);
  }

  public static int getBunchSize(int size) {
    Integer configuredSize = Integer.getInteger(PROP_BUNCH_SIZE);

    return configuredSize != null ? configuredSize : size > 0 ? size : BUNCH_SIZE;
  }

  @NotNull
  private static List<Pair<SvnChangeList, LogHierarchyNode>> getChangeListsBefore(@NotNull MergeContext mergeContext,
                                                                                  long beforeRevision,
                                                                                  int size) throws VcsException {
    ChangeBrowserSettings settings = new ChangeBrowserSettings();
    if (beforeRevision > 0) {
      settings.CHANGE_BEFORE = String.valueOf(beforeRevision);
      settings.USE_CHANGE_BEFORE_FILTER = true;
    }

    List<Pair<SvnChangeList, LogHierarchyNode>> result = newArrayList();

    ((SvnCommittedChangesProvider)mergeContext.getVcs().getCommittedChangesProvider())
      .getCommittedChangesWithMergedRevisons(settings, new SvnRepositoryLocation(mergeContext.getSourceUrl()),
                                             size + (beforeRevision > 0 ? 2 : 1),
                                             (list, tree) -> result.add(Pair.create(list, tree)));
    return result;
  }

  @NotNull
  private static List<SvnChangeList> getNotMergedChangeLists(@NotNull List<Pair<SvnChangeList, LogHierarchyNode>> changeLists,
                                                             long revision) {
    List<SvnChangeList> result = newArrayList();

    for (Pair<SvnChangeList, LogHierarchyNode> pair : changeLists) {
      // do not take first since it's equal
      if (revision <= 0 || revision != pair.getFirst().getNumber()) {
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
  public List<SvnChangeList> getChangeLists() {
    return myCommittedChangeLists;
  }
}
