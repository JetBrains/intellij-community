package org.jetbrains.idea.svn.cleanup;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface CleanupClient extends SvnClient {

  void cleanup(@NotNull File path, @Nullable ISVNEventHandler handler) throws VcsException;
}
