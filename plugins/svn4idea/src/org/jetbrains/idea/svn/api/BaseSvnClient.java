// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api;

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

import static com.intellij.openapi.util.text.StringUtil.join;
import static org.jetbrains.idea.svn.SvnBundle.message;

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
        message("error.format.is.not.supported", format.getDisplayName(), join(supported, it -> it.getDisplayName(), ",")));
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
