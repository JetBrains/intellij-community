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

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.LineHandlerHelper;
import com.intellij.openapi.vcs.LineProcessEventListener;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 4:05 PM
 *
 * honestly stolen from GitLineHandler
 */
public class SvnLineCommand extends SvnCommand {

  // kept for exact text
  //public static final String CLIENT_CERTIFICATE_FILENAME = "Client certificate filename:";
  /**
   * the partial line from stdout stream
   */
  private final StringBuilder myStdoutLine = new StringBuilder();
  /**
   * the partial line from stderr stream
   */
  private final StringBuilder myStderrLine = new StringBuilder();
  private final EventDispatcher<LineProcessEventListener> myLineListeners;
  private final AtomicReference<Integer> myExitCode;
  private final StringBuffer myErr;
  private final StringBuffer myStdOut;

  public SvnLineCommand(File workingDirectory, @NotNull SvnCommandName commandName, @NotNull @NonNls String exePath, File configDir) {
    super(workingDirectory, commandName, exePath, configDir);
    myLineListeners = EventDispatcher.create(LineProcessEventListener.class);
    myExitCode = new AtomicReference<Integer>();
    myErr = new StringBuffer();
    myStdOut = new StringBuffer();
  }

  @Override
  protected void processTerminated(int exitCode) {
    // force newline
    if (myStdoutLine.length() != 0) {
      onTextAvailable("\n\r", ProcessOutputTypes.STDOUT);
    }
    else if (myStderrLine.length() != 0) {
      onTextAvailable("\n\r", ProcessOutputTypes.STDERR);
    }
  }

