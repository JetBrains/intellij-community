package org.jetbrains.idea.svn.delete;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitDeleteClient extends BaseSvnClient implements DeleteClient {

  @Override
  public void delete(@NotNull File path, boolean force, boolean dryRun, @Nullable ISVNEventHandler handler) throws VcsException {
    SVNWCClient client = myVcs.createWCClient();
    client.setEventHandler(handler);

    try {
      client.doDelete(path, force, dryRun);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }
}
