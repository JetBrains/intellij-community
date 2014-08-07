package org.jetbrains.idea.svn.copy;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.checkin.CommitEventHandler;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitCopyMoveClient extends BaseSvnClient implements CopyMoveClient {

  private static final int INVALID_REVISION = -1;

  @Override
  public void copy(@NotNull File src, @NotNull File dst, boolean makeParents, boolean isMove) throws VcsException {
    final SVNCopySource copySource = new SVNCopySource(isMove ? SVNRevision.UNDEFINED : SVNRevision.WORKING, SVNRevision.WORKING, src);

    try {
      myVcs.getSvnKitManager().createCopyClient().doCopy(new SVNCopySource[]{copySource}, dst, isMove, makeParents, true);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  @Override
  public long copy(@NotNull SvnTarget source,
                   @NotNull SvnTarget destination,
                   @Nullable SVNRevision revision,
                   boolean makeParents,
                   boolean isMove,
                   @NotNull String message,
                   @Nullable CommitEventHandler handler) throws VcsException {

    if (!destination.isURL()) {
      throw new IllegalArgumentException("Only urls are supported as destination " + destination);
    }

    final SVNCopySource copySource = createCopySource(source, revision);
    SVNCopyClient client = myVcs.getSvnKitManager().createCopyClient();
    client.setEventHandler(toEventHandler(handler));

    SVNCommitInfo info;
    try {
      info = client
        .doCopy(new SVNCopySource[]{copySource}, destination.getURL(), isMove, makeParents, true, message, null);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }

    return info != null ? info.getNewRevision() : INVALID_REVISION;
  }

  @Override
  public void copy(@NotNull SvnTarget source,
                   @NotNull File destination,
                   @Nullable SVNRevision revision,
                   boolean makeParents,
                   @Nullable ProgressTracker handler) throws VcsException {
    SVNCopyClient client = myVcs.getSvnKitManager().createCopyClient();
    client.setEventHandler(toEventHandler(handler));

    try {
      client.doCopy(new SVNCopySource[]{createCopySource(source, revision)}, destination, false, makeParents, true);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @NotNull
  private static SVNCopySource createCopySource(@NotNull SvnTarget source, @Nullable SVNRevision revision) {
    return source.isFile()
           ? new SVNCopySource(source.getPegRevision(), revision, source.getFile())
           : new SVNCopySource(source.getPegRevision(), revision, source.getURL());
  }
}
