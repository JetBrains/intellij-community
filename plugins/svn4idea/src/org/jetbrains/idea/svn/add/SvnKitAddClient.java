package org.jetbrains.idea.svn.add;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitAddClient extends BaseSvnClient implements AddClient {

  /**
   * TODO: Implement correct support for includeIgnored parameter. Also check that correct depth will be used for all cases (when another
   * TODO: overload of doAdd() is used) as, for instance, SVNDepth.recurseFromDepth(EMPTY) = false, SVNDepth.fromRecursive(false) = FILES.
   */
  @Override
  public void add(@NotNull File file,
                  @Nullable Depth depth,
                  boolean makeParents,
                  boolean includeIgnored,
                  boolean force,
                  @Nullable ProgressTracker handler) throws VcsException {
    try {
      SVNWCClient client = myVcs.getSvnKitManager().createWCClient();

      client.setEventHandler(toEventHandler(handler));
      client.doAdd(file, force,
                   false, // directory should already be created
                   makeParents, // not used but will be passed as makeParents value
                   Depth.isRecursive(depth));
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }
}
