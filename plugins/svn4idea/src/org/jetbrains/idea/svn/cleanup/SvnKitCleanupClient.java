package org.jetbrains.idea.svn.cleanup;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitCleanupClient extends BaseSvnClient implements CleanupClient {

  @Override
  public void cleanup(@NotNull File path, @Nullable ISVNEventHandler handler) throws VcsException {
    SVNWCClient client = myVcs.createWCClient();

    client.setEventHandler(handler);
    try {
      client.doCleanup(path);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }
}
