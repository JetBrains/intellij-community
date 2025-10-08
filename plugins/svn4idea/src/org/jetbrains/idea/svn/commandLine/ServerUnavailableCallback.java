// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.commandLine;

import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.AuthenticationService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles server connectivity issues
 */
public final class ServerUnavailableCallback extends AuthCallbackCase {
  public ServerUnavailableCallback(AuthenticationService service, Url url) {super(service, url);}

  // SVN 1.6 message
  // SVN 1.7 message
  // SVN 1.8-1.12 message: Unable to connect to a repository at URL
  private static final Pattern PATTERN = Pattern.compile(
    """
      svn: OPTIONS of '(.+)': ((?:Could not|could not).+)
      ? \\(.+\\)\
      |svn: E175002: (.+)
      svn: E175002: OPTIONS of '.+': (.+)(?:
      .+)?\
      |svn: E\\d{6}: (Unable to connect to a repository at URL .+)
      svn: E\\d+: (.+)"""
  );

  @Override
  public boolean canHandle(String error) {
    return PATTERN.matcher(error).matches() && !UsernamePasswordCallback.isAuthenticationProblem(error);
  }

  @Override
  public boolean getCredentials(String errText) throws SvnBindException {
    Matcher matcher = PATTERN.matcher(errText);
    boolean matches = matcher.matches();
    assert matches;
    int offset = 1;
    while (matcher.group(offset) == null) {
      offset += 2;
    }
    throw new SvnBindException(matcher.group(offset) + ":\n" + matcher.group(offset + 1));
  }
}
