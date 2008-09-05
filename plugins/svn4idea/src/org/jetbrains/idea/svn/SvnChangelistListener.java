package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SvnChangelistListener implements ChangeListListener {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnChangelistListener");

  private final SVNChangelistClient myClient;

  public SvnChangelistListener(final SVNChangelistClient client) {
    myClient = client;
  }

  public void changeListAdded(final ChangeList list) {
    // SVN change list exists only when there are any files in it  
  }

  public void changeListRemoved(final ChangeList list) {
    try {
      myClient.doRemoveFromChangelist(getPathsFromChanges(list.getChanges()), SVNDepth.EMPTY, null);
    }
    catch (SVNException e) {
      LOG.info(e);
    }
  }

  private static File[] getPathsFromChanges(final Collection<Change> changes) {
    final List<File> paths = new ArrayList<File>();
    for (Change change : changes) {
      if (change.getBeforeRevision() != null) {
        paths.add(change.getBeforeRevision().getFile().getIOFile());
      }
      if (change.getAfterRevision() != null) {
        paths.add(change.getAfterRevision().getFile().getIOFile());
      }
    }
    return paths.toArray(new File[paths.size()]);
  }

  public void changeListChanged(final ChangeList list) {
  }

  public void changeListRenamed(final ChangeList list, final String oldName) {
    if (! ((LocalChangeList) list).isDefault()) {
      try {
        myClient.doAddToChangelist(getPathsFromChanges(list.getChanges()), SVNDepth.EMPTY, list.getName(), null);
      }
      catch (SVNException e) {
        LOG.info(e);
      }
    }
  }

  public void changeListCommentChanged(final ChangeList list, final String oldComment) {
  }

  public void changesMoved(final Collection<Change> changes, final ChangeList fromList, final ChangeList toList) {
    final String[] fromLists = ((LocalChangeList) fromList).isDefault() ? null : new String[] {fromList.getName()};
    try {
      myClient.doAddToChangelist(getPathsFromChanges(changes), SVNDepth.EMPTY, toList.getName(), fromLists);
    }
    catch (SVNException e) {
      LOG.info(e);
    }
  }

  public void defaultListChanged(final ChangeList oldDefaultList, final ChangeList newDefaultList) {
    // to track list addition
    changeListRenamed(oldDefaultList, null);
    changeListRemoved(newDefaultList);
  }

  public void unchangedFileStatusChanged() {
  }

  public void changeListUpdateDone() {
  }
}
