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
package org.jetbrains.idea.svn.lock;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CmdLockClient extends BaseSvnClient implements LockClient {

  @Override
  public void lock(@NotNull File file, boolean force, @NotNull String message, @Nullable ProgressTracker handler) throws VcsException {
    List<String> parameters = prepareParameters(file, force);

    parameters.add("--message");
    parameters.add(message);

    CommandExecutor command = execute(myVcs, Target.on(file), SvnCommandName.lock, parameters, null);
    handleCommandCompletion(command, file, EventAction.LOCKED, EventAction.LOCK_FAILED, handler);
  }

  @Override
  public void unlock(@NotNull File file, boolean force, @Nullable ProgressTracker handler) throws VcsException {
    List<String> parameters = prepareParameters(file, force);

    CommandExecutor command = execute(myVcs, Target.on(file), SvnCommandName.unlock, parameters, null);
    handleCommandCompletion(command, file, EventAction.UNLOCKED, EventAction.UNLOCK_FAILED, handler);
  }

  private static List<String> prepareParameters(@NotNull File file, boolean force) {
    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, file);
    CommandUtil.put(parameters, force, "--force");

    return parameters;
  }

  private static void handleCommandCompletion(@NotNull CommandExecutor command,
                                              @NotNull File file,
                                              @NotNull EventAction success,
                                              @NotNull EventAction failure,
                                              @Nullable ProgressTracker handler) throws VcsException {
    // just warning appears in output when can not lock/unlock file for some reason (like, that file is already locked)
    String error = SvnUtil.parseWarning(command.getErrorOutput());

    callHandler(handler, createEvent(file, error == null ? success : failure, error));
  }

  @NotNull
  private static ProgressEvent createEvent(@NotNull File file, @NotNull EventAction action, @Nullable String error) {
    return new ProgressEvent(file, -1, null, null, action, error, null);
  }
}
