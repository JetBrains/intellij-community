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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.LineHandlerHelper;
import com.intellij.openapi.vcs.LineProcessEventListener;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.AuthenticationCallback;
import org.jetbrains.idea.svn.SvnBindUtil;
import org.jetbrains.idea.svn.config.SvnBindException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Iterator;
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
  public static final String AUTHENTICATION_REALM = "Authentication realm:";
  public static final String CERTIFICATE_ERROR = "Error validating server certificate for";
  public static final String PASSPHRASE_FOR = "Passphrase for";
  public static final String UNABLE_TO_CONNECT = "svn: E170001:";
  public static final String CANNOT_AUTHENTICATE_TO_PROXY = "Could not authenticate to proxy server";

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

  public SvnLineCommand(File workingDirectory, @NotNull SvnCommandName commandName, @NotNull @NonNls String exePath) {
    this(workingDirectory, commandName, exePath, null);
  }

  public SvnLineCommand(File workingDirectory, @NotNull SvnCommandName commandName, @NotNull @NonNls String exePath, File configDir) {
    super(workingDirectory, commandName, exePath, configDir);
    myLineListeners = EventDispatcher.create(LineProcessEventListener.class);
    myExitCode = new AtomicReference<Integer>();
    myErr = new StringBuffer();
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

  public static void runWithAuthenticationAttempt(final String exePath,
                                                  final File firstFile,
                                                  SvnCommandName commandName,
                                                  final LineCommandListener listener,
                                                  @Nullable AuthenticationCallback authenticationCallback,
                                                  final String... parameters) throws SvnBindException {
    File base = firstFile.isDirectory() ? firstFile : firstFile.getParentFile();
    base = SvnBindUtil.correctUpToExistingParent(base);

    listener.baseDirectory(base);

    File configDir = null;

    try {
      // for IDEA proxy case
      if (authenticationCallback != null) {
        writeIdeaConfig2SubversionConfig(authenticationCallback, base);
        configDir = authenticationCallback.getSpecialConfigDir();
      }

      while (true) {
        final SvnLineCommand command = runCommand(exePath, commandName, listener, base, configDir, parameters);
        if (command.myErr.length() > 0) {
          final String errText = command.myErr.toString().trim();
          if (authenticationCallback != null) {
            final AuthCallbackCase callback = createCallback(errText, authenticationCallback, base);
            if (callback != null) {
              cleanup(exePath, commandName, base);
              if (callback.getCredentials(errText)) {
                if (authenticationCallback.getSpecialConfigDir() != null) {
                  configDir = authenticationCallback.getSpecialConfigDir();
                }
                continue;
              }
            }
          }
          throw new SvnBindException(errText);
        }
        final Integer exitCode = command.myExitCode.get();
        if (exitCode != 0) {
          throw new SvnBindException("Svn process exited with error code: " + exitCode);
        }
        return;
      }
    } finally {
      if (authenticationCallback != null) {
        authenticationCallback.reset();
      }
    }
  }

  private static void writeIdeaConfig2SubversionConfig(@NotNull AuthenticationCallback authenticationCallback, @NotNull File base) throws SvnBindException {
    if (authenticationCallback.haveDataForTmpConfig()) {
      try {
        if (! authenticationCallback.persistDataToTmpConfig(base)) {
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

  private static AuthCallbackCase createCallback(final String errText, final AuthenticationCallback callback, final File base) {
    if (errText.startsWith(CERTIFICATE_ERROR)) {
      return new CertificateCallbackCase(callback, base);
    }
    if (errText.startsWith(AUTHENTICATION_REALM)) {
      return new CredentialsCallback(callback, base);
    }
    if (errText.startsWith(PASSPHRASE_FOR)) {
      return new PassphraseCallback(callback, base);
    }
    if (errText.startsWith(UNABLE_TO_CONNECT) && errText.contains(CANNOT_AUTHENTICATE_TO_PROXY)) {
      return new ProxyCallback(callback, base);
    }
    return null;
  }

  private static class ProxyCallback extends AuthCallbackCase {
    protected ProxyCallback(AuthenticationCallback callback, File base) {
      super(callback, base);
    }

    @Override
    boolean getCredentials(String errText) throws SvnBindException {
      return myAuthenticationCallback.askProxyCredentials(myBase);
    }
  }

  private static class CredentialsCallback extends AuthCallbackCase {
    protected CredentialsCallback(AuthenticationCallback callback, File base) {
      super(callback, base);
    }

    @Override
    boolean getCredentials(String errText) throws SvnBindException {
      final String realm = cutFirstLine(errText).substring(AUTHENTICATION_REALM.length()).trim();
      final boolean isPassword = StringUtil.containsIgnoreCase(errText, "password");
      if (myTried) {
        myAuthenticationCallback.clearPassiveCredentials(realm, myBase, isPassword);
      }
      myTried = true;
      if (myAuthenticationCallback.authenticateFor(realm, myBase, myAuthenticationCallback.getSpecialConfigDir() != null, isPassword)) {
        return true;
      }
      throw new SvnBindException("Authentication canceled for realm: " + realm);
    }
  }

  private static String cutFirstLine(final String text) {
    final int idx = text.indexOf('\n');
    if (idx == -1) return text;
    return text.substring(0, idx);
  }

  private static class CertificateCallbackCase extends AuthCallbackCase {
    private CertificateCallbackCase(AuthenticationCallback callback, File base) {
      super(callback, base);
    }

    @Override
    public boolean getCredentials(final String errText) throws SvnBindException {
      String realm = cutFirstLine(errText).substring(CERTIFICATE_ERROR.length());
      final int idx1 = realm.indexOf('\'');
      if (idx1 == -1) {
        throw new SvnBindException("Can not detect authentication realm name: " + errText);
      }
      final int idx2 = realm.indexOf('\'', idx1 + 1);
      if (idx2== -1) {
        throw new SvnBindException("Can not detect authentication realm name: " + errText);
      }
      realm = realm.substring(idx1 + 1, idx2);
      if (! myTried && myAuthenticationCallback.acceptSSLServerCertificate(myBase, realm)) {
        myTried = true;
        return true;
      }
      throw new SvnBindException("Server SSL certificate rejected");
    }
  }

  private static abstract class AuthCallbackCase {
    protected boolean myTried = false;
    protected final AuthenticationCallback myAuthenticationCallback;
    protected final File myBase;

    protected AuthCallbackCase(AuthenticationCallback callback, final File base) {
      myAuthenticationCallback = callback;
      myBase = base;
    }

    abstract boolean getCredentials(final String errText) throws SvnBindException;
  }

  private static void cleanup(String exePath, SvnCommandName commandName, File base) throws SvnBindException {
    File wcRoot = SvnBindUtil.getWcRoot(base);
    if (wcRoot == null) throw new SvnBindException("Can not find working copy root for: " + base.getPath());

    //cleanup -> check command type
    if (commandName.isWriteable()) {
      final SvnSimpleCommand command = new SvnSimpleCommand(wcRoot, SvnCommandName.cleanup, exePath);
      try {
        command.run();
      }
      catch (VcsException e) {
        throw new SvnBindException(e);
      }
    }
  }

  /*svn: E170001: Commit failed (details follow):
  svn: E170001: Unable to connect to a repository at URL 'htt../svn/secondRepo/local2/trunk/mod2/src/com/test/gggGA'
  svn: E170001: OPTIONS of 'htt.../svn/secondRepo/local2/trunk/mod2/src/com/test/gggGA': authorization failed: Could not authenticate to server: rejected Basic challenge (ht)*/
  private final static String ourAuthFailed = "authorization failed";
  private final static String ourAuthFailed2 = "Could not authenticate to server";

  private static boolean isAuthenticationFailed(String s) {
    return s.trim().startsWith(AUTHENTICATION_REALM);
    //return s.contains(ourAuthFailed) && s.contains(ourAuthFailed2);
  }

  private static SvnLineCommand runCommand(String exePath,
                                           SvnCommandName commandName,
                                           final LineCommandListener listener,
                                           File base, File configDir,
                                           String... parameters) throws SvnBindException {
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
          final String trim = text.trim();
          // should end in 1 second
          errorReceived.set(true);
          if (trim.startsWith(UNABLE_TO_CONNECT)) {
            // wait for 3 lines of text then
            if (myErrCnt >= 3) {
              destroyProcess();
            }
          } else if (trim.startsWith(PASSPHRASE_FOR) || myErrCnt >= 2) {
            destroyProcess();
          }
        }
        super.onTextAvailable(text, outputType);
      }
    };

    //command.addParameters("--non-interactive");
    command.addParameters(parameters);
    final AtomicReference<Throwable> exceptionRef = new AtomicReference<Throwable>();
    // several threads
    command.addLineListener(new LineProcessEventListener() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (SvnCommand.LOG.isDebugEnabled()) {
          SvnCommand.LOG.debug("==> " + line);
        }
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          System.out.println("==> " + line);
        }
        listener.onLineAvailable(line, outputType);
        if (listener.isCanceled()) {
          command.destroyProcess();
          return;
        }
        if (ProcessOutputTypes.STDERR.equals(outputType)) {
          if (command.myErr.length() > 0) {
            command.myErr.append('\n');
          }
          command.myErr.append(line);
        }
      }

      @Override
      public void processTerminated(int exitCode) {
        listener.processTerminated(exitCode);
        command.myExitCode.set(exitCode);
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
      if (!finished && errorReceived.get()) {
        command.waitFor(1000);
        command.destroyProcess();
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

  private static class PassphraseCallback extends AuthCallbackCase {
    public PassphraseCallback(AuthenticationCallback callback, File base) {
      super(callback, base);
    }

    @Override
    boolean getCredentials(String errText) throws SvnBindException {
      // try to get from file
      /*if (myTried) {
        myAuthenticationCallback.clearPassiveCredentials(null, myBase);
      }*/
      myTried = true;
      if (myAuthenticationCallback.authenticateFor(null, myBase, myAuthenticationCallback.getSpecialConfigDir() != null, false)) {
        return true;
      }
      throw new SvnBindException("Authentication canceled for : " + errText.substring(PASSPHRASE_FOR.length()));
    }
  }
}
