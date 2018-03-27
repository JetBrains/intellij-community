// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.ErrorCode;
import org.jetbrains.idea.svn.change.ChangeListClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.status.Status;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class SvnChangelistListener implements ChangeListListener {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnChangelistListener");

  @NotNull private final SvnVcs myVcs;
  @NotNull private final Condition<FilePath> myUnderSvnCondition;

  public SvnChangelistListener(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    myUnderSvnCondition = path -> {
      final AbstractVcs vcs1 = ProjectLevelVcsManager.getInstance(myVcs.getProject()).getVcsFor(path);
      return vcs1 != null && SvnVcs.VCS_NAME.equals(vcs1.getName());
    };
  }

  public void changeListAdded(final ChangeList list) {
    // SVN change list exists only when there are any files in it
  }

  public void changesRemoved(final Collection<Change> changes, final ChangeList fromList) {
    if (LocalChangeList.DEFAULT_NAME.equals(fromList.getName())) {
      return;
    }
    removeFromChangeList(changes);
  }

  public void changesAdded(Collection<Change> changes, ChangeList toList) {
    if (toList == null || LocalChangeList.DEFAULT_NAME.equals(toList.getName())) {
      return;
    }
    addToChangeList(toList.getName(), changes);
  }

  public void changeListRemoved(final ChangeList list) {
    removeFromChangeList(list.getChanges());
  }

  @NotNull
  private List<FilePath> getPathsFromChanges(@NotNull Collection<Change> changes) {
    return ContainerUtil.findAll(ChangesUtil.getPaths(changes), myUnderSvnCondition);
  }

  public void changeListChanged(final ChangeList list) {
  }

  public void changeListRenamed(final ChangeList list, final String oldName) {
    if (Comparing.equal(list.getName(), oldName)) {
      return;
    }
    if (LocalChangeList.DEFAULT_NAME.equals(list.getName())) {
      changeListRemoved(list);
      return;
    }
    addToChangeList(list.getName(), list.getChanges());
  }

  public void changeListCommentChanged(final ChangeList list, final String oldComment) {
  }

  public void changesMoved(final Collection<Change> changes, final ChangeList fromList, final ChangeList toList) {
    if (fromList.getName().equals(toList.getName())) {
      return;
    }
    if (LocalChangeList.DEFAULT_NAME.equals(toList.getName())) {
      changeListRemoved(toList);
      return;
    }

    final String[] fromLists = LocalChangeList.DEFAULT_NAME.equals(fromList.getName()) ? null : new String[] {fromList.getName()};
    addToChangeList(toList.getName(), changes, fromLists);
  }

  public void defaultListChanged(final ChangeList oldDefaultList, final ChangeList newDefaultList) {
  }

  public void unchangedFileStatusChanged() {
  }

  public void changeListUpdateDone() {
  }

  @Nullable
  public static String getCurrentMapping(@NotNull SvnVcs vcs, @NotNull File file) {
    try {
      final Status status = vcs.getFactory(file).createStatusClient().doStatus(file, false);
      return status == null ? null : status.getChangelistName();
    }
    catch (SvnBindException e) {
      if (e.contains(ErrorCode.WC_NOT_WORKING_COPY) || e.contains(ErrorCode.WC_NOT_FILE)) {
        LOG.debug("Logging only, exception is valid (caught) here", e);
      } else {
        LOG.info("Logging only, exception is valid (caught) here", e);
      }
    }
    return null;
  }

  public static void putUnderList(@NotNull SvnVcs vcs, @NotNull String list, @NotNull File after) throws VcsException {
    doChangeListOperation(vcs, after, client -> client.add(list, after, null));
  }

  public static void removeFromList(@NotNull SvnVcs vcs, @NotNull File after) throws VcsException {
    doChangeListOperation(vcs, after, client -> client.remove(after));
  }

  private static void doChangeListOperation(@NotNull SvnVcs vcs,
                                            @NotNull File file,
                                            @NotNull ThrowableConsumer<ChangeListClient, VcsException> operation) throws VcsException {
    try {
      operation.consume(vcs.getFactory(file).createChangeListClient());
    }
    catch (SvnBindException e) {
      LOG.info(e);
      if (!e.contains(ErrorCode.WC_NOT_WORKING_COPY) && !e.contains(ErrorCode.WC_NOT_FILE)) {
        throw e;
      }
    }
    catch (VcsException e) {
      LOG.info(e);
      throw e;
    }
  }

  private void removeFromChangeList(@NotNull Collection<Change> changes) {
    for (FilePath path : getPathsFromChanges(changes)) {
      try {
        File file = path.getIOFile();
        myVcs.getFactory(file).createChangeListClient().remove(file);
      }
      catch (VcsException e) {
        LOG.info(e);
      }
    }
  }

  private void addToChangeList(@NotNull String changeList, @NotNull Collection<Change> changes) {
    addToChangeList(changeList, changes, null);
  }

  private void addToChangeList(@NotNull String changeList, @NotNull Collection<Change> changes, @Nullable String[] changeListsToOperate) {
    for (FilePath path : getPathsFromChanges(changes)) {
      try {
        File file = path.getIOFile();
        myVcs.getFactory(file).createChangeListClient().add(changeList, file, changeListsToOperate);
      }
      catch (VcsException e) {
        LOG.info(e);
      }
    }
  }
}
