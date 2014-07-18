package org.jetbrains.idea.svn.revert;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitRevertClient extends BaseSvnClient implements RevertClient {

  @Override
  public void revert(@NotNull File[] paths, @Nullable Depth depth, @Nullable ProgressTracker handler) throws VcsException {
    SVNWCClient client = myVcs.getSvnKitManager().createWCClient();

    client.setEventHandler(toEventHandler(handler));
    try {
      client.doRevert(paths, toDepth(depth), null);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }
}
