// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.AuthenticationData;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.jetbrains.idea.svn.auth.PasswordAuthenticationData;
import org.jetbrains.idea.svn.auth.SvnAuthenticationManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Special callback for svn 1.8 credentials request as --non-interactive does not return authentication realm (just url) - so we
 * could not create temp cache
 *
 * @author Konstantin Kolosovsky.
 */
public class UsernamePasswordCallback extends AuthCallbackCase {

  private static final String COULD_NOT_AUTHENTICATE_TO_SERVER_MESSAGE = "could not authenticate to server";
  private static final String UNABLE_TO_CONNECT_MESSAGE = "Unable to connect to a repository";
  private static final String AUTHENTICATION_FAILED_MESSAGE = "Authentication failed";
  private static final String INVALID_CREDENTIALS_FOR_SVN_PROTOCOL = "svn: E170001: Can't get";
  private static final String PASSWORD_STRING = "password";
  private static final Pattern UNABLE_TO_CONNECT_TO_URL_PATTERN = Pattern.compile("Unable to connect to a repository at URL '(.*)'");

  protected AuthenticationData myAuthentication;

  UsernamePasswordCallback(@NotNull AuthenticationService authenticationService, Url url) {
    super(authenticationService, url);
  }

  @Override
  public boolean canHandle(String error) {
    return
      // http/https protocol invalid credentials
      error.contains(AUTHENTICATION_FAILED_MESSAGE) ||
      // svn protocol invalid credentials - messages could be "Can't get password", "Can't get username or password"
      error.contains(INVALID_CREDENTIALS_FOR_SVN_PROTOCOL) && error.contains(PASSWORD_STRING) ||
      // http/https protocol, svn 1.7, non-interactive
      // we additionally check that error is not related to certificate verification - as CertificateCallbackCase could only handle
      // untrusted but not invalid certificates
      (error.contains(UNABLE_TO_CONNECT_MESSAGE) && !CertificateCallbackCase.isCertificateVerificationFailed(error)) ||
      // http, svn 1.6, non-interactive
      StringUtil.containsIgnoreCase(error, COULD_NOT_AUTHENTICATE_TO_SERVER_MESSAGE);
  }

  @Override
  boolean getCredentials(String errText) {
    myAuthentication = myAuthenticationService.requestCredentials(myUrl != null ? myUrl : parseUrlFromError(errText), getType());

    return myAuthentication != null;
  }

  public String getType() {
    return SvnAuthenticationManager.PASSWORD;
  }

  @Override
  public void updateParameters(@NotNull Command command) {
    if (myAuthentication instanceof PasswordAuthenticationData) {
      PasswordAuthenticationData auth = (PasswordAuthenticationData)myAuthentication;

      command.put("--username");
      command.put(auth.getUserName());
      command.put("--password");
      command.put(auth.getPassword());
      if (!auth.isStorageAllowed()) {
        command.put("--no-auth-cache");
      }
    }
  }

  private Url parseUrlFromError(String errorText) {
    Matcher matcher = UNABLE_TO_CONNECT_TO_URL_PATTERN.matcher(errorText);
    String urlValue = null;

    if (matcher.find()) {
      urlValue = matcher.group(1);
    }

    return urlValue != null ? parseUrl(urlValue) : null;
  }
}
