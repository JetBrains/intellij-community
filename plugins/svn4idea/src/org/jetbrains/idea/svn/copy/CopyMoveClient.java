package org.jetbrains.idea.svn.copy;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.checkin.CommitEventHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface CopyMoveClient extends SvnClient {

  void copy(@NotNull File src, @NotNull File dst, boolean makeParents, boolean isMove) throws VcsException;

  /**
   * @param source
   * @param destination
   * @param revision
   * @param makeParents
   * @param isMove
   * @param message
   * @param handler
   * @return new revision number
   * @throws VcsException
   */
  long copy(@NotNull SvnTarget source,
            @NotNull SvnTarget destination,
            @Nullable SVNRevision revision,
            boolean makeParents,
            boolean isMove,
            @NotNull String message,
            @Nullable CommitEventHandler handler) throws VcsException;

  void copy(@NotNull SvnTarget source,
            @NotNull File destination,
            @Nullable SVNRevision revision,
            boolean makeParents,
            @Nullable ProgressTracker handler) throws VcsException;
}
