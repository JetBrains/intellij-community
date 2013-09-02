package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitMergeClient extends BaseSvnClient implements MergeClient {

  public void merge(@NotNull SvnTarget source,
                    @NotNull File destination,
                    boolean dryRun,
                    @Nullable SVNDiffOptions diffOptions,
                    @Nullable ISVNEventHandler handler) throws VcsException {
    if (!source.isURL()) {
      throw new IllegalArgumentException("Only urls are supported as source " + source);
    }

    SVNDiffClient client = myVcs.createDiffClient();

    client.setMergeOptions(diffOptions);
    client.setEventHandler(handler);

    try {
      client.doMergeReIntegrate(source.getURL(), source.getPegRevision(), destination, dryRun);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }
}
