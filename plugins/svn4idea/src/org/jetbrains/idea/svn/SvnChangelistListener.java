package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import org.tmatesoft.svn.core.SVNDepth;
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

  // just to have all work with CLs in one class
  public void pathChanged(final File from, final File to) throws SVNException {
    // everything under from must became under to
    final Map<File, String> oldMappings = new HashMap<File, String>();
    try {
      myClient.doGetChangeLists(from, null, SVNDepth.INFINITY, new ISVNChangelistHandler() {
        public void handle(final File path, final String changelistName) {
          oldMappings.put(path, changelistName);
        }
      });
    }
    catch (SVNException e) {
      LOG.info(e);
    }
    for (Map.Entry<File, String> entry : oldMappings.entrySet()) {
      final File file = entry.getKey();
      try {
        myClient.doRemoveFromChangelist(new File[]{file}, SVNDepth.EMPTY, null);
      }
      catch (SVNException e) {
        LOG.info(e);
        throw e;
      }

      final String relativePath = file.getAbsolutePath().substring(from.getAbsolutePath().length());
      final File newPath = new File(to, relativePath);

      try {
        myClient.doAddToChangelist(new File[]{newPath}, SVNDepth.EMPTY, entry.getValue(), null);
      }
      catch (SVNException e) {
        LOG.info(e);
        throw e;
      }
    }
  }
}
