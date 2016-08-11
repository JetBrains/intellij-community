package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.util.Version;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.commandLine.BaseUpdateCommandListener;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdCheckoutClient extends BaseSvnClient implements CheckoutClient {
  @Override
  public void checkout(@NotNull SvnTarget source,
                       @NotNull File destination,
                       @Nullable SVNRevision revision,
                       @Nullable Depth depth,
                       boolean ignoreExternals,
                       boolean force,
                       @NotNull WorkingCopyFormat format,
                       @Nullable ProgressTracker handler) throws VcsException {
    validateFormat(format, getSupportedFormats());

    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, source);
    CommandUtil.put(parameters, destination, false);
    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, ignoreExternals, "--ignore-externals");
    CommandUtil.put(parameters, force, "--force"); // corresponds to "allowUnversionedObstructions" in SVNKit

    run(source, destination, handler, parameters);
  }

  @Override
  public List<WorkingCopyFormat> getSupportedFormats() throws VcsException {
    ArrayList<WorkingCopyFormat> result = new ArrayList<>();

    Version version = myFactory.createVersionClient().getVersion();
    result.add(WorkingCopyFormat.from(version));

    return result;
  }

  private void run(@NotNull SvnTarget source,
                   @NotNull File destination,
                   @Nullable ProgressTracker handler,
                   @NotNull List<String> parameters) throws VcsException {
    BaseUpdateCommandListener listener = new BaseUpdateCommandListener(destination, handler);

    execute(myVcs, source, SvnCommandName.checkout, parameters, listener);

    listener.throwWrappedIfException();
  }
}
