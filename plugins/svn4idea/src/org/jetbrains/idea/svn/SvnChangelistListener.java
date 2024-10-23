// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
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
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.idea.svn.api.ErrorCode;
import org.jetbrains.idea.svn.change.ChangeListClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.status.Status;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class SvnChangelistListener implements ChangeListListener {
  private final static Logger LOG = Logger.getInstance(SvnChangelistListener.class);

  @NotNull private final SvnVcs myVcs;
  @NotNull private final Condition<FilePath> myUnderSvnCondition;

  public SvnChangelistListener(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    myUnderSvnCondition = path -> {
      final AbstractVcs vcs1 = ProjectLevelVcsManager.getInstance(myVcs.getProject()).getVcsFor(path);
      return vcs1 != null && SvnVcs.VCS_NAME.equals(vcs1.getName());
    };
  }

  @Override
  public void changesRemoved(final Collection<? extends Change> changes, final ChangeList fromList) {
    if (LocalChangeList.getDefaultName().equals(fromList.getName())) {
      return;
    }
    removeFromChangeList(changes);
  }

  @Override
  public void changesAdded(Collection<? extends Change> changes, ChangeList toList) {
    if (toList == null || LocalChangeList.getDefaultName().equals(toList.getName())) {
      return;
    }
    addToChangeList(toList.getName(), changes);
  }

  @Override
  public void changeListRemoved(final ChangeList list) {
    removeFromChangeList(list.getChanges());
  }

  @NotNull
  @Unmodifiable
  private List<FilePath> getPathsFromChanges(@NotNull Collection<? extends Change> changes) {
    return ContainerUtil.findAll(ChangesUtil.getPaths(changes), myUnderSvnCondition);
  }

  @Override
  public void changeListRenamed(final ChangeList list, final String oldName) {
    if (Objects.equals(list.getName(), oldName)) {
      return;
    }
    if (LocalChangeList.getDefaultName().equals(list.getName())) {
      changeListRemoved(list);
      return;
    }
    addToChangeList(list.getName(), list.getChanges());
  }

  @Override
  public void changesMoved(final Collection<? extends Change> changes, final ChangeList fromList, final ChangeList toList) {
    if (fromList.getName().equals(toList.getName())) {
      return;
    }
    if (LocalChangeList.getDefaultName().equals(toList.getName())) {
      changeListRemoved(toList);
      return;
    }

    final String[] fromLists = LocalChangeList.getDefaultName().equals(fromList.getName()) ? null : new String[] {fromList.getName()};
    addToChangeList(toList.getName(), changes, fromLists);
  }

  @Nullable
  public static String getCurrentMapping(@NotNull SvnVcs vcs, @NotNull File file) {
    try {
      final Status status = vcs.getFactory(file).createStatusClient().doStatus(file, false);
      return status == null ? null : status.getChangeListName();
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
                                            @NotNull ThrowableConsumer<? super ChangeListClient, VcsException> operation) throws VcsException {
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

  private void removeFromChangeList(@NotNull Collection<? extends Change> changes) {
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

  private void addToChangeList(@NotNull String changeList, @NotNull Collection<? extends Change> changes) {
    addToChangeList(changeList, changes, null);
  }

  private void addToChangeList(@NotNull String changeList, @NotNull Collection<? extends Change> changes, String @Nullable [] changeListsToOperate) {
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
