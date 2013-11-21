package org.jetbrains.idea.svn.delete;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface DeleteClient extends SvnClient {

  void delete(@NotNull File path, boolean force, boolean dryRun, @Nullable ISVNEventHandler handler) throws VcsException;
}
