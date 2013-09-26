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
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnUtil;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  public static final String UNABLE_TO_CONNECT_CODE = "svn: E170001:";
  public static final String UNABLE_TO_CONNECT_MESSAGE = "Unable to connect to a repository";
  public static final String CANNOT_AUTHENTICATE_TO_PROXY = "Could not authenticate to proxy server";
  public static final String AUTHENTICATION_FAILED_MESSAGE = "Authentication failed";

  private static final String INVALID_CREDENTIALS_FOR_SVN_PROTOCOL = "svn: E170001: Can't get";
  private static final String UNTRUSTED_SERVER_CERTIFICATE = "Server SSL certificate untrusted";
  private static final String ACCESS_TO_PREFIX = "Access to ";
  private static final String FORBIDDEN_STATUS = "forbidden";
  private static final String PASSWORD_STRING = "password";

  private static final Pattern UNABLE_TO_CONNECT_TO_URL_PATTERN = Pattern.compile("Unable to connect to a repository at URL '(.*)'");

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

  public SvnLineCommand(File workingDirectory, @NotNull SvnCommandName commandName, @NotNull @NonNls String exePath) {
    this(workingDirectory, commandName, exePath, null);
  }

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
    File configDir = null;

    try {
      // for IDEA proxy case
      writeIdeaConfig2SubversionConfig(authenticationCallback, repositoryUrl);
      configDir = authenticationCallback.getSpecialConfigDir();

      while (true) {
        final String exePath = SvnApplicationSettings.getInstance().getCommandLinePath();
        final SvnLineCommand command = runCommand(exePath, commandName, listener, workingDirectory, configDir, parameters);
        final Integer exitCode = command.myExitCode.get();

        // could be situations when exit code = 0, but there is info "warning" in error stream for instance, for "svn status"
        // on non-working copy folder
        // TODO: synchronization does not work well in all cases - sometimes exit code is not yet set and null returned - fix synchronization
        // here we treat null exit code as some non-zero exit code
        if (exitCode == null || exitCode != 0) {
          logNullExitCode(command, exitCode);

          if (command.myErr.length() > 0) {
            // handle authentication
            final String errText = command.myErr.toString().trim();
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
        } else if (command.myErr.length() > 0) {
          // here exitCode == 0, but some warnings are in error stream
          LOG.info("Detected warning - " + command.myErr);
        }
        return command;
      }
    } finally {
      if (authenticationCallback != null) {
        authenticationCallback.reset();
      }
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
    if (errText.startsWith(CERTIFICATE_ERROR)) {
      return new CertificateCallbackCase(callback, url);
    }
    if (errText.startsWith(AUTHENTICATION_REALM)) {
      return new CredentialsCallback(callback, url);
    }
    if (errText.startsWith(PASSPHRASE_FOR)) {
      return new PassphraseCallback(callback, url);
    }
    if (errText.startsWith(UNABLE_TO_CONNECT_CODE) && errText.contains(CANNOT_AUTHENTICATE_TO_PROXY)) {
      return new ProxyCallback(callback, url);
    }
    // http/https protocol invalid credentials
    if (errText.contains(AUTHENTICATION_FAILED_MESSAGE)) {
      return new UsernamePasswordCallback(callback, url);
    }
    // messages could be "Can't get password", "Can't get username or password"
    if (errText.contains(INVALID_CREDENTIALS_FOR_SVN_PROTOCOL) && errText.contains(PASSWORD_STRING)) {
      // svn protocol invalid credentials
      return new UsernamePasswordCallback(callback, url);
    }
    // http/https protocol, svn 1.7, non-interactive
    if (errText.contains(UNABLE_TO_CONNECT_MESSAGE)) {
      return new UsernamePasswordCallback(callback, url);
    }
    // https one-way protocol untrusted server certificate
    if (errText.contains(UNTRUSTED_SERVER_CERTIFICATE)) {
      return new CertificateCallbackCase(callback, url);
    }
    // https two-way protocol invalid client certificate
    if (errText.contains(ACCESS_TO_PREFIX) && errText.contains(FORBIDDEN_STATUS)) {
      return new TwoWaySslCallback(callback, url);
    }
    return null;
  }

  // Special callback for svn 1.8 credentials request as --non-interactive does not return
  // authentication realm (just url) - so we could not create temp cache
  private static class UsernamePasswordCallback extends AuthCallbackCase {
    protected SVNAuthentication myAuthentication;

    protected UsernamePasswordCallback(@NotNull AuthenticationCallback callback, SVNURL url) {
      super(callback, url);
    }

    @Override
    boolean getCredentials(String errText) throws SvnBindException {
      myAuthentication = myAuthenticationCallback.requestCredentials(myUrl != null ? myUrl : parseUrlFromError(errText),
                                                                     getType());

      return myAuthentication != null;
    }

    public String getType() {
      return ISVNAuthenticationManager.PASSWORD;
    }

    @Override
    public void updateParameters(List<String> parameters) {
      if (myAuthentication instanceof SVNPasswordAuthentication) {
        SVNPasswordAuthentication auth = (SVNPasswordAuthentication)myAuthentication;

        parameters.add("--username");
        parameters.add(auth.getUserName());
        parameters.add("--password");
        parameters.add(auth.getPassword());
        if (!auth.isStorageAllowed()) {
          parameters.add("--no-auth-cache");
        }
      }
    }

    private SVNURL parseUrlFromError(String errorText) {
      Matcher matcher = UNABLE_TO_CONNECT_TO_URL_PATTERN.matcher(errorText);
      String urlValue = null;

      if (matcher.find()) {
        urlValue = matcher.group(1);
      }

      return urlValue != null ? parseUrl(urlValue) : null;
    }

    private SVNURL parseUrl(String urlValue) {
      try {
        return SVNURL.parseURIEncoded(urlValue);
      }
      catch (SVNException e) {
        return null;
      }
    }
  }

  private static class ProxyCallback extends AuthCallbackCase {

    protected ProxyCallback(@NotNull AuthenticationCallback callback, SVNURL url) {
      super(callback, url);
    }

    @Override
    boolean getCredentials(String errText) throws SvnBindException {
      return myAuthenticationCallback.askProxyCredentials(myUrl);
    }
  }

  private static class CredentialsCallback extends AuthCallbackCase {

    private CredentialsCallback(@NotNull AuthenticationCallback callback, SVNURL url) {
      super(callback, url);
    }

    @Override
    boolean getCredentials(String errText) throws SvnBindException {
      final String realm =
        errText.startsWith(AUTHENTICATION_REALM) ? cutFirstLine(errText).substring(AUTHENTICATION_REALM.length()).trim() : null;
      final boolean isPassword = StringUtil.containsIgnoreCase(errText, "password");
      if (myTried) {
        myAuthenticationCallback.clearPassiveCredentials(realm, myUrl, isPassword);
      }
      myTried = true;
      if (myAuthenticationCallback.authenticateFor(realm, myUrl, myAuthenticationCallback.getSpecialConfigDir() != null, isPassword)) {
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
    private boolean accepted;

    protected CertificateCallbackCase(@NotNull AuthenticationCallback callback, SVNURL url) {
      super(callback, url);
    }

    @Override
    public boolean getCredentials(final String errText) throws SvnBindException {
      // parse realm from error text
      String realm = errText;
      final int idx1 = realm.indexOf('\'');
      if (idx1 == -1) {
        throw new SvnBindException("Can not detect authentication realm name: " + errText);
      }
      final int idx2 = realm.indexOf('\'', idx1 + 1);
      if (idx2== -1) {
        throw new SvnBindException("Can not detect authentication realm name: " + errText);
      }
      realm = realm.substring(idx1 + 1, idx2);
      if (! myTried && myAuthenticationCallback.acceptSSLServerCertificate(myUrl, realm)) {
        accepted = true;
        myTried = true;
        return true;
      }
      throw new SvnBindException("Server SSL certificate rejected");
    }

    @Override
    public void updateParameters(List<String> parameters) {
      if (accepted) {
        parameters.add("--trust-server-cert");
        // force --non-interactive as it is required by --trust-server-cert, but --non-interactive is not default mode for 1.7 or earlier
        parameters.add("--non-interactive");
      }
    }
  }

  private static class TwoWaySslCallback extends UsernamePasswordCallback {

    protected TwoWaySslCallback(AuthenticationCallback callback, SVNURL url) {
      super(callback, url);
    }

    @Override
    public String getType() {
      return ISVNAuthenticationManager.SSL;
    }

    @Override
    public void updateParameters(List<String> parameters) {
      if (myAuthentication instanceof SVNSSLAuthentication) {
        SVNSSLAuthentication auth = (SVNSSLAuthentication)myAuthentication;

        // TODO: Seems that config option should be specified for concrete server and not for global group.
        // as in that case it could be overriden by settings in config file
        parameters.add("--config-option");
        parameters.add("servers:global:ssl-client-cert-file=" + auth.getCertificatePath());
        parameters.add("--config-option");
        parameters.add("servers:global:ssl-client-cert-password=" + auth.getPassword());
        if (!auth.isStorageAllowed()) {
          parameters.add("--no-auth-cache");
        }
      }
    }
  }

  private static abstract class AuthCallbackCase {
    protected final SVNURL myUrl;
    protected boolean myTried = false;
    @NotNull protected final AuthenticationCallback myAuthenticationCallback;

    protected AuthCallbackCase(@NotNull AuthenticationCallback callback, SVNURL url) {
      myAuthenticationCallback = callback;
      myUrl = url;
    }

    abstract boolean getCredentials(final String errText) throws SvnBindException;

    public void updateParameters(List<String> parameters) {
    }
  }

  private static void cleanup(String exePath, SvnCommand command, @NotNull File workingDirectory) throws SvnBindException {
    if (command.isManuallyDestroyed() && command.getCommandName().isWriteable()) {
      File wcRoot = SvnUtil.getWorkingCopyRootNew(workingDirectory);

      // not all commands require cleanup - for instance, some commands operate only with repository - like "svn info <url>"
      // TODO: check if we could "configure" commands (or make command to explicitly ask) if cleanup is required - not to search
      // TODO: working copy root each time
      if (wcRoot != null) {
        runCommand(exePath, SvnCommandName.cleanup, new SvnCommitRunner.CommandListener(null), wcRoot, null);
      } else {
        LOG.info("Could not execute cleanup for command " + command.getCommandText());
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
        }
        super.onTextAvailable(text, outputType);
      }
    };

    command.addParameters(parameters);
    command.addParameters("--non-interactive");
    final AtomicReference<Throwable> exceptionRef = new AtomicReference<Throwable>();
    // several threads
    command.addLineListener(new LineProcessEventListener() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (ProcessOutputTypes.STDOUT.equals(outputType)) {
          command.myStdOut.append(line);
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

  private static class PassphraseCallback extends AuthCallbackCase {

    protected PassphraseCallback(@NotNull AuthenticationCallback callback, SVNURL url) {
      super(callback, url);
    }

    @Override
    boolean getCredentials(String errText) throws SvnBindException {
      // try to get from file
      /*if (myTried) {
        myAuthenticationCallback.clearPassiveCredentials(null, myBase);
      }*/
      myTried = true;
      if (myAuthenticationCallback.authenticateFor(null, myUrl, myAuthenticationCallback.getSpecialConfigDir() != null, false)) {
        return true;
      }
      throw new SvnBindException("Authentication canceled for : " + errText.substring(PASSPHRASE_FOR.length()));
    }
  }
}
