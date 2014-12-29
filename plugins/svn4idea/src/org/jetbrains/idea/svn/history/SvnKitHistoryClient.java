package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitHistoryClient extends BaseSvnClient implements HistoryClient {

  @Override
  public void doLog(@NotNull SvnTarget target,
                    @NotNull SVNRevision startRevision,
                    @NotNull SVNRevision endRevision,
                    boolean stopOnCopy,
                    boolean discoverChangedPaths,
                    boolean includeMergedRevisions,
                    long limit,
                    @Nullable String[] revisionProperties,
                    @Nullable LogEntryConsumer handler) throws VcsException {
    try {
      SVNLogClient client = myVcs.getSvnKitManager().createLogClient();

      if (target.isFile()) {
        client.doLog(new File[]{target.getFile()}, startRevision, endRevision, target.getPegRevision(), stopOnCopy, discoverChangedPaths,
                     includeMergedRevisions, limit, revisionProperties, toHandler(handler));
      }
      else {
        client.doLog(target.getURL(), ArrayUtil.EMPTY_STRING_ARRAY, target.getPegRevision(), startRevision, endRevision, stopOnCopy,
                     discoverChangedPaths, includeMergedRevisions, limit, revisionProperties, toHandler(handler));
      }
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @Nullable
  private static ISVNLogEntryHandler toHandler(@Nullable final LogEntryConsumer handler) {
    ISVNLogEntryHandler result = null;

    if (handler != null) {
      result = new ISVNLogEntryHandler() {
        @Override
        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
          handler.consume(LogEntry.create(logEntry));
        }
      };
    }

    return result;
  }
}
