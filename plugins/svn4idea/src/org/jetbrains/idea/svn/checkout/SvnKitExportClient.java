package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitExportClient extends BaseSvnClient implements ExportClient {

  @Override
  public void export(@NotNull SvnTarget from,
                     @NotNull File to,
                     @Nullable SVNRevision revision,
                     @Nullable SVNDepth depth,
                     @Nullable String nativeLineEnd,
                     boolean force,
                     boolean ignoreExternals,
                     @Nullable ISVNEventHandler handler) throws VcsException {
    SVNUpdateClient client = myVcs.createUpdateClient();

    client.setEventHandler(handler);
    client.setIgnoreExternals(ignoreExternals);

    try {
      if (from.isFile()) {
        client.doExport(from.getFile(), to, from.getPegRevision(), revision, nativeLineEnd, force, depth);
      }
      else {
        client.doExport(from.getURL(), to, from.getPegRevision(), revision, nativeLineEnd, force, depth);
      }
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }
}
