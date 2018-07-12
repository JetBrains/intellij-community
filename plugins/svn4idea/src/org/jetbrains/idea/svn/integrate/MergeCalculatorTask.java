// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.util.Consumer;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.history.LogHierarchyNode;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnCommittedChangesProvider;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;
import org.jetbrains.idea.svn.mergeinfo.OneShotMergeInfoHelper;

import java.util.List;

import static com.intellij.openapi.progress.ProgressManager.progress;
import static com.intellij.openapi.progress.ProgressManager.progress2;
import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static java.lang.Math.min;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache.MergeCheckResult;

public class MergeCalculatorTask extends BaseMergeTask {

  public static final String PROP_BUNCH_SIZE = "idea.svn.quick.merge.bunch.size";
  private final static int BUNCH_SIZE = 100;

  @Nullable private final SvnBranchPointsCalculator.WrapperInvertor myCopyPoint;
  @NotNull private final OneShotMergeInfoHelper myMergeChecker;
  @NotNull private final List<SvnChangeList> myChangeLists;
  @NotNull private final Consumer<MergeCalculatorTask> myCallback;
  private boolean myAllListsLoaded;

  public MergeCalculatorTask(@NotNull QuickMerge mergeProcess,
                             @Nullable SvnBranchPointsCalculator.WrapperInvertor copyPoint,
                             @NotNull Consumer<MergeCalculatorTask> callback) {
    super(mergeProcess);
    myCopyPoint = copyPoint;
    myCallback = callback;
    myChangeLists = newArrayList();
    // TODO: Previously it was configurable - either to use OneShotMergeInfoHelper or BranchInfo as merge checker, but later that logic
    // TODO: was commented (in 80ebdbfea5210f6c998e67ddf28ca9c670fa4efe on 5/28/2010).
    // TODO: Still check if we need to preserve such configuration or it is sufficient to always use OneShotMergeInfoHelper.
    myMergeChecker = new OneShotMergeInfoHelper(myMergeContext);
  }

  public boolean areAllListsLoaded() {
    return myAllListsLoaded;
  }

  @NotNull
  public MergeChecker getMergeChecker() {
    return myMergeChecker;
  }

  @NotNull
  public List<SvnChangeList> getChangeLists() {
    return myChangeLists;
  }

  @Override
  public void run() throws VcsException {
    progress("Collecting merge information");
    myMergeChecker.prepare();

    if (myCopyPoint != null) {
      myChangeLists.addAll(getNotMergedChangeLists(getChangeListsAfter(myCopyPoint.getTrue().getTargetRevision())));
      myAllListsLoaded = true;
    }
    else {
      Pair<List<SvnChangeList>, Boolean> loadResult = loadChangeLists(myMergeContext, -1, getBunchSize(-1));

      myChangeLists.addAll(loadResult.first);
      myAllListsLoaded = loadResult.second;
    }

    if (!myChangeLists.isEmpty()) {
      myCallback.consume(this);
    }
    else {
      myMergeProcess.end("Everything is up-to-date", false);
    }
  }

  @NotNull
  private List<Pair<SvnChangeList, LogHierarchyNode>> getChangeListsAfter(long revision) throws VcsException {
    ChangeBrowserSettings settings = new ChangeBrowserSettings();
    settings.CHANGE_AFTER = Long.toString(revision);
    settings.USE_CHANGE_AFTER_FILTER = true;

    return getChangeLists(myMergeContext, settings, revision, -1, Pair::create);
  }

  @NotNull
  private List<SvnChangeList> getNotMergedChangeLists(@NotNull List<Pair<SvnChangeList, LogHierarchyNode>> changeLists) {
    List<SvnChangeList> result = newArrayList();

    progress("Collecting not merged revisions");
    for (Pair<SvnChangeList, LogHierarchyNode> pair : changeLists) {
      SvnChangeList changeList = pair.getFirst();

      progress2(message("progress.text2.processing.revision", changeList.getNumber()));
      if (MergeCheckResult.NOT_MERGED.equals(myMergeChecker.checkList(changeList)) && !myMergeChecker.checkListForPaths(pair.getSecond())) {
        result.add(changeList);
      }
    }

    return result;
  }

  @NotNull
  public static Pair<List<SvnChangeList>, Boolean> loadChangeLists(@NotNull MergeContext mergeContext, long beforeRevision, int size)
    throws VcsException {
    ChangeBrowserSettings settings = new ChangeBrowserSettings();
    if (beforeRevision > 0) {
      settings.CHANGE_BEFORE = String.valueOf(beforeRevision);
      settings.USE_CHANGE_BEFORE_FILTER = true;
    }

    List<SvnChangeList> changeLists = getChangeLists(mergeContext, settings, beforeRevision, size, (changeList, tree) -> changeList);
    return Pair.create(
      changeLists.subList(0, min(size, changeLists.size())),
      changeLists.size() < size + 1);
  }

  public static int getBunchSize(int size) {
    Integer configuredSize = Integer.getInteger(PROP_BUNCH_SIZE);

    return configuredSize != null ? configuredSize : size > 0 ? size : BUNCH_SIZE;
  }

  @NotNull
  private static <T> List<T> getChangeLists(@NotNull MergeContext mergeContext,
                                            @NotNull ChangeBrowserSettings settings,
                                            long revisionToExclude,
                                            int size,
                                            @NotNull PairFunction<SvnChangeList, LogHierarchyNode, T> resultProvider) throws VcsException {
    List<T> result = newArrayList();

    ((SvnCommittedChangesProvider)mergeContext.getVcs().getCommittedChangesProvider())
      .getCommittedChangesWithMergedRevisons(settings, new SvnRepositoryLocation(mergeContext.getSourceUrl()),
                                             size > 0 ? size + (revisionToExclude > 0 ? 2 : 1) : 0,
                                             (changeList, tree) -> {
                                               if (revisionToExclude != changeList.getNumber()) {
                                                 result.add(resultProvider.fun(changeList, tree));
                                               }
                                             });

    return result;
  }
}
