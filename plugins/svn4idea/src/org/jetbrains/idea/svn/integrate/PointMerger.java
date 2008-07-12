package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.SvnRepositoryContentRevision;
import org.jetbrains.idea.svn.update.UpdateEventHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.*;

public class PointMerger extends Merger {
  private final List<Change> mySelectedChanges;
  private SVNWCClient myWcClient;
  private SVNCopyClient myCopyClient;
  private SvnVcs myVcs;
  private final UpdateEventHandler myHandler;

  public PointMerger(final SvnVcs vcs, CommittedChangeList selectedChangeList, final File target, final UpdateEventHandler handler, final SVNURL currentBranchUrl,
                     final List<Change> selectedChanges) {
    super(vcs, new ArrayList<CommittedChangeList>(Arrays.<CommittedChangeList>asList(selectedChangeList)),
          target, handler, currentBranchUrl);
    myHandler = handler;
    myVcs = vcs;
    mySelectedChanges = selectedChanges;
    Collections.sort(mySelectedChanges, ChangesComparator.getInstance());
    myLatestProcessed = selectedChangeList;
  }

  public boolean hasNext() {
    return myCount == 0;
  }

  protected void doMerge() throws SVNException {
    for (Change change : mySelectedChanges) {
      if (change.getBeforeRevision() == null) {
        add(change);
      } else if (change.getAfterRevision() == null) {
        delete(change);
      } else {
        merge(change);
      }
    }
  }

  private void merge(final Change change) throws SVNException {
    final SvnRepositoryContentRevision before = (SvnRepositoryContentRevision) change.getBeforeRevision();
    final SvnRepositoryContentRevision after = (SvnRepositoryContentRevision) change.getAfterRevision();

    final String path = myCurrentBranchUrl.toString();
    final String beforeUrl = before.getFullPath();
    final String afterUrl = after.getFullPath();

    final File afterPath = SvnUtil.fileFromUrl(myTarget, path, afterUrl);

    myDiffClient.doMerge(SVNURL.parseURIEncoded(beforeUrl), ((SvnRevisionNumber) before.getRevisionNumber()).getRevision(),
                         SVNURL.parseURIEncoded(afterUrl), ((SvnRevisionNumber) after.getRevisionNumber()).getRevision(),
                         afterPath, false, true, false, mySvnConfig.MERGE_DRY_RUN);
  }

  private void delete(final Change change) throws SVNException {
    if (myWcClient == null) {
      myWcClient = myVcs.createWCClient();
      myWcClient.setEventHandler(myHandler);
    }
    final SvnRepositoryContentRevision before = (SvnRepositoryContentRevision) change.getBeforeRevision();
    final String path = myCurrentBranchUrl.toString();
    final String beforeUrl = before.getFullPath();
    final File beforePath = SvnUtil.fileFromUrl(myTarget, path, beforeUrl);

    myWcClient.doDelete(beforePath, false, mySvnConfig.MERGE_DRY_RUN);
  }

  private void add(final Change change) throws SVNException {
    if (myCopyClient == null) {
      myCopyClient = myVcs.createCopyClient();
      myCopyClient.setEventHandler(myHandler);
    }
    final SvnRepositoryContentRevision after = (SvnRepositoryContentRevision) change.getAfterRevision();
    final String path = myCurrentBranchUrl.toString();
    final String afterUrl = after.getFullPath();
    final File afterPath = SvnUtil.fileFromUrl(myTarget, path, afterUrl);

    final SVNRevision revision = ((SvnRevisionNumber)after.getRevisionNumber()).getRevision();
    final SVNCopySource[] copySource = new SVNCopySource[]{new SVNCopySource(revision, revision, SVNURL.parseURIEncoded(afterUrl))};
    // todo dry run
    myCopyClient.doCopy(copySource, afterPath, false, true, true);
  }

  private static class ChangesComparator implements Comparator<Change> {
    private final static ChangesComparator ourInstance = new ChangesComparator();

    public static ChangesComparator getInstance() {
      return ourInstance;
    }

    public int compare(final Change o1, final Change o2) {
      final SvnRepositoryContentRevision after1 = (SvnRepositoryContentRevision) o1.getAfterRevision();
      final SvnRepositoryContentRevision after2 = (SvnRepositoryContentRevision) o2.getAfterRevision();

      if (after1 == null) {
        return 1;
      }
      if (after2 == null) {
        return -1;
      }

      final String path1 = after1.getFullPath();
      final String path2 = after2.getFullPath();
      if (path1 == null) {
        return 1;
      }
      if (path2 == null) {
        return -1;
      }

      final String ancestor = SVNPathUtil.getCommonPathAncestor(path1, path2);
      return (path1.equals(ancestor)) ? -1 : 1;
    }
  }

  @Nullable
  @Override
  public File getMergeInfoHolder() {
    return null;
  }
}
