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
package org.jetbrains.idea.svn.api;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.jetbrains.idea.svn.commandLine.*;

import java.io.File;
import java.util.Collection;
import java.util.List;

public abstract class BaseSvnClient implements SvnClient {
  protected SvnVcs myVcs;
  protected ClientFactory myFactory;
  protected boolean myIsActive;

  @NotNull
  @Override
  public SvnVcs getVcs() {
    return myVcs;
  }

  @Override
  public void setVcs(@NotNull SvnVcs vcs) {
    myVcs = vcs;
  }

  @NotNull
  @Override
  public ClientFactory getFactory() {
    return myFactory;
  }

  @Override
  public void setFactory(@NotNull ClientFactory factory) {
    myFactory = factory;
  }

  @Override
  public void setIsActive(boolean isActive) {
    myIsActive = isActive;
  }

  protected void assertUrl(@NotNull Target target) {
    if (!target.isUrl()) {
      throw new IllegalArgumentException("Target should be url " + target);
    }
  }

  protected void assertFile(@NotNull Target target) {
    if (!target.isFile()) {
      throw new IllegalArgumentException("Target should be file " + target);
    }
  }

  protected void assertDirectory(@NotNull Target target) {
    assertFile(target);
    if (!target.getFile().isDirectory()) {
      throw new IllegalArgumentException("Target should be directory " + target);
    }
  }

  protected void validateFormat(@NotNull WorkingCopyFormat format, @NotNull Collection<WorkingCopyFormat> supported) throws VcsException {
    if (!supported.contains(format)) {
      throw new VcsException(
        String.format("%s format is not supported. Supported formats are: %s.", format.getName(), StringUtil.join(supported, ",")));
    }
  }

  @NotNull
  public CommandExecutor execute(@NotNull SvnVcs vcs,
                                 @NotNull Target target,
                                 @NotNull SvnCommandName name,
                                 @NotNull List<String> parameters,
                                 @Nullable LineCommandListener listener) throws SvnBindException {
    return execute(vcs, target, null, name, parameters, listener);
  }

  @NotNull
  public CommandExecutor execute(@NotNull SvnVcs vcs,
                                 @NotNull Target target,
                                 @Nullable File workingDirectory,
                                 @NotNull SvnCommandName name,
                                 @NotNull List<String> parameters,
                                 @Nullable LineCommandListener listener) throws SvnBindException {
    Command command = newCommand(name);

    command.put(parameters);

    return execute(vcs, target, workingDirectory, command, listener);
  }

  @NotNull
  public CommandExecutor execute(@NotNull SvnVcs vcs,
                                 @NotNull Target target,
                                 @Nullable File workingDirectory,
                                 @NotNull Command command,
                                 @Nullable LineCommandListener listener) throws SvnBindException {
    command.setTarget(target);
    command.setWorkingDirectory(workingDirectory);
    command.setResultBuilder(listener);

    return newRuntime(vcs).runWithAuthenticationAttempt(command);
  }

  @NotNull
  public Command newCommand(@NotNull SvnCommandName name) {
    return new Command(name);
  }

  @NotNull
  public CommandRuntime newRuntime(@NotNull SvnVcs vcs) {
    return new CommandRuntime(vcs, new AuthenticationService(vcs, myIsActive));
  }

  public static void callHandler(@Nullable ProgressTracker handler, @NotNull ProgressEvent event) throws SvnBindException {
    if (handler != null) {
      handler.consume(event);
    }
  }

  @NotNull
  protected static ProgressEvent createEvent(@NotNull File path, @Nullable EventAction action) {
    return new ProgressEvent(path, 0, null, null, action, null, null);
  }

  @NotNull
  protected static Revision notNullize(@Nullable Revision revision) {
    return revision != null ? revision : Revision.UNDEFINED;
  }
}
