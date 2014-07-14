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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.FilePathByPathComparator;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.dialogs.LocalChangesAction;
import org.jetbrains.idea.svn.dialogs.MergeContext;
import org.jetbrains.idea.svn.dialogs.SvnBranchPointsCalculator;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.io.File;
import java.util.*;

/**
 * @author Konstantin Kolosovsky.
 */
public class LocalChangesPromptTask extends BaseMergeTask {
  private final boolean myMergeAll;
  @Nullable private final List<CommittedChangeList> myLists;
  private final SvnBranchPointsCalculator.WrapperInvertor myCopyPoint;

  public LocalChangesPromptTask(@NotNull MergeContext mergeContext, @NotNull QuickMergeInteraction interaction, final boolean mergeAll,
                                @Nullable final List<CommittedChangeList> lists,
                                @Nullable SvnBranchPointsCalculator.WrapperInvertor copyPoint) {
    super(mergeContext, interaction, "local changes intersection check", Where.AWT);
    myMergeAll = mergeAll;
    myLists = lists;
    myCopyPoint = copyPoint;
  }

  private static Intersection getMergeAllIntersection(List<LocalChangeList> localChangeLists) {
    final Intersection intersection = new Intersection();

    for (LocalChangeList localChangeList : localChangeLists) {
      final Collection<Change> localChanges = localChangeList.getChanges();
      for (Change localChange : localChanges) {
        intersection.add(localChangeList.getName(), localChangeList.getComment(), localChange);
      }
    }
    return intersection;
  }

  @Nullable
  private File getLocalPath(final String relativeToRepoPath) {
    // from source if not inverted
    final String pathToCheck = SVNPathUtil.append(myMergeContext.getWcInfo().getRepositoryRoot(), relativeToRepoPath);
    final SvnBranchPointsCalculator.BranchCopyData wrapped = myCopyPoint.getWrapped();
    final String relativeInSource =
      SVNPathUtil.getRelativePath(myCopyPoint.isInvertedSense() ? wrapped.getSource() : wrapped.getTarget(), pathToCheck);
    if (StringUtil.isEmptyOrSpaces(relativeInSource)) return null;
    final File local = new File(myMergeContext.getWcInfo().getPath(), relativeInSource);
    return local;
  }

  @Override
  public void run(ContinuationContext context) {
    final Intersection intersection;
    final ChangeListManager listManager = ChangeListManager.getInstance(myMergeContext.getProject());
    final List<LocalChangeList> localChangeLists = listManager.getChangeListsCopy();

    if (myMergeAll) {
      intersection = getMergeAllIntersection(localChangeLists);
    }
    else {
      intersection = checkIntersection(myLists, localChangeLists);
    }
    if (intersection == null || intersection.getChangesSubset().isEmpty()) return;

    final LocalChangesAction action = myInteraction.selectLocalChangesAction(myMergeAll);
    switch (action) {
      // shelve
      case shelve:
        context.next(new ShelveLocalChangesTask(myMergeContext, myInteraction, intersection));
        return;
      // cancel
      case cancel:
        context.cancelEverything();
        return;
      // continue
      case continueMerge:
        return;
      // inspect
      case inspect:
        // here's cast is due to generic's bug
        @SuppressWarnings("unchecked") final Collection<Change> changes = (Collection<Change>)intersection.getChangesSubset().values();
        final List<FilePath> paths = ChangesUtil.getPaths(changes);
        Collections.sort(paths, FilePathByPathComparator.getInstance());
        myInteraction.showIntersectedLocalPaths(paths);
        context.cancelEverything();
        return;
      default:
    }
  }

  @Nullable
  private Intersection checkIntersection(@Nullable final List<CommittedChangeList> lists, List<LocalChangeList> localChangeLists) {
    if (lists == null || lists.isEmpty()) {
      return null;
    }
    final Set<FilePath> mergePaths = new HashSet<FilePath>();
    for (CommittedChangeList list : lists) {
      final SvnChangeList svnList = (SvnChangeList)list;
      final List<String> paths = new ArrayList<String>(svnList.getAddedPaths());
      paths.addAll(svnList.getChangedPaths());
      paths.addAll(svnList.getDeletedPaths());
      for (String path : paths) {
        final File localPath = getLocalPath(path);
        if (localPath != null) {
          mergePaths.add(new FilePathImpl(localPath, false));
        }
      }
    }

    final Intersection intersection = new Intersection();
    for (LocalChangeList localChangeList : localChangeLists) {
      final Collection<Change> localChanges = localChangeList.getChanges();

      for (Change localChange : localChanges) {
        final FilePath before = localChange.getBeforeRevision() == null ? null : localChange.getBeforeRevision().getFile();
        final FilePath after = localChange.getAfterRevision() == null ? null : localChange.getAfterRevision().getFile();

        if ((before != null && mergePaths.contains(before)) || (after != null && mergePaths.contains(after))) {
          intersection.add(localChangeList.getName(), localChangeList.getComment(), localChange);
        }
      }
    }
    return intersection;
  }
}
