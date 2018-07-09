// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.commandLine.BaseUpdateCommandListener;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.info.Info;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class CmdUpdateClient extends BaseSvnClient implements UpdateClient {

  @Nullable private ProgressTracker myDispatcher;
  private boolean myIgnoreExternals;

  @Override
  public void setUpdateLocksOnDemand(boolean locksOnDemand) {
  }

  @Override
  public void setEventHandler(ProgressTracker dispatcher) {
    myDispatcher = dispatcher;
  }

  @Override
  public void setIgnoreExternals(boolean ignoreExternals) {
    myIgnoreExternals = ignoreExternals;
  }

  private void checkWorkingCopy(@NotNull File path) throws SvnBindException {
    final Info info = myFactory.createInfoClient().doInfo(path, Revision.UNDEFINED);

    if (info == null || info.getURL() == null) {
      throw new SvnBindException(ErrorCode.WC_NOT_WORKING_COPY, path.getPath());
    }
  }

  private long[] run(@NotNull File path, @NotNull List<String> parameters, @NotNull SvnCommandName command) throws SvnBindException {
    File base = path.isDirectory() ? path : path.getParentFile();

    final AtomicReference<long[]> updatedToRevision = new AtomicReference<>();
    updatedToRevision.set(new long[0]);

    final BaseUpdateCommandListener listener = createCommandListener(new File[]{path}, updatedToRevision, base);
    execute(myVcs, Target.on(base), command, parameters, listener);

    listener.throwWrappedIfException();

    return updatedToRevision.get();
  }

  private BaseUpdateCommandListener createCommandListener(final File[] paths,
                                                          final AtomicReference<long[]> updatedToRevision,
                                                          final File base) {
    return new BaseUpdateCommandListener(base, myDispatcher) {
      final long[] myRevisions = new long[paths.length];

      @Override
      protected void beforeHandler(@NotNull ProgressEvent event) {
        if (EventAction.UPDATE_COMPLETED.equals(event.getAction())) {
          final long eventRevision = event.getRevision();
          for (int i = 0; i < paths.length; i++) {
            final File path = paths[i];
            if (FileUtil.filesEqual(path, event.getFile())) {
              myRevisions[i] = eventRevision;
              break;
            }
          }
        }
      }

      @Override
      public void processTerminated(int exitCode) {
        super.processTerminated(exitCode);
        updatedToRevision.set(myRevisions);
      }
    };
  }

  private static void fillParameters(@NotNull List<String> parameters,
                                     @Nullable Revision revision,
                                     @Nullable Depth depth,
                                     boolean depthIsSticky,
                                     boolean allowUnversionedObstructions) {

    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, depth, depthIsSticky);
    CommandUtil.put(parameters, allowUnversionedObstructions, "--force");
    parameters.add("--accept");
    parameters.add("postpone");
  }

  @Override
  public long doUpdate(File path, Revision revision, Depth depth, boolean allowUnversionedObstructions, boolean depthIsSticky)
    throws SvnBindException {
    checkWorkingCopy(path);

    final List<String> parameters = new ArrayList<>();

    fillParameters(parameters, revision, depth, depthIsSticky, allowUnversionedObstructions);
    CommandUtil.put(parameters, myIgnoreExternals, "--ignore-externals");
    CommandUtil.put(parameters, path);

    final long[] longs = run(path, parameters, SvnCommandName.up);
    return longs[0];
  }

  @Override
  public long doSwitch(File path,
                       Url url,
                       Revision pegRevision,
                       Revision revision,
                       Depth depth,
                       boolean allowUnversionedObstructions,
                       boolean depthIsSticky) throws SvnBindException {
    checkWorkingCopy(path);

    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, Target.on(url, pegRevision));
    CommandUtil.put(parameters, path, false);
    fillParameters(parameters, revision, depth, depthIsSticky, allowUnversionedObstructions);
    if (!myVcs.is16SupportedByCommandLine() ||
        WorkingCopyFormat.from(myFactory.createVersionClient().getVersion()).isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN)) {
      parameters.add("--ignore-ancestry");
    }

    long[] revisions = run(path, parameters, SvnCommandName.switchCopy);

    return revisions != null && revisions.length > 0 ? revisions[0] : -1;
  }
}
