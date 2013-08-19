package org.jetbrains.idea.svn.copy;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitCopyMoveClient extends BaseSvnClient implements CopyMoveClient {

  @Override
  public void copy(@NotNull File src, @NotNull File dst, boolean makeParents, boolean isMove) throws VcsException {
    final SVNCopySource copySource = new SVNCopySource(isMove ? SVNRevision.UNDEFINED : SVNRevision.WORKING, SVNRevision.WORKING, src);

    try {
      myVcs.createCopyClient().doCopy(new SVNCopySource[]{copySource}, dst, isMove, makeParents, true);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }
}
