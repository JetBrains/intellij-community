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

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;

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

  protected SVNAuthentication myAuthentication;

  UsernamePasswordCallback(@NotNull AuthenticationService authenticationService, SVNURL url) {
    super(authenticationService, url);
  }

  @Override
  public boolean canHandle(String error) {
    boolean useSvnKit = Registry.is("svn.use.svnkit.for.https.server.certificate.check");

    return
      // http/https protocol invalid credentials
      error.contains(AUTHENTICATION_FAILED_MESSAGE) ||
      // svn protocol invalid credentials - messages could be "Can't get password", "Can't get username or password"
      error.contains(INVALID_CREDENTIALS_FOR_SVN_PROTOCOL) && error.contains(PASSWORD_STRING) ||
      // http/https protocol, svn 1.7, non-interactive
      // we additionally check that error is not related to certificate verification - as CertificateCallbackCase could only handle
      // untrusted certificates, but not invalid when useSvnKit = false
      (error.contains(UNABLE_TO_CONNECT_MESSAGE) && (useSvnKit || !CertificateCallbackCase.isCertificateVerificationFailed(error))) ||
      // http, svn 1.6, non-interactive
      StringUtil.containsIgnoreCase(error, COULD_NOT_AUTHENTICATE_TO_SERVER_MESSAGE);
  }

  @Override
  boolean getCredentials(String errText) {
    myAuthentication = myAuthenticationService.requestCredentials(myUrl != null ? myUrl : parseUrlFromError(errText), getType());

    return myAuthentication != null;
  }

  public String getType() {
    return ISVNAuthenticationManager.PASSWORD;
  }

  @Override
  public void updateParameters(@NotNull Command command) {
    if (myAuthentication instanceof SVNPasswordAuthentication) {
      SVNPasswordAuthentication auth = (SVNPasswordAuthentication)myAuthentication;

      command.put("--username");
      command.put(auth.getUserName());
      command.put("--password");
      command.put(auth.getPassword());
      if (!auth.isStorageAllowed()) {
        command.put("--no-auth-cache");
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
}
