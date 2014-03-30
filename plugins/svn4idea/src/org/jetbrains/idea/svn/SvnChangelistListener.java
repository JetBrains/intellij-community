/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNStatus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SvnChangelistListener implements ChangeListListener {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnChangelistListener");

  private final Project myProject;
  private final SvnVcs myVcs;

  public SvnChangelistListener(@NotNull final Project project, @NotNull final SvnVcs vcs) {
    myProject = project;
    myVcs = vcs;
  }

  public void changeListAdded(final ChangeList list) {
    // SVN change list exists only when there are any files in it
  }

  public void changesRemoved(final Collection<Change> changes, final ChangeList fromList) {
    if (SvnChangeProvider.ourDefaultListName.equals(fromList.getName())) {
      return;
    }
    removeFromChangeList(changes);
  }

  public void changesAdded(Collection<Change> changes, ChangeList toList) {
    if (toList == null || SvnChangeProvider.ourDefaultListName.equals(toList.getName())) {
      return;
    }
    addToChangeList(toList.getName(), changes);
  }

  public void changeListRemoved(final ChangeList list) {
    removeFromChangeList(list.getChanges());
  }

  private boolean isUnderSvn(final FilePath path) {
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(path);
    return ((vcs != null) && (SvnVcs.VCS_NAME.equals(vcs.getName())));
  }

  private List<String> getPathsFromChanges(final Collection<Change> changes) {
    final List<String> paths = new ArrayList<String>();
    for (Change change : changes) {
      if ((change.getBeforeRevision() != null) && (isUnderSvn(change.getBeforeRevision().getFile()))) {
        final String path = change.getBeforeRevision().getFile().getIOFile().getAbsolutePath();
        if (! paths.contains(path)) {
          paths.add(path);
        }
      }
      if ((change.getAfterRevision() != null) && (isUnderSvn(change.getAfterRevision().getFile()))) {
        final String path = change.getAfterRevision().getFile().getIOFile().getAbsolutePath();
        if (! paths.contains(path)) {
          paths.add(path);
        }
      }
    }
    return paths;
  }

  public void changeListChanged(final ChangeList list) {
  }

  public void changeListRenamed(final ChangeList list, final String oldName) {
    if (Comparing.equal(list.getName(), oldName)) {
      return;
    }
    if (SvnChangeProvider.ourDefaultListName.equals(list.getName())) {
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
    if (SvnChangeProvider.ourDefaultListName.equals(toList.getName())) {
      changeListRemoved(toList);
      return;
    }

    final String[] fromLists = SvnChangeProvider.ourDefaultListName.equals(fromList.getName()) ? null : new String[] {fromList.getName()};
    addToChangeList(toList.getName(), changes, fromLists);
  }

  public void defaultListChanged(final ChangeList oldDefaultList, final ChangeList newDefaultList) {
  }

  public void unchangedFileStatusChanged() {
  }

  public void changeListUpdateDone() {
  }

  @Nullable
  public static String getCurrentMapping(final SvnVcs vcs, final File file) {
    try {
      final SVNStatus status = vcs.getFactory(file).createStatusClient().doStatus(file, false);
      return status == null ? null : status.getChangelistName();
    }
    catch (SVNException e) {
      final SVNErrorCode errorCode = e.getErrorMessage().getErrorCode();
      if (SVNErrorCode.WC_NOT_DIRECTORY.equals(errorCode) || SVNErrorCode.WC_NOT_FILE.equals(errorCode)) {
        LOG.debug("Logging only, exception is valid (caught) here", e);
      } else {
        LOG.info("Logging only, exception is valid (caught) here", e);
      }
    }
    return null;
  }

  public static void putUnderList(@NotNull final Project project, @NotNull final String list, @NotNull final File after)
    throws VcsException {
    final SvnVcs vcs = SvnVcs.getInstance(project);

    try {
      vcs.getFactory(after).createChangeListClient().add(list, after, null);
    }
    catch(SvnBindException e) {
      LOG.info(e);
      if (!e.contains(SVNErrorCode.WC_NOT_DIRECTORY) && !e.contains(SVNErrorCode.WC_NOT_FILE)) {
        throw e;
      }
    }
    catch (VcsException e) {
      LOG.info(e);
      throw e;
    }
  }

  public static void removeFromList(@NotNull final Project project, @NotNull final File after) throws VcsException {
    final SvnVcs vcs = SvnVcs.getInstance(project);
    try {
      vcs.getFactory(after).createChangeListClient().remove(after);
    }
    catch(SvnBindException e) {
      LOG.info(e);
      if (!e.contains(SVNErrorCode.WC_NOT_DIRECTORY) && !e.contains(SVNErrorCode.WC_NOT_FILE)) {
        throw e;
      }
    }
    catch (VcsException e) {
      LOG.info(e);
      throw e;
    }
  }

  private void removeFromChangeList(@NotNull Collection<Change> changes) {
    final List<String> paths = getPathsFromChanges(changes);

    for (String path : paths) {
      try {
        File file = new File(path);

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
    final List<String> paths = getPathsFromChanges(changes);

    for (String path : paths) {
      try {
        File file = new File(path);
        myVcs.getFactory(file).createChangeListClient().add(changeList, file, changeListsToOperate);
      }
      catch (VcsException e) {
        LOG.info(e);
      }
    }
  }
}
