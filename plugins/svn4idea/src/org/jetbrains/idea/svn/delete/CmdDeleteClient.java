// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.delete;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.checkin.CmdCheckinClient;
import org.jetbrains.idea.svn.commandLine.BaseUpdateCommandListener;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CmdDeleteClient extends BaseSvnClient implements DeleteClient {

  @Override
  public void delete(@NotNull File path, boolean force, boolean dryRun, @Nullable ProgressTracker handler) throws VcsException {
    // TODO: no actual support for dryRun in 'svn delete', SvnKit performs certain validation on file status and svn:externals property
    // TODO: probably add some widespread checks for dryRun delete - but most likely this should be placed upper - in merge logic
    if (!dryRun) {
      List<String> parameters = new ArrayList<>();

      CommandUtil.put(parameters, path);
      CommandUtil.put(parameters, force, "--force");

      File workingDirectory = CommandUtil.getHomeDirectory();
      BaseUpdateCommandListener listener = new BaseUpdateCommandListener(workingDirectory, handler);

      execute(myVcs, Target.on(path), workingDirectory, SvnCommandName.delete, parameters, listener);

      listener.throwWrappedIfException();
    }
  }

  @Override
  public long delete(@NotNull Url url, @NotNull String message) throws VcsException {
    Target target = Target.on(url);
    List<String> parameters = ContainerUtil.newArrayList();

    CommandUtil.put(parameters, target);
    parameters.add("--message");
    parameters.add(message);

    CmdCheckinClient.CommandListener listener = new CmdCheckinClient.CommandListener(null);

    execute(myVcs, target, SvnCommandName.delete, parameters, listener);

    return listener.getCommittedRevision();
  }
}
