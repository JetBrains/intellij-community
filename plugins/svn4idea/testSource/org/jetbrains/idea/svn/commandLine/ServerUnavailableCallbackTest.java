// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import one.util.streamex.StreamEx;
import org.jetbrains.idea.svn.SvnTestCase;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.junit.Test;

import static org.junit.Assert.*;

public class ServerUnavailableCallbackTest extends SvnTestCase {
  private static final String[][] SVN_ERRORS = {
    {
      // SVN 1.6 "Connection refused", "No route to host", etc.
      "svn: OPTIONS of 'http://127.0.0.1/foo': could not connect to server (http://127.0.0.1)",
      "http://127.0.0.1/foo:\n" +
      "could not connect to server"
    },
    {
      // SVN 1.6 "Connection reset by peer"
      "svn: OPTIONS of 'http://127.0.0.1:3389/foo': Could not read status line: An existing connection was forcibly closed by the remote host.\n" +
      " (http://127.0.0.1:3389)",
      "http://127.0.0.1:3389/foo:\n" +
      "Could not read status line: An existing connection was forcibly closed by the remote host."
    },
    {
      // SVN 1.7 "Connection refused", "No route to host", etc.
      "svn: E175002: Unable to connect to a repository at URL 'https://a.b.c.d/svn/foobar/trunk'\n" +
      "svn: E175002: OPTIONS of 'https://a.b.c.d/svn/foobar/trunk': could not connect to server (https://a.b.c.d)",
      "Unable to connect to a repository at URL 'https://a.b.c.d/svn/foobar/trunk':\n" +
      "could not connect to server (https://a.b.c.d)"
    },
    {
      // SVN 1.7 "Connection reset by peer"
      "svn: E175002: Unable to connect to a repository at URL 'http://localhost:1443/foobar'\n" +
      "svn: E175002: OPTIONS of 'http://localhost:1443/foobar': Could not read status line: connection was closed by server (http://localhost:1443)",
      "Unable to connect to a repository at URL 'http://localhost:1443/foobar':\n" +
      "Could not read status line: connection was closed by server (http://localhost:1443)"
    },
    {
      // SVN 1.7 "DNS lookup failure"
      """
svn: E175002: Unable to connect to a repository at URL 'https://a.b.c.d/svn/foobar/trunk'
svn: E175002: OPTIONS of 'https://a.b.c.d/svn/foobar/trunk': Could not resolve hostname `a.b.c.d': No such host is known.
 (https://a.b.c.d)""",
      "Unable to connect to a repository at URL 'https://a.b.c.d/svn/foobar/trunk':\n" +
      "No such host is known."
    },
    {
      // SVN 1.8 Windows "Connection refused"
      "svn: E730061: Unable to connect to a repository at URL 'http://127.0.0.1/foo'\n" +
      "svn: E730061: Error running context: No connection could be made because the target machine actively refused it.",
      "Unable to connect to a repository at URL 'http://127.0.0.1/foo':\n" +
      "Error running context: No connection could be made because the target machine actively refused it."
    },
    {
      // SVN 1.9+ Windows "Connection refused"
      "svn: E170013: Unable to connect to a repository at URL 'http://127.0.0.1/foo'\n" +
      "svn: E730061: Error running context: No connection could be made because the target machine actively refused it.",
      "Unable to connect to a repository at URL 'http://127.0.0.1/foo':\n" +
      "Error running context: No connection could be made because the target machine actively refused it."
    },
    {
      // SVN 1.9+ Mac "Connection refused"
      "svn: E170013: Unable to connect to a repository at URL 'http://127.0.0.1:1231/foo'\n" +
      "svn: E000061: Error running context: Connection refused",
      "Unable to connect to a repository at URL 'http://127.0.0.1:1231/foo':\n" +
      "Error running context: Connection refused"
    },
    {
      // SVN 1.9+ Linux "Connection refused"
      "svn: E170013: Unable to connect to a repository at URL 'http://127.0.0.1:1231/foo'\n" +
      "svn: E000111: Error running context: Connection refused",
      "Unable to connect to a repository at URL 'http://127.0.0.1:1231/foo':\n" +
      "Error running context: Connection refused"
    },
    {
      // SVN 1.8 Windows "Connection reset by peer"
      "svn: E730054: Unable to connect to a repository at URL 'http://127.0.0.1:3389/foo'\n" +
      "svn: E730054: Error running context: An existing connection was forcibly closed by the remote host.",
      "Unable to connect to a repository at URL 'http://127.0.0.1:3389/foo':\n" +
      "Error running context: An existing connection was forcibly closed by the remote host."
    },
    {
      // SVN 1.9+ Windows "Connection reset by peer"
      "svn: E170013: Unable to connect to a repository at URL 'http://127.0.0.1:3389/foo'\n" +
      "svn: E730054: Error running context: An existing connection was forcibly closed by the remote host.",
      "Unable to connect to a repository at URL 'http://127.0.0.1:3389/foo':\n" +
      "Error running context: An existing connection was forcibly closed by the remote host."
    },
    {
      // SVN 1.9+ Linux "Connection reset by peer"
      "svn: E170013: Unable to connect to a repository at URL 'http://127.0.0.1:3389/foo'\n" +
      "svn: E000104: Error running context: Connection reset by peer",
      "Unable to connect to a repository at URL 'http://127.0.0.1:3389/foo':\n" +
      "Error running context: Connection reset by peer"
    },
    {
      // SVN 1.9+ Windows "Connection reset by peer -- unexpected"
      "svn: E170013: Unable to connect to a repository at URL 'http://127.0.0.1/foobar'\n" +
      "svn: E120108: Error running context: The server unexpectedly closed the connection.",
      "Unable to connect to a repository at URL 'http://127.0.0.1/foobar':\n" +
      "Error running context: The server unexpectedly closed the connection."
    },
    {
      // SVN 1.9+ Linux/Mac "Invalid protocol"
      "svn: E170013: Unable to connect to a repository at URL 'http://127.0.0.1:22/foo'\n" +
      "svn: E120105: Error running context: The server sent an improper HTTP response",
      "Unable to connect to a repository at URL 'http://127.0.0.1:22/foo':\n" +
      "Error running context: The server sent an improper HTTP response"
    },
    {
      // SVN 1.8 Windows "Unreachable host"
      "svn: E730065: Unable to connect to a repository at URL 'http://1.1.1.1/foo'\n" +
      "svn: E730065: Error running context: A socket operation was attempted to an unreachable host.",
      "Unable to connect to a repository at URL 'http://1.1.1.1/foo':\n" +
      "Error running context: A socket operation was attempted to an unreachable host."
    },
    {
      // SVN 1.9+ Windows "Unreachable host"
      "svn: E170013: Unable to connect to a repository at URL 'http://1.1.1.1/foo'\n" +
      "svn: E730065: Error running context: A socket operation was attempted to an unreachable host.",
      "Unable to connect to a repository at URL 'http://1.1.1.1/foo':\n" +
      "Error running context: A socket operation was attempted to an unreachable host."
    },
    {
      // SVN 1.9+ Linux "Unreachable network"
      "svn: E170013: Unable to connect to a repository at URL 'https://1.1.1.1/foo'\n" +
      "svn: E000113: Error running context: No route to host",
      "Unable to connect to a repository at URL 'https://1.1.1.1/foo':\n" +
      "Error running context: No route to host"
    },
    {
      // SVN 1.9+ Mac "Unreachable host"
      "svn: E170013: Unable to connect to a repository at URL 'https://1.1.1.1/foo'\n" +
      "svn: E000051: Error running context: Network is unreachable",
      "Unable to connect to a repository at URL 'https://1.1.1.1/foo':\n" +
      "Error running context: Network is unreachable"
    },
    {
      // SVN 1.8 Windows "DNS Lookup Failure"
      "svn: E731001: Unable to connect to a repository at URL 'http://asdgfasdfafa/foo'\n" +
      "svn: E731001: No such host is known.",
      "Unable to connect to a repository at URL 'http://asdgfasdfafa/foo':\n" +
      "No such host is known."
    },
    {
      // SVN 1.9+ Windows "DNS Lookup Failure"
      "svn: E170013: Unable to connect to a repository at URL 'http://asdgfasdfafa/foo'\n" +
      "svn: E731001: No such host is known.",
      "Unable to connect to a repository at URL 'http://asdgfasdfafa/foo':\n" +
      "No such host is known."
    },
    {
      // SVN 1.9+ Mac "DNS Lookup Failure"
      "svn: E170013: Unable to connect to a repository at URL 'http://sfdsfgdfsggdg/foo'\n" +
      "svn: E670008: nodename nor servname provided, or not known",
      "Unable to connect to a repository at URL 'http://sfdsfgdfsggdg/foo':\n" +
      "nodename nor servname provided, or not known"
    },
    {
      // SVN 1.9+ Linux "DNS Lookup Failure"
      "svn: E170013: Unable to connect to a repository at URL 'http://sfdsfgdfsggdg/foo'\n" +
      "svn: E670003: Temporary failure in name resolution",
      "Unable to connect to a repository at URL 'http://sfdsfgdfsggdg/foo':\n" +
      "Temporary failure in name resolution"
    },
  };

  @Test
  public void testCanHandle() {
    ServerUnavailableCallback callback = new ServerUnavailableCallback(new AuthenticationService(vcs, true), myRepositoryUrl);
    StreamEx<String[]> error2Exception =
      StreamEx.of(SVN_ERRORS).append(StreamEx.of(UsernamePasswordCallbackTest.AUTH_ERRORS).map(e -> new String[]{e, null}));
    for (String[] pair : error2Exception) {
      String svnError = pair[0];
      String exceptionText = pair[1];
      if (exceptionText == null) {
        assertFalse(svnError, callback.canHandle(svnError));
      } else {
        assertTrue(svnError, callback.canHandle(svnError));
        try {
          callback.getCredentials(svnError);
        }
        catch (SvnBindException e) {
          assertEquals(svnError, exceptionText, e.getMessage());
          continue;
        }
        fail(svnError);
      }
    }
  }
}
