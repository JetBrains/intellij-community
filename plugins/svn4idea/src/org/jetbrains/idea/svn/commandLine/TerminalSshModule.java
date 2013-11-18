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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.ServerSSHDialog;
import org.jetbrains.idea.svn.dialogs.SimpleCredentialsDialog;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Kolosovsky.
 */
public class TerminalSshModule extends LineCommandAdapter implements CommandRuntimeModule, InteractiveCommandListener {

  private static final Logger LOG = Logger.getInstance(TerminalSshModule.class);

  private static final Pattern PASSPHRASE_PROMPT = Pattern.compile("Enter passphrase for key \\'(.*)\\':\\s?");
  private static final Pattern PASSWORD_PROMPT = Pattern.compile("(.*)\\'s password:\\s?");

  private static final Pattern UNKNOWN_HOST_MESSAGE =
    Pattern.compile("The authenticity of host \\'((.*) \\((.*)\\))\\' can\\'t be established\\.\\s?");
  private static final Pattern HOST_FINGERPRINT_MESSAGE = Pattern.compile("(\\w+) key fingerprint is (.*)\\.\\s?");
  private static final Pattern ACCEPT_HOST_PROMPT = Pattern.compile("Are you sure you want to continue connecting \\(yes/no\\)\\?\\s?");

  @NotNull private final CommandRuntime myRuntime;
  @NotNull private final CommandExecutor myExecutor;

  private String unknownHost;
  private String fingerprintAlgorithm;
  private String hostFingerprint;

  // TODO: Do not accept executor here and make it as command runtime module
  public TerminalSshModule(@NotNull CommandRuntime runtime, @NotNull CommandExecutor executor) {
    myExecutor = executor;
    myRuntime = runtime;
  }

  @Override
  public void onStart(@NotNull Command command) throws SvnBindException {
  }

  @Override
  public boolean handlePrompt(String line, Key outputType) {
    return checkPassphrase(line) || checkPassword(line) || checkUnknownHost(line);
  }

  private boolean checkPassphrase(@NotNull String line) {
    Matcher matcher = PASSPHRASE_PROMPT.matcher(line);

    return matcher.matches() && handleAuthPrompt(SimpleCredentialsDialog.Mode.SSH_PASSPHRASE, matcher.group(1));
  }

  private boolean checkPassword(@NotNull String line) {
    Matcher matcher = PASSWORD_PROMPT.matcher(line);

    return matcher.matches() && handleAuthPrompt(SimpleCredentialsDialog.Mode.SSH_PASSWORD, matcher.group(1));
  }

  private boolean checkUnknownHost(@NotNull String line) {
    Matcher unknownHostMatcher = UNKNOWN_HOST_MESSAGE.matcher(line);
    Matcher hostFingerPrintMatcher = HOST_FINGERPRINT_MESSAGE.matcher(line);
    Matcher acceptHostMatcher = ACCEPT_HOST_PROMPT.matcher(line);

    if (unknownHostMatcher.matches()) {
      unknownHost = unknownHostMatcher.group(1);
    }
    else if (hostFingerPrintMatcher.matches()) {
      fingerprintAlgorithm = hostFingerPrintMatcher.group(1);
      hostFingerprint = hostFingerPrintMatcher.group(2);
    }
    else if (acceptHostMatcher.matches()) {
      handleUnknownHost();
    }

    return unknownHostMatcher.matches() || hostFingerPrintMatcher.matches() || acceptHostMatcher.matches();
  }

  private void handleUnknownHost() {
    final Project project = myRuntime.getVcs().getProject();
    final Ref<Integer> answer = new Ref<Integer>();

    Runnable command = new Runnable() {
      @Override
      public void run() {
        final ServerSSHDialog dialog = new ServerSSHDialog(project, true, unknownHost, fingerprintAlgorithm, hostFingerprint);
        dialog.show();
        answer.set(dialog.getResult());
      }
    };

    WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(command);

    unknownHost = null;
    fingerprintAlgorithm = null;
    hostFingerprint = null;

    sendAnswer(answer.get() == ISVNAuthenticationProvider.REJECTED ? "no" : "yes");
  }

  private boolean handleAuthPrompt(@NotNull final SimpleCredentialsDialog.Mode mode, @NotNull final String key) {
    final SVNURL repositoryUrl = myExecutor.getCommand().getRepositoryUrl();

    // TODO: repositoryUrl could be null for some cases, for instance for info command for file is invoked that requires
    // TODO: authentication (like "svn info <file> -r HEAD"), if it is invoked before all working copy roots are resolved.
    // TODO: resolving repositoryUrl logic should be updated so that repositoryUrl is not null here.
    String auth =
      myRuntime.getAuthCallback().requestSshCredentials(repositoryUrl != null ? repositoryUrl.toDecodedString() : "", mode, key);

    if (!StringUtil.isEmpty(auth)) {
      sendAnswer(auth);
    } else {
      myExecutor.destroyProcess("Authentication canceled for repository: " + repositoryUrl);
    }

    return !StringUtil.isEmpty(auth);
  }

  private boolean sendAnswer(@NotNull String answer) {
    try {
      myExecutor.write(answer + "\n");
      return true;
    }
    catch (SvnBindException e) {
      // TODO: handle this more carefully
      LOG.info(e);
    }
    return false;
  }
}
