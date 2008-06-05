package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnFileUrlMapping;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

public class LatestExistentSearcher {
  private long myStartNumber;
  private final boolean myStartExistsKnown;
  private final SVNURL myUrl;
  private final SvnVcs myVcs;
  private final long myEndNumber;

  public LatestExistentSearcher(final long startNumber, final long endNumber, final boolean startExistsKnown, final SvnVcs vcs, final SVNURL url) {
    myStartNumber = startNumber;
    myEndNumber = endNumber;
    myStartExistsKnown = startExistsKnown;
    myVcs = vcs;
    myUrl = url;
  }

  public long execute() {
    if (! myStartExistsKnown) {
      final SvnFileUrlMapping mapping = myVcs.getSvnFileUrlMapping();
      final VirtualFile vf = mapping.getVcRootByUrl(myUrl.toString());
      if (vf == null) {
        return -1;
      }
      final SVNWCClient client = myVcs.createWCClient();
      try {
        final SVNInfo info = client.doInfo(new File(vf.getPath()), SVNRevision.WORKING);
        if ((info == null) || (info.getRevision() == null)) {
          return -1;
        }
        myStartNumber = info.getRevision().getNumber();
      }
      catch (SVNException e) {
        return -1;
      }
    }

    // not frequent case, so there is no need to make it very well optimized.. maybe further should be rewritten
    long latestOk = myStartNumber;

    SVNRepository repository = null;
    try {
      repository = myVcs.createRepository(myUrl.toString());
      final SVNURL repRoot = repository.getRepositoryRoot(true);
      if (repRoot != null) {
        final String urlString = myUrl.toString().substring(repRoot.toString().length());
        for (long i = myStartNumber + 1; i < myEndNumber; i++) {
          final SVNNodeKind kind = repository.checkPath(urlString, i);
          if (SVNNodeKind.DIR.equals(kind) || SVNNodeKind.FILE.equals(kind)) {
            latestOk = i;
          }
        }
      }

      // log does NOT allow to use HEAD if head does not contain url, and we know for sure that it does not contain

      /*myVcs.createLogClient().doLog(myUrl, new String[]{""}, startRevision, startRevision, SVNRevision.HEAD, false, false, 0,
                     new ISVNLogEntryHandler() {
                       public void handleLogEntry(final SVNLogEntry logEntry) throws SVNException {
                         latestOk.set(logEntry.getRevision());
                       }
                     });*/
    }
    catch (SVNException e) {
      return -1;
    } finally {
      if (repository != null) {
        repository.closeSession();
      }
    }

    return latestOk;
  }
}
