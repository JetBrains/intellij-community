package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

/**
 * @author Konstantin Kolosovsky.
 */
public interface HistoryClient extends SvnClient {

  void doLog(@NotNull SvnTarget target,
             @NotNull SVNRevision startRevision,
             @NotNull SVNRevision endRevision,
             boolean stopOnCopy,
             boolean discoverChangedPaths,
             boolean includeMergedRevisions,
             long limit,
             @Nullable String[] revisionProperties,
             @Nullable LogEntryConsumer handler) throws VcsException;
}
