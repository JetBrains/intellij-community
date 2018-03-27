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
package org.jetbrains.idea.svn.copy;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.checkin.CmdCheckinClient;
import org.jetbrains.idea.svn.checkin.CommitEventHandler;
import org.jetbrains.idea.svn.commandLine.BaseUpdateCommandListener;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CmdCopyMoveClient extends BaseSvnClient implements CopyMoveClient {

  @Override
  public void copy(@NotNull File src, @NotNull File dst, boolean makeParents, boolean isMove) throws VcsException {
    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, src);
    CommandUtil.put(parameters, dst, false);
    CommandUtil.put(parameters, makeParents, "--parents");

    // for now parsing of the output is not required as command is executed only for one file
    // and will be either successful or exception will be thrown
    // Use idea home directory for directory renames which differ only by character case on case insensitive file systems - otherwise that
    // directory being renamed will be blocked by svn process
    File workingDirectory =
      isMove && !SystemInfo.isFileSystemCaseSensitive && FileUtil.filesEqual(src, dst) ? CommandUtil.getHomeDirectory() : null;
    execute(myVcs, Target.on(dst), workingDirectory, getCommandName(isMove), parameters, null);
  }

  @Override
  public long copy(@NotNull Target source,
                   @NotNull Target destination,
                   @Nullable Revision revision,
                   boolean makeParents,
                   boolean isMove,
                   @NotNull String message,
                   @Nullable CommitEventHandler handler) throws VcsException {
    if (!destination.isUrl()) {
      throw new IllegalArgumentException("Only urls are supported as destination " + destination);
    }

    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, source);
    CommandUtil.put(parameters, destination);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, makeParents, "--parents");
    parameters.add("--message");
    parameters.add(message);

    // copy to url output is the same as commit output - just statuses have "copy of" suffix
    // so "Adding" will be "Adding copy of"
    CmdCheckinClient.CommandListener listener = new CmdCheckinClient.CommandListener(handler);
    if (source.isFile()) {
      listener.setBaseDirectory(source.getFile());
    }
    execute(myVcs, source, getCommandName(isMove), parameters, listener);

    return listener.getCommittedRevision();
  }

  @Override
  public void copy(@NotNull Target source,
                   @NotNull File destination,
                   @Nullable Revision revision,
                   boolean makeParents,
                   @Nullable ProgressTracker handler) throws VcsException {
    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, source);
    CommandUtil.put(parameters, destination);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, makeParents, "--parents");

    File workingDirectory = CommandUtil.getHomeDirectory();
    BaseUpdateCommandListener listener = new BaseUpdateCommandListener(workingDirectory, handler);

    execute(myVcs, source, workingDirectory, SvnCommandName.copy, parameters, listener);

    listener.throwWrappedIfException();
  }

  @NotNull
  private static SvnCommandName getCommandName(boolean isMove) {
    return isMove ? SvnCommandName.move : SvnCommandName.copy;
  }
}
