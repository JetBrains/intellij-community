package org.jetbrains.idea.svn.lock;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface LockClient extends SvnClient {

  void lock(@NotNull File file,
            boolean force,
            @NotNull String message,
            @Nullable ProgressTracker handler) throws VcsException;

  void unlock(@NotNull File file,
              boolean force,
              @Nullable ProgressTracker handler) throws VcsException;
}
