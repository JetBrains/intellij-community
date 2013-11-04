/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CommandRuntime {

  private static final Logger LOG = Logger.getInstance(CommandRuntime.class);

  @NotNull private final AuthenticationCallback myAuthCallback;
  @NotNull private final SvnVcs myVcs;
  @NotNull private final List<CommandRuntimeModule> myModules;
  private final String exePath;

  public CommandRuntime(@NotNull SvnVcs vcs, @NotNull AuthenticationCallback authCallback) {
    myVcs = vcs;
    myAuthCallback = authCallback;
    exePath = SvnApplicationSettings.getInstance().getCommandLinePath();

    myModules = ContainerUtil.newArrayList();
    myModules.add(new CommandParametersResolutionModule(this));
    myModules.add(new ProxyModule(this));
  }

  @NotNull
  public CommandExecutor runWithAuthenticationAttempt(@NotNull Command command) throws SvnBindException {
    try {
      onStart(command);

      boolean repeat = true;
      CommandExecutor executor = null;
      while (repeat) {
        executor = newExecutor(command);
        executor.run();
        repeat = onAfterCommand(executor, command);
      }
      return executor;
    } finally {
      onFinish();
    }
  }

  private void onStart(@NotNull Command command) throws SvnBindException {
    for (CommandRuntimeModule module : myModules) {
      module.onStart(command);
    }
  }

  private boolean onAfterCommand(@NotNull CommandExecutor executor, @NotNull Command command) throws SvnBindException {
    boolean repeat = false;

    // could be situations when exit code = 0, but there is info "warning" in error stream for instance, for "svn status"
    // on non-working copy folder
    // TODO: synchronization does not work well in all cases - sometimes exit code is not yet set and null returned - fix synchronization
    // here we treat null exit code as some non-zero exit code
    final Integer exitCode = executor.getExitCodeReference();
    if (exitCode == null || exitCode != 0) {
      logNullExitCode(executor, exitCode);

      if (executor.getErrorOutput().length() > 0) {
        // handle authentication
        final String errText = executor.getErrorOutput().trim();
        final AuthCallbackCase callback = createCallback(errText, command.getRepositoryUrl());
        if (callback != null) {
          cleanup(executor, command.getWorkingDirectory());
          if (callback.getCredentials(errText)) {
            if (myAuthCallback.getSpecialConfigDir() != null) {
              command.setConfigDir(myAuthCallback.getSpecialConfigDir());
            }
            callback.updateParameters(command);
            repeat = true;
          } else {
            throw new SvnBindException(errText);
          }
        } else {
          throw new SvnBindException(errText);
        }
      } else {
        // no errors found in error stream => we treat null exitCode as successful, otherwise exception is thrown
        if (exitCode != null) {
          // here exitCode != null && exitCode != 0
          LOG.info("Command - " + executor.getCommandText());
          LOG.info("Command output - " + executor.getOutput());

          throw new SvnBindException("Svn process exited with error code: " + exitCode);
        }
      }
    } else if (executor.getErrorOutput().length() > 0) {
      // here exitCode == 0, but some warnings are in error stream
      LOG.info("Detected warning - " + executor.getErrorOutput());
    }

    return repeat;
  }

  private void onFinish() {
    myAuthCallback.reset();
  }

  private static void logNullExitCode(@NotNull CommandExecutor executor, @Nullable Integer exitCode) {
    if (exitCode == null) {
      LOG.info("Null exit code returned, but not errors detected " + executor.getCommandText());
    }
  }

  @Nullable
  private AuthCallbackCase createCallback(@NotNull final String errText, @Nullable final SVNURL url) {
    List<AuthCallbackCase> authCases = ContainerUtil.newArrayList();

    authCases.add(new CertificateCallbackCase(myAuthCallback, url));
    authCases.add(new CredentialsCallback(myAuthCallback, url));
    authCases.add(new PassphraseCallback(myAuthCallback, url));
    authCases.add(new ProxyCallback(myAuthCallback, url));
    authCases.add(new TwoWaySslCallback(myAuthCallback, url));
    authCases.add(new UsernamePasswordCallback(myAuthCallback, url));

    return ContainerUtil.find(authCases, new Condition<AuthCallbackCase>() {
      @Override
      public boolean value(AuthCallbackCase authCase) {
        return authCase.canHandle(errText);
      }
    });
  }

  private void cleanup(@NotNull CommandExecutor executor, @NotNull File workingDirectory) throws SvnBindException {
    if (executor.isManuallyDestroyed() && executor.getCommandName().isWriteable()) {
      File wcRoot = SvnUtil.getWorkingCopyRootNew(workingDirectory);

      // not all commands require cleanup - for instance, some commands operate only with repository - like "svn info <url>"
      // TODO: check if we could "configure" commands (or make command to explicitly ask) if cleanup is required - not to search
      // TODO: working copy root each time
      if (wcRoot != null) {
        Command cleanupCommand = new Command(SvnCommandName.cleanup);
        cleanupCommand.setWorkingDirectory(wcRoot);

        newExecutor(cleanupCommand).run();
      } else {
        LOG.info("Could not execute cleanup for command " + executor.getCommandText());
      }
    }
  }

  @NotNull
  private CommandExecutor newExecutor(@NotNull Command command) {
    return new CommandExecutor(exePath, command);
  }

  @NotNull
  public AuthenticationCallback getAuthCallback() {
    return myAuthCallback;
  }

  @NotNull
  public SvnVcs getVcs() {
    return myVcs;
  }
}