  public static SvnLineCommand runWithAuthenticationAttempt(@NotNull final File workingDirectory,
                                                            @Nullable final SVNURL repositoryUrl,
                                                            SvnCommandName commandName,
                                                            final LineCommandListener listener,
                                                            @NotNull AuthenticationCallback authenticationCallback,
                                                            String... parameters) throws SvnBindException {
    try {
      // for IDEA proxy case
      writeIdeaConfig2SubversionConfig(authenticationCallback, repositoryUrl);
      File configDir = authenticationCallback.getSpecialConfigDir();
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

          if (command.getError().length() > 0) {
            // handle authentication
            final String errText = command.getError().toString().trim();
            final AuthCallbackCase callback = createCallback(errText, authenticationCallback, repositoryUrl);
            if (callback != null) {
              cleanup(exePath, command, workingDirectory);
              if (callback.getCredentials(errText)) {
                if (authenticationCallback.getSpecialConfigDir() != null) {
                  configDir = authenticationCallback.getSpecialConfigDir();
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
        } else if (command.getError().length() > 0) {
          // here exitCode == 0, but some warnings are in error stream
          LOG.info("Detected warning - " + command.getError());
        }
        return command;
      }
    } finally {
      authenticationCallback.reset();
    }
  }

  private static void logNullExitCode(@NotNull SvnLineCommand command, @Nullable Integer exitCode) {
    if (exitCode == null) {
      LOG.info("Null exit code returned, but not errors detected " + command.getCommandText());
    }
  }

  private static String[] updateParameters(AuthCallbackCase callback, String[] parameters) {
    List<String> p = new ArrayList<String>(Arrays.asList(parameters));

    callback.updateParameters(p);
    return ArrayUtil.toStringArray(p);
  }

  private static void writeIdeaConfig2SubversionConfig(@NotNull AuthenticationCallback authenticationCallback,
                                                       @Nullable SVNURL repositoryUrl) throws SvnBindException {
    if (authenticationCallback.haveDataForTmpConfig()) {
      try {
        if (!authenticationCallback.persistDataToTmpConfig(repositoryUrl)) {
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
      assert authenticationCallback.getSpecialConfigDir() != null;
    }
  }

  private static AuthCallbackCase createCallback(final String errText,
                                                 @NotNull final AuthenticationCallback callback,
                                                 final SVNURL url) {
    List<AuthCallbackCase> authCases = ContainerUtil.newArrayList();

    authCases.add(new CertificateCallbackCase(callback, url));
    authCases.add(new CredentialsCallback(callback, url));
    authCases.add(new PassphraseCallback(callback, url));
    authCases.add(new ProxyCallback(callback, url));
    authCases.add(new TwoWaySslCallback(callback, url));
    authCases.add(new UsernamePasswordCallback(callback, url));

    return ContainerUtil.find(authCases, new Condition<AuthCallbackCase>() {
      @Override
      public boolean value(AuthCallbackCase authCase) {
        return authCase.canHandle(errText);
      }
    });
  }

  private static void cleanup(String exePath, SvnCommand command, @NotNull File workingDirectory) throws SvnBindException {
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

  private static SvnLineCommand runCommand(String exePath,
                                           SvnCommandName commandName,
                                           final LineCommandListener listener,
                                           File base, File configDir,
                                           String[] parameters, String[] originalParameters) throws SvnBindException {
    final AtomicBoolean errorReceived = new AtomicBoolean(false);
    final SvnLineCommand command = new SvnLineCommand(base, commandName, exePath, configDir) {
      int myErrCnt = 0;

      @Override
      protected void onTextAvailable(String text, Key outputType) {

        // we won't stop right now if got "authentication realm" -> since we want to get "password" string (that would mean password is expected
        // or certificate maybe is expected
        // but for certificate passphrase we get just "Passphrase for ..." - one line without line feed

        // for client certificate (when no path in servers file) we get
        // Authentication realm: <text>
        // Client certificate filename:
        if (ProcessOutputTypes.STDERR.equals(outputType)) {
          ++ myErrCnt;
          // should end in 1 second
          errorReceived.set(true);
        }
        super.onTextAvailable(text, outputType);
      }
    };

    command.setOriginalParameters(originalParameters);

    command.addParameters(parameters);
    command.addParameters("--non-interactive");
    final AtomicReference<Throwable> exceptionRef = new AtomicReference<Throwable>();
    // several threads
    command.addLineListener(new LineProcessEventListener() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (ProcessOutputTypes.STDOUT.equals(outputType)) {
          command.getStdOut().append(line);
        }

        if (SvnCommand.LOG.isDebugEnabled()) {
          SvnCommand.LOG.debug("==> " + line);
        }
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          System.out.println("==> " + line);
        }
        listener.onLineAvailable(line, outputType);
        if (listener.isCanceled()) {
          LOG.info("Cancelling command: " + command.getCommandText());
          command.destroyProcess();
          return;
        }
        if (ProcessOutputTypes.STDERR.equals(outputType)) {
          if (command.getError().length() > 0) {
            command.getError().append('\n');
          }
          command.getError().append(line);
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
      if (!finished && (errorReceived.get() || command.needsDestroy())) {
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

  @Override
  protected void onTextAvailable(String text, Key outputType) {
    Iterator<String> lines = LineHandlerHelper.splitText(text).iterator();
    if (ProcessOutputTypes.STDOUT == outputType) {
      notifyLines(outputType, lines, myStdoutLine);
    }
    else if (ProcessOutputTypes.STDERR == outputType) {
      notifyLines(outputType, lines, myStderrLine);
    }
  }

  private void notifyLines(final Key outputType, final Iterator<String> lines, final StringBuilder lineBuilder) {
    if (!lines.hasNext()) return;
    if (lineBuilder.length() > 0) {
      lineBuilder.append(lines.next());
      if (lines.hasNext()) {
        // line is complete
        final String line = lineBuilder.toString();
        notifyLine(line, outputType);
        lineBuilder.setLength(0);
      }
    }
    while (true) {
      String line = null;
      if (lines.hasNext()) {
        line = lines.next();
      }

      if (lines.hasNext()) {
        notifyLine(line, outputType);
      }
      else {
        if (line != null && line.length() > 0) {
          lineBuilder.append(line);
        }
        break;
      }
    }
  }

  private void notifyLine(final String line, final Key outputType) {
    String trimmed = LineHandlerHelper.trimLineSeparator(line);
    myLineListeners.getMulticaster().onLineAvailable(trimmed, outputType);
  }

  public void addLineListener(LineProcessEventListener listener) {
    myLineListeners.addListener(listener);
    super.addListener(listener);
  }

  public StringBuffer getError() {
    return myErr;
  }

  public StringBuffer getStdOut() {
    return myStdOut;
  }

  public Integer getExitCodeReference() {
    return myExitCode.get();
  }

  public void setExitCodeReference(int value) {
    myExitCode.set(value);
  }
}
