package org.jetbrains.idea.svn.add;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitAddClient extends BaseSvnClient implements AddClient {
  @Override
  public void add(@NotNull File file, SVNDepth depth, boolean makeParents, boolean includeIgnored, boolean force) throws VcsException {
    try {
      myVcs.createWCClient().doAdd(file, force,
                                   false, // directory should already be created
                                   makeParents, // not used but will be passed as makeParents value
                                   SVNDepth.recurseFromDepth(depth));
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }
}
