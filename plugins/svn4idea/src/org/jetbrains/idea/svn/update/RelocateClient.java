package org.jetbrains.idea.svn.update;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface RelocateClient extends SvnClient {

  void relocate(@NotNull File copyRoot, @NotNull String fromPrefix, @NotNull String toPrefix) throws VcsException;
}
