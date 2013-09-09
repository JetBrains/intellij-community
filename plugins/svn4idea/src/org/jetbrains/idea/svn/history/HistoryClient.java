package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface HistoryClient extends SvnClient {

  // TODO: Url is also supported as parameter in cmd - use Target class
  void doLog(@NotNull File path,
             @NotNull SVNRevision startRevision,
             @NotNull SVNRevision endRevision,
             @Nullable SVNRevision pegRevision,
             boolean stopOnCopy,
             boolean discoverChangedPaths,
             boolean includeMergedRevisions,
             long limit,
             @Nullable String[] revisionProperties,
             @Nullable ISVNLogEntryHandler handler) throws VcsException;
}
