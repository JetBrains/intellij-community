package org.jetbrains.idea.svn.conflict;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitConflictClient extends BaseSvnClient implements ConflictClient {
  @Override
  public void resolve(@NotNull File path,
                      @Nullable SVNDepth depth,
                      boolean resolveProperty,
                      boolean resolveContent,
                      boolean resolveTree) throws VcsException {
    try {
      myVcs.createWCClient().doResolve(path, depth, resolveContent, resolveProperty, resolveTree, SVNConflictChoice.MERGED);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }
}
