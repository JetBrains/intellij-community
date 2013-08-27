package org.jetbrains.idea.svn.revert;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface RevertClient extends SvnClient {

  void revert(@NotNull File[] paths, @Nullable SVNDepth depth, @Nullable ISVNEventHandler handler) throws VcsException;
}
