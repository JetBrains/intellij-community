package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.List;

public class LiveProvider implements BunchProvider {
  private final SvnLogLoader myLoader;
  private final SvnRepositoryLocation myLocation;
  private boolean myEarliestRevisionWasAccessed;
  private final long myYoungestRevision;
  private final SvnVcs myVcs;

  public LiveProvider(final SvnVcs vcs, final SvnRepositoryLocation location, final long latestRevision, final SvnLogLoader loader) {
    myVcs = vcs;
    myLoader = loader;
    myLocation = location;
    myYoungestRevision = latestRevision;
  }

  public long getEarliestRevision() {
    return -1;
  }

  public boolean isEmpty() {
    return false;
  }

  public Fragment getEarliestBunchInInterval(final long earliestRevision, final long oldestRevision, final int desirableSize, final boolean includeYoungest,
                                             final boolean includeOldest) throws SVNException {
    final SVNRevision youngRevision = (earliestRevision == -1) ? SVNRevision.HEAD : SVNRevision.create(earliestRevision);
    try {
      final List<CommittedChangeList> list = myLoader.loadInterval(youngRevision, SVNRevision.create(oldestRevision), desirableSize, includeYoungest, includeOldest);
      if (list.isEmpty()) {
        myEarliestRevisionWasAccessed = (oldestRevision == 0);
        return null;
      }
      myEarliestRevisionWasAccessed = (oldestRevision == 0) && ((list.size() + ((! includeOldest) ? 1 : 0) + ((! includeYoungest) ? 1 : 0)) < desirableSize);
      return new Fragment(Origin.LIVE, list, true, true, null);
    } catch (SVNException e) {
      if (SVNErrorCode.FS_NOT_FOUND.equals(e.getErrorMessage().getErrorCode())) {
        // occurs when target URL is deleted in repository
        // try to find latest existent revision. expensive ...
        final LatestExistentSearcher searcher = new LatestExistentSearcher(oldestRevision, myYoungestRevision, (oldestRevision != 0),
            myVcs, SVNURL.parseURIEncoded(myLocation.getURL()));
        final long existent = searcher.execute();
        if ((existent == -1) || (existent == earliestRevision)) {
          myEarliestRevisionWasAccessed = true;
          return null;
        }
        return getEarliestBunchInInterval(existent, oldestRevision, includeYoungest ? desirableSize : (desirableSize + 1), true, includeOldest);
      }
      throw e;
    }
  }

  public boolean isEarliestRevisionWasAccessed() {
    return myEarliestRevisionWasAccessed;
  }
}
