package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitCheckoutClient extends BaseSvnClient implements CheckoutClient {

  public static final List<WorkingCopyFormat> SUPPORTED_FORMATS;

  static {
    List<WorkingCopyFormat> supportedFormats = new ArrayList<>();

    supportedFormats.add(WorkingCopyFormat.ONE_DOT_SEVEN);
    supportedFormats.add(WorkingCopyFormat.ONE_DOT_SIX);

    SUPPORTED_FORMATS = Collections.unmodifiableList(supportedFormats);
  }

  @Override
  public void checkout(@NotNull SvnTarget source,
                       @NotNull File destination,
                       @Nullable SVNRevision revision,
                       @Nullable Depth depth,
                       boolean ignoreExternals,
                       boolean force,
                       @NotNull WorkingCopyFormat format,
                       @Nullable ProgressTracker handler) throws VcsException {
    assertUrl(source);
    validateFormat(format, getSupportedFormats());

    SVNUpdateClient client = myVcs.getSvnKitManager().createUpdateClient();

    client.setIgnoreExternals(ignoreExternals);
    client.setEventHandler(toEventHandler(handler));

    try {
      runCheckout(client, format, source, destination, revision, depth, force);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  /**
   * This is mostly inlined {@code SVNUpdateClient.doCheckout()} - to allow specifying necessary working copy format. Otherwise, if only
   * {@link SvnWcGeneration} is used - either svn 1.6 or svn 1.8 working copy will be created.
   * <p/>
   * See also http://issues.tmatesoft.com/issue/SVNKIT-495 for more details.
   */
  private static void runCheckout(@NotNull SVNUpdateClient client,
                                  @NotNull WorkingCopyFormat format,
                                  @NotNull SvnTarget source,
                                  @NotNull File destination,
                                  @Nullable SVNRevision revision,
                                  @Nullable Depth depth,
                                  boolean force) throws SVNException {
    SvnCheckout checkoutOperation = createCheckoutOperation(client, format);

    checkoutOperation.setUpdateLocksOnDemand(client.isUpdateLocksOnDemand());
    checkoutOperation.setSource(SvnTarget.fromURL(source.getURL(), source.getPegRevision()));
    checkoutOperation.setSingleTarget(SvnTarget.fromFile(destination));
    checkoutOperation.setRevision(revision);
    checkoutOperation.setDepth(toDepth(depth));
    checkoutOperation.setAllowUnversionedObstructions(force);
    checkoutOperation.setIgnoreExternals(client.isIgnoreExternals());
    checkoutOperation.setExternalsHandler(SvnCodec.externalsHandler(client.getExternalsHandler()));

    checkoutOperation.run();
  }

  @NotNull
  private static SvnCheckout createCheckoutOperation(@NotNull SVNUpdateClient client, @NotNull WorkingCopyFormat format) {
    if (WorkingCopyFormat.ONE_DOT_SIX.equals(format)) {
      client.getOperationsFactory().setPrimaryWcGeneration(SvnWcGeneration.V16);
    }

    SvnCheckout checkoutOperation = client.getOperationsFactory().createCheckout();

    if (WorkingCopyFormat.ONE_DOT_SEVEN.equals(format)) {
      checkoutOperation.setTargetWorkingCopyFormat(ISVNWCDb.WC_FORMAT_17);
    }

    return checkoutOperation;
  }

  @Override
  public List<WorkingCopyFormat> getSupportedFormats() throws VcsException {
    return SUPPORTED_FORMATS;
  }
}
