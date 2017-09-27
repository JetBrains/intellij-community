/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class CmdImportClient extends BaseSvnClient implements ImportClient {

  @Override
  public long doImport(@NotNull File path,
                       @NotNull SVNURL url,
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
