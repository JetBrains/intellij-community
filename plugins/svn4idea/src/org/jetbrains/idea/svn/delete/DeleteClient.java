package org.jetbrains.idea.svn.delete;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface DeleteClient extends SvnClient {

  void delete(@NotNull File path, boolean force, boolean dryRun, @Nullable ProgressTracker handler) throws VcsException;

  long delete(@NotNull SVNURL url, @NotNull String message) throws VcsException;
}
