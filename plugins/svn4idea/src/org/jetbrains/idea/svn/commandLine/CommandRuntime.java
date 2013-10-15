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

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.InfoCommandRepositoryProvider;
import org.jetbrains.idea.svn.api.Repository;
import org.jetbrains.idea.svn.api.UrlMappingRepositoryProvider;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CommandRuntime {

  private static final Logger LOG = Logger.getInstance(CommandRuntime.class);

  @NotNull private final AuthenticationCallback myAuthCallback;
  @NotNull private final SvnVcs myVcs;
  private final String exePath;

  public CommandRuntime(@NotNull SvnVcs vcs, @NotNull AuthenticationCallback authCallback) {
    myVcs = vcs;
    myAuthCallback = authCallback;
    exePath = SvnApplicationSettings.getInstance().getCommandLinePath();
  }

  public CommandExecutor runWithAuthenticationAttempt(@NotNull Command command) throws SvnBindException {
    try {
      // for IDEA proxy case
      writeIdeaConfig2SubversionConfig(command.getRepositoryUrl());

      if (command.getRepositoryUrl() == null) {
        command.setRepositoryUrl(resolveRepositoryUrl(command));
      }
      if (command.getWorkingDirectory() == null) {
        command.setWorkingDirectory(resolveWorkingDirectory(command));
      }

      command.setConfigDir(myAuthCallback.getSpecialConfigDir());
      command.addParameters("--non-interactive");
      command.saveOriginalParameters();

      while (true) {
        final CommandExecutor executor = newExecutor(command);
        executor.run();
        final Integer exitCode = executor.getExitCodeReference();

        // could be situations when exit code = 0, but there is info "warning" in error stream for instance, for "svn status"
        // on non-working copy folder
        // TODO: synchronization does not work well in all cases - sometimes exit code is not yet set and null returned - fix synchronization
        // here we treat null exit code as some non-zero exit code
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
                List<String> newParameters = command.getParameters();
                callback.updateParameters(newParameters);
                command.setParameters(newParameters);
                continue;
              }
            }
            throw new SvnBindException(errText);
          } else {
            // no errors found in error stream => we treat null exitCode as successful, otherwise exception is thrown
            if (exitCode != null) {
              // here exitCode != null && exitCode != 0
              throw new SvnBindException("Svn process exited with error code: " + exitCode);
            }
          }
        } else if (executor.getErrorOutput().length() > 0) {
          // here exitCode == 0, but some warnings are in error stream
          LOG.info("Detected warning - " + executor.getErrorOutput());
        }
        return executor;
      }
    } finally {
      myAuthCallback.reset();
    }
  }

  private void logNullExitCode(@NotNull CommandExecutor command, @Nullable Integer exitCode) {
    if (exitCode == null) {
      LOG.info("Null exit code returned, but not errors detected " + command.getCommandText());
    }
  }

  private void writeIdeaConfig2SubversionConfig(@Nullable SVNURL repositoryUrl) throws SvnBindException {
    if (myAuthCallback.haveDataForTmpConfig()) {
      try {
        if (!myAuthCallback.persistDataToTmpConfig(repositoryUrl)) {
          throw new SvnBindException("Can not persist " + ApplicationNamesInfo.getInstance().getProductName() +
                                     " HTTP proxy information into tmp config directory");
        }
      }
      catch (IOException e) {
        throw new SvnBindException(e);
      }
      catch (URISyntaxException e) {
        throw new SvnBindException(e);
      }
      assert myAuthCallback.getSpecialConfigDir() != null;
    }
  }

  private AuthCallbackCase createCallback(final String errText, final SVNURL url) {
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

  private void cleanup(CommandExecutor command, @NotNull File workingDirectory) throws SvnBindException {
    if (command.isManuallyDestroyed() && command.getCommandName().isWriteable()) {
      File wcRoot = SvnUtil.getWorkingCopyRootNew(workingDirectory);

      // not all commands require cleanup - for instance, some commands operate only with repository - like "svn info <url>"
      // TODO: check if we could "configure" commands (or make command to explicitly ask) if cleanup is required - not to search
      // TODO: working copy root each time
      if (wcRoot != null) {
        Command cleanupCommand = new Command(SvnCommandName.cleanup);
        cleanupCommand.setWorkingDirectory(wcRoot);

        newExecutor(cleanupCommand).run();
      } else {
        LOG.info("Could not execute cleanup for command " + command.getCommandText());
      }
    }
  }

  private CommandExecutor newExecutor(@NotNull Command command) {
    return new CommandExecutor(exePath, command);
  }

  private SVNURL resolveRepositoryUrl(@NotNull Command command) {
    UrlMappingRepositoryProvider urlMappingProvider = new UrlMappingRepositoryProvider(myVcs, command.getTarget());
    InfoCommandRepositoryProvider infoCommandProvider = new InfoCommandRepositoryProvider(myVcs, command.getTarget());

    Repository repository = urlMappingProvider.get();
    if (repository == null && !SvnCommandName.info.equals(command.getName())) {
      repository = infoCommandProvider.get();
    }

    return repository != null ? repository.getUrl() : null;
  }

  @NotNull
  private File resolveWorkingDirectory(@NotNull Command command) {
    SvnTarget target = command.getTarget();
    File workingDirectory = target.isFile() ? target.getFile() : null;
    // TODO: Do we really need search existing parent - or just take parent directory if target is file???
    workingDirectory = CommandUtil.correctUpToExistingParent(workingDirectory);

    if (workingDirectory == null) {
      workingDirectory =
        !myVcs.getProject().isDefault() ? VfsUtilCore.virtualToIoFile(myVcs.getProject().getBaseDir()) : CommandUtil.getHomeDirectory();
    }

    return workingDirectory;
  }
}
