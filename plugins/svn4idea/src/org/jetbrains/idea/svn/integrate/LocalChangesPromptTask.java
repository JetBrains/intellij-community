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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.FilePathByPathComparator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.Where;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Kolosovsky.
 */
public class LocalChangesPromptTask extends BaseMergeTask {

  private final boolean myMergeAll;
  @Nullable private final List<CommittedChangeList> myChangeListsToMerge;
  private final SvnBranchPointsCalculator.WrapperInvertor myCopyPoint;

  public LocalChangesPromptTask(@NotNull MergeContext mergeContext,
                                @NotNull QuickMergeInteraction interaction,
                                boolean mergeAll,
                                @Nullable List<CommittedChangeList> changeListsToMerge,
                                @Nullable SvnBranchPointsCalculator.WrapperInvertor copyPoint) {
    super(mergeContext, interaction, "local changes intersection check", Where.AWT);

    myMergeAll = mergeAll;
    myChangeListsToMerge = changeListsToMerge;
    myCopyPoint = copyPoint;
  }

  @Nullable
  private File getLocalPath(String repositoryRelativePath) {
    // from source if not inverted
    final String absolutePath = SVNPathUtil.append(myMergeContext.getWcInfo().getRepositoryRoot(), repositoryRelativePath);
    final SvnBranchPointsCalculator.BranchCopyData wrapped = myCopyPoint.getWrapped();
    final String sourceRelativePath =
      SVNPathUtil.getRelativePath(myCopyPoint.isInvertedSense() ? wrapped.getSource() : wrapped.getTarget(), absolutePath);

    return !StringUtil.isEmptyOrSpaces(sourceRelativePath) ? new File(myMergeContext.getWcInfo().getPath(), sourceRelativePath) : null;
  }

  @Override
  public void run(ContinuationContext context) {
    List<LocalChangeList> localChangeLists = ChangeListManager.getInstance(myMergeContext.getProject()).getChangeListsCopy();
    Intersection intersection =
      myMergeAll
      ? getAllChangesIntersection(localChangeLists)
      : getChangesIntersection(localChangeLists, myChangeListsToMerge);

    if (intersection != null && !intersection.getChangesSubset().isEmpty()) {
      processIntersection(context, intersection);
    }
  }

  private void processIntersection(@NotNull ContinuationContext context, @NotNull Intersection intersection) {
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (myInteraction.selectLocalChangesAction(myMergeAll)) {
      case shelve:
        context.next(new ShelveLocalChangesTask(myMergeContext, myInteraction, intersection));
        break;
      case cancel:
        context.cancelEverything();
        break;
      case inspect:
        // here's cast is due to generic's bug
        @SuppressWarnings("unchecked") Collection<Change> changes = (Collection<Change>)intersection.getChangesSubset().values();
        myInteraction
          .showIntersectedLocalPaths(ContainerUtil.sorted(ChangesUtil.getPaths(changes), FilePathByPathComparator.getInstance()));
        context.cancelEverything();
        break;
    }
  }

  @Nullable
  private Intersection getChangesIntersection(@NotNull List<LocalChangeList> localChangeLists,
                                              @Nullable List<CommittedChangeList> changeListsToMerge) {
    Intersection result = null;

    if (!ContainerUtil.isEmpty(changeListsToMerge)) {
      final Set<FilePath> pathsToMerge = collectPaths(changeListsToMerge);

      result = getChangesIntersection(localChangeLists, new Condition<Change>() {
        @Override
        public boolean value(Change change) {
          return notNullAndInSet(ChangesUtil.getBeforePath(change), pathsToMerge) ||
                 notNullAndInSet(ChangesUtil.getAfterPath(change), pathsToMerge);
        }
      });
    }

    return result;
  }

  @NotNull
  private Set<FilePath> collectPaths(@NotNull List<CommittedChangeList> lists) {
    Set<FilePath> result = new HashSet<>();

    for (CommittedChangeList list : lists) {
      SvnChangeList svnList = (SvnChangeList)list;

      for (String path : svnList.getAffectedPaths()) {
        File localPath = getLocalPath(path);

        if (localPath != null) {
          result.add(VcsUtil.getFilePath(localPath, false));
        }
      }
    }

    return result;
  }

  @NotNull
  private static Intersection getAllChangesIntersection(@NotNull List<LocalChangeList> localChangeLists) {
    return getChangesIntersection(localChangeLists, Conditions.<Change>alwaysTrue());
  }

  @NotNull
  private static Intersection getChangesIntersection(@NotNull List<LocalChangeList> changeLists, @NotNull Condition<Change> filter) {
    Intersection result = new Intersection();

    for (LocalChangeList changeList : changeLists) {
      for (Change change : changeList.getChanges()) {
        if (filter.value(change)) {
          result.add(changeList.getName(), changeList.getComment(), change);
        }
      }
    }

    return result;
  }

  private static boolean notNullAndInSet(@Nullable FilePath path, @NotNull Set<FilePath> items) {
    return path != null && items.contains(path);
  }
}
