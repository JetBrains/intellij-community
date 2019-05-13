// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.AcceptResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allows responding to "accept certificate" prompts of the following structure:
 * <p/>
 * Error validating server certificate for '%server_url%':
 * %certificate_error%
 * Certificate information:
 * %certificate_info%
 * (R)eject, accept (t)emporarily or accept (p)ermanently?
 *
 * @author Konstantin Kolosovsky.
 */
public class TerminalSslCertificateModule extends BaseTerminalModule {

  private static final Pattern ERROR_VALIDATING_CERTIFICATE_MESSAGE =
    Pattern.compile("Error validating server certificate for \\'(.*)\\':\\s?");
  private static final Pattern CERTIFICATE_INFORMATION_MESSAGE = Pattern.compile("Certificate information:\\s?");
  // messages could be either "(R)eject or accept (t)emporarily?" or "(R)eject, accept (t)emporarily or accept (p)ermanently?" depending on
  // if credentials caching is allowed
  private static final Pattern ACCEPT_CERTIFICATE_PROMPT = Pattern.compile("\\(R\\)eject.*\\?\\s?");

  @NotNull private final StringBuilder certificateError = new StringBuilder();
  @NotNull private final StringBuilder certificateInfo = new StringBuilder();
  private boolean collectingCertificateError;
  private boolean collectingCertificateInfo;
  private String serverUrl;

  public TerminalSslCertificateModule(@NotNull CommandRuntime runtime, @NotNull CommandExecutor executor) {
    super(runtime, executor);
  }

  @Override
  public boolean doHandlePrompt(String line, Key outputType) {
    return checkCertificate(line);
  }

  private boolean checkCertificate(String line) {
    Matcher certificateErrorMatcher = ERROR_VALIDATING_CERTIFICATE_MESSAGE.matcher(line);
    Matcher certificateInfoMatcher = CERTIFICATE_INFORMATION_MESSAGE.matcher(line);
    Matcher acceptCertificateMatcher = ACCEPT_CERTIFICATE_PROMPT.matcher(line);

    if (certificateErrorMatcher.matches()) {
      serverUrl = certificateErrorMatcher.group(1);
      setCollectingMode(false, true);
    }
    else if (certificateInfoMatcher.matches()) {
      setCollectingMode(true, false);
    }
    else if (acceptCertificateMatcher.matches()) {
      setCollectingMode(false, false);
      handleAcceptCertificatePrompt();
    }
    else if (collectingCertificateError) {
      certificateError.append(line);
    }
    else if (collectingCertificateInfo) {
      certificateInfo.append(line);
    }

    return certificateErrorMatcher.matches() ||
           certificateInfoMatcher.matches() ||
           acceptCertificateMatcher.matches() ||
           collectingCertificateError ||
           collectingCertificateInfo;
  }

  private void setCollectingMode(boolean collectInfo, boolean collectError) {
    collectingCertificateInfo = collectInfo;
    collectingCertificateError = collectError;

    if (collectingCertificateInfo) {
      certificateInfo.setLength(0);
    }

    if (collectingCertificateError) {
      certificateError.setLength(0);
    }
  }

  private void handleAcceptCertificatePrompt() {
    // TODO: show 'certificateError' in dialog
    Url url = SvnUtil.parseUrl(serverUrl);
    AcceptResult result = myRuntime.getAuthenticationService().acceptCertificate(url, certificateInfo.toString());

    sendData(result.toString());
  }
}
