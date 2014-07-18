package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;
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
                @Nullable Depth depth,
                boolean ignoreExternals,
                boolean force,
                @NotNull WorkingCopyFormat format,
                @Nullable ProgressTracker handler) throws VcsException;

  List<WorkingCopyFormat> getSupportedFormats() throws VcsException;
}
