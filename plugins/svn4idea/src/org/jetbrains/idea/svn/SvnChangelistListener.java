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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNChangelistHandler;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;

import java.io.File;
import java.util.*;

public class SvnChangelistListener implements ChangeListListener {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnChangelistListener");

  private final Project myProject;
  private final SVNChangelistClient myClient;

  public SvnChangelistListener(final Project project, final SVNChangelistClient client) {
    myProject = project;
    myClient = client;
  }

  public void changeListAdded(final ChangeList list) {
    // SVN change list exists only when there are any files in it
  }

  public void changesRemoved(final Collection<Change> changes, final ChangeList fromList) {
    if (SvnChangeProvider.ourDefaultListName.equals(fromList.getName())) {
      return;
    }
    final List<String> paths = getPathsFromChanges(changes);
    for (String path : paths) {
      try {
        myClient.doRemoveFromChangelist(new File[]{new File(path)}, SVNDepth.EMPTY, null);
      }
      catch (SVNException e) {
        LOG.info(e);
      }
    }
  }

  public void changesAdded(Collection<Change> changes, ChangeList toList) {
    if (SvnChangeProvider.ourDefaultListName.equals(toList.getName())) {
      return;
    }
    final List<String> paths = getPathsFromChanges(changes);
    for (String path : paths) {
      try {
        myClient.doAddToChangelist(new File[]{new File(path)}, SVNDepth.EMPTY, toList.getName(), null);
      }
      catch (SVNException e) {
        LOG.info(e);
      }
    }
  }

  public void changeListRemoved(final ChangeList list) {
    final List<String> paths = getPathsFromChanges(list.getChanges());
    for (String path : paths) {
      try {
        myClient.doRemoveFromChangelist(new File[]{new File(path)}, SVNDepth.EMPTY, null);
      }
      catch (SVNException e) {
        LOG.info(e);
      }
    }
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
    final List<String> paths = getPathsFromChanges(list.getChanges());
    for (String path : paths) {
      try {
        myClient.doAddToChangelist(new File[]{new File(path)}, SVNDepth.EMPTY, list.getName(), null);
      }
      catch (SVNException e) {
        LOG.info(e);
      }
    }
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
    final List<String> paths = getPathsFromChanges(changes);
    for (final String path : paths) {
      try {
        myClient.doAddToChangelist(new File[]{new File(path)}, SVNDepth.EMPTY, toList.getName(), fromLists);
      }
      catch (SVNException e) {
        LOG.info(e);
      }
    }
  }

  public void defaultListChanged(final ChangeList oldDefaultList, final ChangeList newDefaultList) {
  }

  public void unchangedFileStatusChanged() {
  }

  public void changeListUpdateDone() {
  }

  @Nullable
  public static String getCurrentMapping(final Project project, final File file) {
    final SvnVcs vcs = SvnVcs.getInstance(project);
    final SVNChangelistClient client = vcs.createChangelistClient();
    try {
      final Ref<String> refResult = new Ref<String>();
      final ISVNChangelistHandler handler = new ISVNChangelistHandler() {
        public void handle(final File path, final String changelistName) {
          if (refResult.isNull() && Comparing.equal(path, file)) {
            refResult.set(changelistName);
          }
        }
      };
      if (file.exists()) {
        client.doGetChangeLists(file, null, SVNDepth.EMPTY, handler);
      } else if (file.getParentFile() != null) {
        client.doGetChangeLists(file.getParentFile(), null, SVNDepth.IMMEDIATES, handler);
      }
      return refResult.get();
    }
    catch (SVNException e) {
      LOG.info(e);
    }
    return null;
  }

  public static void putUnderList(final Project project, final String list, final File after) throws SVNException {
    final SvnVcs vcs = SvnVcs.getInstance(project);
    final SVNChangelistClient client = vcs.createChangelistClient();
    try {
      client.doAddToChangelist(new File[]{after}, SVNDepth.EMPTY, list, null);
    }
    catch (SVNException e) {
      LOG.info(e);
      if ((! SVNErrorCode.WC_NOT_DIRECTORY.equals(e.getErrorMessage().getErrorCode()) && (! SVNErrorCode.WC_NOT_FILE.equals(e.getErrorMessage().getErrorCode())))) {
        throw e;
      }
    }
  }

  public static void removeFromList(final Project project, final File after) throws SVNException {
    final SvnVcs vcs = SvnVcs.getInstance(project);
    final SVNChangelistClient client = vcs.createChangelistClient();
    try {
      client.doRemoveFromChangelist(new File[]{after}, SVNDepth.EMPTY, null);
    }
    catch (SVNException e) {
      LOG.info(e);
      if ((! SVNErrorCode.WC_NOT_DIRECTORY.equals(e.getErrorMessage().getErrorCode()) && (! SVNErrorCode.WC_NOT_FILE.equals(e.getErrorMessage().getErrorCode())))) {
        throw e;
      }
    }
  }
}
