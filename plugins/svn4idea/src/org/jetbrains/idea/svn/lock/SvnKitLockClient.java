package org.jetbrains.idea.svn.lock;

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
public class SvnKitLockClient extends BaseSvnClient implements LockClient {

  @Override
  public void lock(@NotNull File file, boolean force, @NotNull String message, @Nullable ISVNEventHandler handler) throws VcsException {
    try {
      getClient(handler).doLock(new File[]{file}, force, message);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @Override
  public void unlock(@NotNull File file, boolean force, @Nullable ISVNEventHandler handler) throws VcsException {
    try {
      getClient(handler).doUnlock(new File[]{file}, force);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @NotNull
  private SVNWCClient getClient(@Nullable ISVNEventHandler handler) {
    SVNWCClient client = myVcs.createWCClient();

    client.setEventHandler(handler);

    return client;
  }
}
