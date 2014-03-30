package org.jetbrains.idea.svn.upgrade;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.checkout.SvnKitCheckoutClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitUpgradeClient extends BaseSvnClient implements UpgradeClient {

  @Override
  public void upgrade(@NotNull File path, @NotNull WorkingCopyFormat format, @Nullable ISVNEventHandler handler) throws VcsException {
    validateFormat(format, getSupportedFormats());

    SVNWCClient client = myVcs.createWCClient();

    client.setEventHandler(handler);
    try {
      cleanupIfNecessary(path, format, client, handler);
      upgrade(path, format, client, handler);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @Override
  public List<WorkingCopyFormat> getSupportedFormats() throws VcsException {
    return SvnKitCheckoutClient.SUPPORTED_FORMATS;
  }

  private static void cleanupIfNecessary(@NotNull File path,
                                         @NotNull WorkingCopyFormat format,
                                         @NotNull SVNWCClient client,
                                         @Nullable ISVNEventHandler handler) throws SVNException, VcsException {
    // cleanup is executed only for SVNKit as it could handle both 1.6 and 1.7 formats
    if (WorkingCopyFormat.ONE_DOT_SEVEN.equals(format)) {
      // fake event indicating cleanup start
      callHandler(handler, createEvent(path, SVNEventAction.UPDATE_STARTED));
      client.doCleanup(path);
    }
  }

  private static void upgrade(@NotNull File path,
                              @NotNull WorkingCopyFormat format,
                              @NotNull SVNWCClient client,
                              @Nullable ISVNEventHandler handler) throws SVNException, VcsException {
    // fake event indicating upgrade start
    callHandler(handler, createEvent(path, SVNEventAction.UPDATE_COMPLETED));
    client.doSetWCFormat(path, format.getFormat());
  }
}
