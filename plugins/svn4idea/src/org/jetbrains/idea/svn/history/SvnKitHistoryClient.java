package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitHistoryClient extends BaseSvnClient implements HistoryClient {
  @Override
  public void doLog(@NotNull File path,
                    @NotNull SVNRevision startRevision,
                    @NotNull SVNRevision endRevision,
                    @Nullable SVNRevision pegRevision,
                    boolean stopOnCopy,
                    boolean discoverChangedPaths,
                    boolean includeMergedRevisions,
                    long limit,
                    @Nullable String[] revisionProperties,
                    @Nullable ISVNLogEntryHandler handler) throws VcsException {
    try {
      // TODO: a bug noticed when testing: we should pass "limit + 1" to get "limit" rows
      SVNLogClient client = myVcs.createLogClient();

      client.doLog(new File[]{path}, startRevision, endRevision, pegRevision, stopOnCopy, discoverChangedPaths, includeMergedRevisions,
                   limit, revisionProperties, handler);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }
}
