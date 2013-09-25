package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface ExportClient extends SvnClient {

  void export(@NotNull SvnTarget from,
              @NotNull File to,
              @Nullable SVNRevision revision,
              @Nullable SVNDepth depth,
              @Nullable String nativeLineEnd,
              boolean force,
              boolean ignoreExternals,
              @Nullable ISVNEventHandler handler) throws VcsException;
}
