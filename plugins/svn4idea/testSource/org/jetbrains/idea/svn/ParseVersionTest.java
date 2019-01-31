// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Version;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.idea.svn.api.CmdVersionClient;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Konstantin Kolosovsky.
 */
public class ParseVersionTest {

  @Test
  public void testSvnOutput() throws Exception {
    assertEquals(new Version(1, 8, 0), CmdVersionClient.parseVersion("1.8.0"));
  }

  @Test
  public void testSlikSvnOutput() throws Exception {
    assertEquals(new Version(1, 7, 8), CmdVersionClient.parseVersion("1.7.8-SlikSvn-1.7.8-WIN32"));
  }

  @Test(expected = VcsException.class)
  public void testInvalidOutput() throws Exception {
    CmdVersionClient.parseVersion("10.2");
  }

  @Test
  public void testMultilineFirstLineCorrect() throws Exception {
    assertEquals(new Version(10, 2, 15), CmdVersionClient.parseVersion("10.2.15fdsjkf\n8.10.3"));
  }

  @Test(expected = VcsException.class)
  public void testMultilineSecondLineCorrect() throws Exception {
    assertEquals(new Version(8, 10, 3), CmdVersionClient.parseVersion("10.2.fdsjkf\n8.10.3"));
  }
}
