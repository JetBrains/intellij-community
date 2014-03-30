package org.jetbrains.idea.svn.revert;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitRevertClient extends BaseSvnClient implements RevertClient {

  @Override
  public void revert(@NotNull File[] paths, @Nullable SVNDepth depth, @Nullable ISVNEventHandler handler) throws VcsException {
    SVNWCClient client = myVcs.createWCClient();

    client.setEventHandler(handler);
    try {
      client.doRevert(paths, depth, null);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }
}
