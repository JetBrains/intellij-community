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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnUtil;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Konstantin Kolosovsky.
 */
public class CommandRuntime {

  private static final Logger LOG = Logger.getInstance(CommandRuntime.class);

  @NotNull private final AuthenticationCallback myAuthCallback;

  public CommandRuntime(@NotNull AuthenticationCallback authCallback) {
    myAuthCallback = authCallback;
  }

  public SvnLineCommand runWithAuthenticationAttempt(@NotNull final File workingDirectory,
                                                     @Nullable final SVNURL repositoryUrl,
                                                     SvnCommandName commandName,
                                                     final LineCommandListener listener,
                                                     String... parameters) throws SvnBindException {
    try {
      // for IDEA proxy case
      writeIdeaConfig2SubversionConfig(repositoryUrl);
      File configDir = myAuthCallback.getSpecialConfigDir();
      String[] originalParameters = Arrays.copyOf(parameters, parameters.length);

      while (true) {
        final String exePath = SvnApplicationSettings.getInstance().getCommandLinePath();
        final SvnLineCommand command =
          runCommand(exePath, commandName, listener, workingDirectory, configDir, parameters, originalParameters);
        final Integer exitCode = command.getExitCodeReference();

        // could be situations when exit code = 0, but there is info "warning" in error stream for instance, for "svn status"
        // on non-working copy folder
        // TODO: synchronization does not work well in all cases - sometimes exit code is not yet set and null returned - fix synchronization
        // here we treat null exit code as some non-zero exit code
        if (exitCode == null || exitCode != 0) {
          logNullExitCode(command, exitCode);

          if (command.getErrorOutput().length() > 0) {
            // handle authentication
            final String errText = command.getErrorOutput().trim();
            final AuthCallbackCase callback = createCallback(errText, repositoryUrl);
            if (callback != null) {
              cleanup(exePath, command, workingDirectory);
              if (callback.getCredentials(errText)) {
                if (myAuthCallback.getSpecialConfigDir() != null) {
                  configDir = myAuthCallback.getSpecialConfigDir();
                }
                parameters = updateParameters(callback, parameters);
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
        } else if (command.getErrorOutput().length() > 0) {
          // here exitCode == 0, but some warnings are in error stream
          LOG.info("Detected warning - " + command.getErrorOutput());
        }
        return command;
      }
    } finally {
      myAuthCallback.reset();
    }
  }

  private void logNullExitCode(@NotNull SvnLineCommand command, @Nullable Integer exitCode) {
    if (exitCode == null) {
      LOG.info("Null exit code returned, but not errors detected " + command.getCommandText());
    }
  }

  private String[] updateParameters(AuthCallbackCase callback, String[] parameters) {
    List<String> p = new ArrayList<String>(Arrays.asList(parameters));

    callback.updateParameters(p);
    return ArrayUtil.toStringArray(p);
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

  private void cleanup(String exePath, SvnCommand command, @NotNull File workingDirectory) throws SvnBindException {
    if (command.isManuallyDestroyed() && command.getCommandName().isWriteable()) {
      File wcRoot = SvnUtil.getWorkingCopyRootNew(workingDirectory);

      // not all commands require cleanup - for instance, some commands operate only with repository - like "svn info <url>"
      // TODO: check if we could "configure" commands (or make command to explicitly ask) if cleanup is required - not to search
      // TODO: working copy root each time
      if (wcRoot != null) {
        runCommand(exePath, SvnCommandName.cleanup, new SvnCommitRunner.CommandListener(null), wcRoot, null, null, null);
      } else {
        LOG.info("Could not execute cleanup for command " + command.getCommandText());
      }
    }
  }

  private SvnLineCommand runCommand(String exePath,
                                    SvnCommandName commandName,
                                    final LineCommandListener listener,
                                    File base, File configDir,
                                    String[] parameters, String[] originalParameters) throws SvnBindException {
    final SvnLineCommand command = new SvnLineCommand(base, commandName, exePath, configDir);

    command.setOriginalParameters(originalParameters);
    command.addParameters(parameters);
    command.addParameters("--non-interactive");
    final AtomicReference<Throwable> exceptionRef = new AtomicReference<Throwable>();
    // several threads
    command.addListener(new LineCommandAdapter() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("==> " + line);
        }
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          System.out.println("==> " + line);
        }
        listener.onLineAvailable(line, outputType);
        if (listener.isCanceled()) {
          LOG.info("Cancelling command: " + command.getCommandText());
          command.destroyProcess();
        }
      }

      @Override
      public void processTerminated(int exitCode) {
        listener.processTerminated(exitCode);
        command.setExitCodeReference(exitCode);
      }

      @Override
      public void startFailed(Throwable exception) {
        listener.startFailed(exception);
        exceptionRef.set(exception);
      }
    });
    command.start();
    boolean finished;
    do {
      finished = command.waitFor(500);
      if (!finished && (command.wasError() || command.needsDestroy())) {
        command.waitFor(1000);
        command.doDestroyProcess();
        break;
      }
    }
    while (!finished);

    if (exceptionRef.get() != null) {
      throw new SvnBindException(exceptionRef.get());
    }
    return command;
  }
}
