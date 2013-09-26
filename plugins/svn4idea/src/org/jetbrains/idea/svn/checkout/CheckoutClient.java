package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public interface CheckoutClient extends SvnClient {

  void checkout(@NotNull SvnTarget source,
                @NotNull File destination,
                @Nullable SVNRevision revision,
                @Nullable SVNDepth depth,
                boolean ignoreExternals,
                boolean force,
                @NotNull WorkingCopyFormat format,
                @Nullable ISVNEventHandler handler) throws VcsException;

  List<WorkingCopyFormat> getSupportedFormats() throws VcsException;
}
