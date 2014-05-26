package org.jetbrains.idea.svn.change;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitChangeListClient extends BaseSvnClient implements ChangeListClient {

  @Override
  public void add(@NotNull String changeList, @NotNull File path, @Nullable String[] changeListsToOperate) throws VcsException {
    try {
      myVcs.getSvnKitManager().createChangelistClient()
        .doAddToChangelist(new File[]{path}, SVNDepth.EMPTY, changeList, changeListsToOperate);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @Override
  public void remove(@NotNull File path) throws VcsException {
    try {
      myVcs.getSvnKitManager().createChangelistClient().doRemoveFromChangelist(new File[]{path}, SVNDepth.EMPTY, null);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }
}
