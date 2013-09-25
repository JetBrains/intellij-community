package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.commandLine.CommitEventHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNCommitHandler;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface ImportClient extends SvnClient {

  long doImport(@NotNull File path,
                @NotNull SVNURL url,
                @Nullable SVNDepth depth,
                @NotNull String message,
                boolean noIgnore,
                @Nullable CommitEventHandler handler,
                @Nullable ISVNCommitHandler commitHandler) throws VcsException;
}
