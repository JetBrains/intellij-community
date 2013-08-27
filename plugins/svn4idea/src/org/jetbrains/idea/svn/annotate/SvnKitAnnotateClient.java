package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitAnnotateClient extends BaseSvnClient implements AnnotateClient {

  @Override
  public void annotate(@NotNull SvnTarget target,
                       @NotNull SVNRevision startRevision,
                       @NotNull SVNRevision endRevision,
                       @Nullable SVNRevision pegRevision,
                       boolean includeMergedRevisions,
                       @Nullable SVNDiffOptions diffOptions,
                       @Nullable ISVNAnnotateHandler handler) throws VcsException {
    try {
      SVNLogClient client = myVcs.createLogClient();

      client.setDiffOptions(diffOptions);
      if (target.isFile()) {
        client.doAnnotate(target.getFile(), pegRevision, startRevision, endRevision, true, includeMergedRevisions, handler, null);
      }
      else {
        client.doAnnotate(target.getURL(), pegRevision, startRevision, endRevision, true, includeMergedRevisions, handler, null);
      }
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }
}
