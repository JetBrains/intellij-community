// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class CmdImportClient extends BaseSvnClient implements ImportClient {

  @Override
  public long doImport(@NotNull File path,
                       @NotNull Url url,
                       @Nullable Depth depth,
                       @NotNull String message,
                       boolean noIgnore,
                       @Nullable CommitEventHandler handler,
                       @Nullable Predicate<File> filter) throws VcsException {
    // TODO: Predicate<File> filter is not currently implemented

    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, path, false);
    CommandUtil.put(parameters, Target.on(url), false);
    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, noIgnore, "--no-ignore");
    parameters.add("--message");
    parameters.add(message);

    CmdCheckinClient.CommandListener listener = new CmdCheckinClient.CommandListener(handler);
    listener.setBaseDirectory(path);

    execute(myVcs, Target.on(url), SvnCommandName.importFolder, parameters, listener);

    return listener.getCommittedRevision();
  }
}
