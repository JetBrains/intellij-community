// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.commandLine.BaseUpdateCommandListener;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.diff.DiffOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CmdMergeClient extends BaseSvnClient implements MergeClient {
  @Override
  public void merge(@NotNull Target source,
                    @NotNull File destination,
                    boolean dryRun,
                    boolean reintegrate,
                    @Nullable DiffOptions diffOptions,
                    @Nullable ProgressTracker handler) throws VcsException {
    assertUrl(source);

    List<String> parameters = new ArrayList<>();
    CommandUtil.put(parameters, source);
    fillParameters(parameters, destination, null, dryRun, false, false, reintegrate, diffOptions);

    run(destination, handler, parameters);
  }

  @Override
  public void merge(@NotNull Target source,
                    @NotNull RevisionRange range,
                    @NotNull File destination,
                    @Nullable Depth depth,
                    boolean dryRun,
                    boolean recordOnly,
                    boolean force,
                    @Nullable DiffOptions diffOptions,
                    @Nullable ProgressTracker handler) throws VcsException {
    assertUrl(source);

    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, range.getStartRevision(), range.getEndRevision());
    CommandUtil.put(parameters, source);
    fillParameters(parameters, destination, depth, dryRun, recordOnly, force, false, diffOptions);

    run(destination, handler, parameters);
  }

  @Override
  public void merge(@NotNull Target source1,
                    @NotNull Target source2,
                    @NotNull File destination,
                    @Nullable Depth depth,
                    boolean useAncestry,
                    boolean dryRun,
                    boolean recordOnly,
                    boolean force,
                    @Nullable DiffOptions diffOptions,
                    @Nullable ProgressTracker handler) throws VcsException {
    assertUrl(source1);
    assertUrl(source2);

    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, source1);
    CommandUtil.put(parameters, source2);
    fillParameters(parameters, destination, depth, dryRun, recordOnly, force, false, diffOptions);
    CommandUtil.put(parameters, !useAncestry, "--ignore-ancestry");

    run(destination, handler, parameters);
  }

  private static void fillParameters(@NotNull List<String> parameters,
                                     @NotNull File destination,
                                     @Nullable Depth depth,
                                     boolean dryRun,
                                     boolean recordOnly,
                                     boolean force,
                                     boolean reintegrate,
                                     @Nullable DiffOptions diffOptions) {
    CommandUtil.put(parameters, destination);
    CommandUtil.put(parameters, diffOptions);
    CommandUtil.put(parameters, dryRun, "--dry-run");

    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, force, "--force");
    CommandUtil.put(parameters, recordOnly, "--record-only");

    CommandUtil.put(parameters, "--accept", "postpone");
    // deprecated for 1.8, but should be specified for previous clients
    CommandUtil.put(parameters, reintegrate, "--reintegrate");
  }

  private void run(File destination, ProgressTracker handler, List<String> parameters) throws VcsException {
    BaseUpdateCommandListener listener = new BaseUpdateCommandListener(CommandUtil.requireExistingParent(destination), handler);

    execute(myVcs, Target.on(destination), SvnCommandName.merge, parameters, listener);

    listener.throwWrappedIfException();
  }
}
