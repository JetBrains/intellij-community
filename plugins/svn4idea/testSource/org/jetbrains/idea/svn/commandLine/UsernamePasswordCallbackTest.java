// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import org.jetbrains.idea.svn.SvnTestCase;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class UsernamePasswordCallbackTest extends SvnTestCase {
  static final String[] AUTH_ERRORS = {
    // SVN 1.6 "Auth failed"
    "svn: OPTIONS of 'https://localhost:1443/svn/foobar': authorization failed: Could not authenticate to server: rejected Basic challenge (https://localhost:1443)",
    // SVN 1.7 "Auth failed"
    "svn: E170001: Unable to connect to a repository at URL 'https://localhost:1443/svn/foobar'\n" +
    "svn: E170001: OPTIONS of 'https://localhost:1443/svn/foobar': authorization failed: Could not authenticate to server: rejected Basic challenge (https://localhost:1443)",
    // SVN 1.8 "Auth failed"
    """
svn: E215004: Authentication failed and interactive prompting is disabled; see the --force-interactive option
svn: E215004: Unable to connect to a repository at URL 'https://localhost:1443/svn/foobar'
svn: E215004: No more credentials or we tried too many times.
Authentication failed""",
    // SVN 1.9+ "Auth failed"
    """
svn: E170013: Unable to connect to a repository at URL 'https://localhost:1443/svn/foobar/trunk'
svn: E215004: No more credentials or we tried too many times.
Authentication failed"""
  };

  @Test
  public void testCanHandle() {
    UsernamePasswordCallback callback = new UsernamePasswordCallback(new AuthenticationService(vcs, true), myRepositoryUrl);
    for (String error : AUTH_ERRORS) {
      assertTrue(error, callback.canHandle(error));
    }
  }
}
