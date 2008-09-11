package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.*;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

  public void changeListRemoved(final ChangeList list) {
    final File[] files = getPathsFromChanges(list.getChanges());
    if (files.length > 0) {
      try {
        myClient.doRemoveFromChangelist(files, SVNDepth.EMPTY, null);
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

  private File[] getPathsFromChanges(final Collection<Change> changes) {
    final List<File> paths = new ArrayList<File>();
    for (Change change : changes) {
      if ((change.getBeforeRevision() != null) && (isUnderSvn(change.getBeforeRevision().getFile()))) {
        paths.add(change.getBeforeRevision().getFile().getIOFile());
      }
      if ((change.getAfterRevision() != null) && (isUnderSvn(change.getAfterRevision().getFile()))) {
        paths.add(change.getAfterRevision().getFile().getIOFile());
      }
    }
    return paths.toArray(new File[paths.size()]);
  }

  public void changeListChanged(final ChangeList list) {
  }

  public void changeListRenamed(final ChangeList list, final String oldName) {
    if (! ((LocalChangeList) list).isDefault()) {
      final File[] files = getPathsFromChanges(list.getChanges());
      if (files.length > 0) {
        try {
          myClient.doAddToChangelist(files, SVNDepth.EMPTY, list.getName(), null);
        }
        catch (SVNException e) {
          LOG.info(e);
        }
      }
    }
  }

  public void changeListCommentChanged(final ChangeList list, final String oldComment) {
  }

  public void changesMoved(final Collection<Change> changes, final ChangeList fromList, final ChangeList toList) {
    final String[] fromLists = ((LocalChangeList) fromList).isDefault() ? null : new String[] {fromList.getName()};
    final File[] files = getPathsFromChanges(changes);
    if (files.length > 0) {
      try {
        myClient.doAddToChangelist(files, SVNDepth.EMPTY, toList.getName(), fromLists);
      }
      catch (SVNException e) {
        LOG.info(e);
      }
    }
  }

  public void defaultListChanged(final ChangeList oldDefaultList, final ChangeList newDefaultList) {
    // to track list addition
    if (oldDefaultList != null) {
      changeListRenamed(oldDefaultList, null);
    }
    changeListRemoved(newDefaultList);
    ChangeListManager.getInstance(myProject).getDefaultChangeList().setReadOnly(true);
  }

  public void unchangedFileStatusChanged() {
  }

  public void changeListUpdateDone() {
    ChangeListManager.getInstance(myProject).getDefaultChangeList().setReadOnly(true);
  }
}
