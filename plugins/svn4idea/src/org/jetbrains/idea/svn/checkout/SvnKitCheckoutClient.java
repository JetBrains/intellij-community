package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitCheckoutClient extends BaseSvnClient implements CheckoutClient {

  private static final List<WorkingCopyFormat> SUPPORTED_FORMATS;

  static {
    List<WorkingCopyFormat> supportedFormats = new ArrayList<WorkingCopyFormat>();

    supportedFormats.add(WorkingCopyFormat.ONE_DOT_SEVEN);
    supportedFormats.add(WorkingCopyFormat.ONE_DOT_SIX);

    SUPPORTED_FORMATS = Collections.unmodifiableList(supportedFormats);
  }

  @Override
  public void checkout(@NotNull SvnTarget source,
                       @NotNull File destination,
                       @Nullable SVNRevision revision,
                       @Nullable SVNDepth depth,
                       boolean ignoreExternals,
                       @Nullable WorkingCopyFormat format,
                       @Nullable ISVNEventHandler handler) throws VcsException {
    assertUrl(source);

    if (WorkingCopyFormat.ONE_DOT_EIGHT.equals(format)) {
      throw new IllegalArgumentException("could not check out 1.8 format with SVNKit");
    }

    SVNUpdateClient client = myVcs.createUpdateClient();

    // TODO: most likely we should compare directly with WorkingCopyFormat.ONE_DOT_SIX
    if (!WorkingCopyFormat.ONE_DOT_SEVEN.equals(format)) {
      client.getOperationsFactory().setPrimaryWcGeneration(SvnWcGeneration.V16);
    }

    client.setIgnoreExternals(ignoreExternals);
    client.setEventHandler(handler);

    try {
      client.doCheckout(source.getURL(), destination, source.getPegRevision(), revision, depth, true);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @Override
  public List<WorkingCopyFormat> getSupportedFormats() throws VcsException {
    return SUPPORTED_FORMATS;
  }
}
