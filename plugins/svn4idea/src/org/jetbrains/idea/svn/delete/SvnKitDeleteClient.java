package org.jetbrains.idea.svn.delete;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitDeleteClient extends BaseSvnClient implements DeleteClient {

  @Override
  public void delete(@NotNull File path, boolean force, boolean dryRun, @Nullable ProgressTracker handler) throws VcsException {
    SVNWCClient client = myVcs.getSvnKitManager().createWCClient();
    client.setEventHandler(toEventHandler(handler));

    try {
      client.doDelete(path, force, dryRun);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  @Override
  public long delete(@NotNull SVNURL url, @NotNull String message) throws VcsException {
    try {
      SVNCommitInfo commitInfo = myVcs.getSvnKitManager().createCommitClient().doDelete(new SVNURL[]{url}, message);

      return commitInfo.getNewRevision();
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }
}
