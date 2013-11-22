package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.*;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdMergeClient extends BaseSvnClient implements MergeClient {
  @Override
  public void merge(@NotNull SvnTarget source,
                    @NotNull File destination,
                    boolean dryRun,
                    @Nullable SVNDiffOptions diffOptions,
                    @Nullable final ISVNEventHandler handler) throws VcsException {
    assertUrl(source);

    List<String> parameters = new ArrayList<String>();
    CommandUtil.put(parameters, source);
    fillParameters(parameters, destination, null, dryRun, false, false, true, diffOptions);

    run(destination, handler, parameters);
  }

  @Override
  public void merge(@NotNull SvnTarget source,
                    @NotNull SVNRevisionRange range,
                    @NotNull File destination,
                    @Nullable SVNDepth depth,
                    boolean dryRun,
                    boolean recordOnly,
                    boolean force,
                    @Nullable SVNDiffOptions diffOptions,
                    @Nullable ISVNEventHandler handler) throws VcsException {
    assertUrl(source);

    List<String> parameters = new ArrayList<String>();

    parameters.add("--revision");
    parameters.add(range.getStartRevision() + ":" + range.getEndRevision());
    CommandUtil.put(parameters, source);
    fillParameters(parameters, destination, depth, dryRun, recordOnly, force, false, diffOptions);

    run(destination, handler, parameters);
  }

  @Override
  public void merge(@NotNull SvnTarget source1,
                    @NotNull SvnTarget source2,
                    @NotNull File destination,
                    @Nullable SVNDepth depth,
                    boolean useAncestry,
                    boolean dryRun,
                    boolean recordOnly,
                    boolean force,
                    @Nullable SVNDiffOptions diffOptions,
                    @Nullable ISVNEventHandler handler) throws VcsException {
    assertUrl(source1);
    assertUrl(source2);

    List<String> parameters = new ArrayList<String>();

    CommandUtil.put(parameters, source1);
    CommandUtil.put(parameters, source2);
    fillParameters(parameters, destination, depth, dryRun, recordOnly, force, false, diffOptions);
    CommandUtil.put(parameters, !useAncestry, "--ignore-ancestry");

    run(destination, handler, parameters);
  }

  private static void fillParameters(@NotNull List<String> parameters,
                                     @NotNull File destination,
                                     @Nullable SVNDepth depth,
                                     boolean dryRun,
                                     boolean recordOnly,
                                     boolean force,
                                     boolean reintegrate,
                                     @Nullable SVNDiffOptions diffOptions) {
    CommandUtil.put(parameters, destination);
    CommandUtil.put(parameters, diffOptions);
    CommandUtil.put(parameters, dryRun, "--dry-run");

    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, force, "--force");
    CommandUtil.put(parameters, recordOnly, "--record-only");

    parameters.add("--accept");
    parameters.add("postpone");
    // deprecated for 1.8, but should be specified for previous clients
    CommandUtil.put(parameters, reintegrate, "--reintegrate");
  }

  private void run(File destination, ISVNEventHandler handler, List<String> parameters) throws VcsException {
    BaseUpdateCommandListener listener = new BaseUpdateCommandListener(CommandUtil.correctUpToExistingParent(destination), handler);

    CommandUtil.execute(myVcs, SvnTarget.fromFile(destination), SvnCommandName.merge, parameters, listener);

    listener.throwWrappedIfException();
  }
}
