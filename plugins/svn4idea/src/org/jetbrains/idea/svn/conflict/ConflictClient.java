package org.jetbrains.idea.svn.conflict;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface ConflictClient extends SvnClient {

  void resolve(@NotNull File path, @Nullable Depth depth, boolean resolveProperty, boolean resolveContent, boolean resolveTree)
    throws VcsException;
}
