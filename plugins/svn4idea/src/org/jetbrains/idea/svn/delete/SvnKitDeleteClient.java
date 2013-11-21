package org.jetbrains.idea.svn.delete;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitDeleteClient extends BaseSvnClient implements DeleteClient {

  @Override
  public void delete(@NotNull File path, boolean force, boolean dryRun) throws VcsException {
    try {
      myVcs.createWCClient().doDelete(path, force, dryRun);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }
}
