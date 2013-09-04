package org.jetbrains.idea.svn.integrate;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.LineCommandListener;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.commandLine.UpdateOutputLineConverter;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
    final AtomicReference<SVNException> excRef = new AtomicReference<SVNException>();
    final UpdateOutputLineConverter converter = new UpdateOutputLineConverter(destination);
    LineCommandListener listener = createListener(handler, excRef, converter);

    CommandUtil.execute(myVcs, SvnCommandName.merge, parameters, null, listener);

    throwIfException(excRef);
  }

  private static void throwIfException(AtomicReference<SVNException> exception) throws VcsException {
    SVNException e = exception.get();

    if (e != null) {
      throw new VcsException(e);
    }
  }

  private static LineCommandListener createListener(@Nullable final ISVNEventHandler handler,
                                                    @NotNull final AtomicReference<SVNException> excRef,
                                                    @NotNull final UpdateOutputLineConverter converter) {
    LineCommandListener result = null;

    if (handler != null) {
      result = new LineCommandListener() {
        @Override
        public void baseDirectory(File file) {
        }

        @Override
        public void onLineAvailable(String line, Key outputType) {
          if (ProcessOutputTypes.STDOUT.equals(outputType)) {
            final SVNEvent event = converter.convert(line);
            if (event != null) {
              try {
                handler.handleEvent(event, 0.5);
              }
              catch (SVNException e) {
                cancel();
                excRef.set(e);
              }
            }
          }
        }
      };
    }

    return result;
  }
}
