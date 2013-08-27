package org.jetbrains.idea.svn.copy;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface CopyMoveClient extends SvnClient {

  void copy(@NotNull File src, @NotNull File dst, boolean makeParents, boolean isMove) throws VcsException;
}
