// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListUtil;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.util.FilePathByPathComparator;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.history.SvnChangeList;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.util.Conditions.alwaysTrue;
import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.openapi.vcs.changes.ChangesUtil.*;
import static com.intellij.util.containers.ContainerUtil.sorted;
import static java.util.stream.Collectors.toSet;
import static org.jetbrains.idea.svn.SvnUtil.*;
import static org.jetbrains.idea.svn.integrate.Intersection.isEmpty;
import static org.jetbrains.idea.svn.integrate.LocalChangesAction.continueMerge;

public class LocalChangesPromptTask extends BaseMergeTask {

  private static final Logger LOG = Logger.getInstance(LocalChangesPromptTask.class);

  @Nullable private final List<SvnChangeList> myChangeListsToMerge;
  @NotNull private final Runnable myCallback;

  public LocalChangesPromptTask(@NotNull QuickMerge mergeProcess,
                                @Nullable List<SvnChangeList> changeListsToMerge,
                                @NotNull Runnable callback) {
    super(mergeProcess);
    myChangeListsToMerge = changeListsToMerge;
    myCallback = callback;
  }

  @Nullable
  private File getLocalPath(@NotNull String repositoryRelativePath) {
    try {
      Url url = append(myMergeContext.getWcInfo().getRepoUrl(), repositoryRelativePath);

      return isAncestor(myMergeContext.getSourceUrl(), url)
             ? new File(myMergeContext.getWcInfo().getPath(), getRelativeUrl(myMergeContext.getSourceUrl(), url))
             : null;
    }
    catch (SvnBindException e) {
      LOG.info(e);
      return null;
    }
  }

  @Override
  public void run() {
    List<LocalChangeList> localChangeLists = ChangeListManager.getInstance(myMergeContext.getProject()).getChangeListsCopy();
    Intersection intersection = myChangeListsToMerge != null
                                ? getChangesIntersection(localChangeLists, myChangeListsToMerge)
                                : getAllChangesIntersection(localChangeLists);

    processIntersection(intersection);
  }

  private void processIntersection(@Nullable Intersection intersection) {
    boolean mergeAll = myChangeListsToMerge == null;
    LocalChangesAction nextAction = !isEmpty(intersection) ? myInteraction.selectLocalChangesAction(mergeAll) : continueMerge;

    switch (nextAction) {
      case continueMerge:
        myCallback.run();
        break;
      case shelve:
        myMergeProcess.runInBackground("Shelving local changes before merge", indicator -> {
          shelveChanges(intersection);
          myCallback.run();
        });
        break;
      case inspect:
        List<FilePath> intersectedPaths = sorted(getPaths(intersection.getAllChanges()), FilePathByPathComparator.getInstance());
        myInteraction.showIntersectedLocalPaths(intersectedPaths);
        break;
      case cancel:
        break;
    }
  }

  private void shelveChanges(@NotNull Intersection intersection) throws VcsException {
    try {
      getApplication().invokeAndWait(() -> FileDocumentManager.getInstance().saveAllDocuments());

      ShelveChangesManager shelveManager = ShelveChangesManager.getInstance(myMergeContext.getProject());

      for (Map.Entry<String, List<Change>> entry : intersection.getChangesByLists().entrySet()) {
        String shelfName = ChangeListUtil
          .createSystemShelvedChangeListName(message("stash.changes.message", "merge"), intersection.getComment(entry.getKey()));

        shelveManager.shelveChanges(entry.getValue(), shelfName, true, true);
      }
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  @Nullable
  private Intersection getChangesIntersection(@NotNull List<LocalChangeList> localChangeLists,
                                              @NotNull List<SvnChangeList> changeListsToMerge) {
    Set<FilePath> pathsToMerge = collectPaths(changeListsToMerge);

    return !changeListsToMerge.isEmpty() ? getChangesIntersection(localChangeLists, change -> hasPathToMerge(change, pathsToMerge)) : null;
  }

  @NotNull
  private Set<FilePath> collectPaths(@NotNull List<SvnChangeList> lists) {
    return lists.stream()
      .flatMap(list -> list.getAffectedPaths().stream())
      .map(this::getLocalPath)
      .filter(Objects::nonNull)
      .map(localPath -> VcsUtil.getFilePath(localPath, false))
      .collect(toSet());
  }

  @NotNull
  private static Intersection getAllChangesIntersection(@NotNull List<LocalChangeList> localChangeLists) {
    return getChangesIntersection(localChangeLists, alwaysTrue());
  }

  @NotNull
  private static Intersection getChangesIntersection(@NotNull List<LocalChangeList> changeLists, @NotNull Condition<Change> filter) {
    Intersection result = new Intersection();

    for (LocalChangeList changeList : changeLists) {
      for (Change change : changeList.getChanges()) {
        if (filter.value(change)) {
          result.add(changeList, change);
        }
      }
    }

    return result;
  }

  private static boolean hasPathToMerge(@NotNull Change change, @NotNull Set<FilePath> pathsToMerge) {
    FilePath beforePath = getBeforePath(change);
    FilePath afterPath = getAfterPath(change);

    return beforePath != null && pathsToMerge.contains(beforePath) || afterPath != null && pathsToMerge.contains(afterPath);
  }
}
