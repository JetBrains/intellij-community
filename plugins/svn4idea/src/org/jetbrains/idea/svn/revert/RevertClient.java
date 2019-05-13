package org.jetbrains.idea.svn.revert;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;
import java.util.Collection;

/**
 * @author Konstantin Kolosovsky.
 */
public interface RevertClient extends SvnClient {

  void revert(@NotNull Collection<File> paths, @Nullable Depth depth, @Nullable ProgressTracker handler) throws VcsException;
}
