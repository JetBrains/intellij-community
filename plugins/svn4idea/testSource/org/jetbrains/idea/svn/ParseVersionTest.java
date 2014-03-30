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
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Version;
import com.intellij.openapi.vcs.VcsException;
import junit.framework.Assert;
import org.jetbrains.idea.svn.api.CmdVersionClient;
import org.junit.Test;

/**
 * @author Konstantin Kolosovsky.
 */
public class ParseVersionTest {

  @Test
  public void testSvnOutput() throws Exception {
    Assert.assertEquals(new Version(1, 8, 0), CmdVersionClient.parseVersion("1.8.0"));
  }

  @Test
  public void testSlikSvnOutput() throws Exception {
    Assert.assertEquals(new Version(1, 7, 8), CmdVersionClient.parseVersion("1.7.8-SlikSvn-1.7.8-WIN32"));
  }

  @Test(expected = VcsException.class)
  public void testInvalidOutput() throws Exception {
    CmdVersionClient.parseVersion("10.2");
  }

  @Test
  public void testMultilineFirstLineCorrect() throws Exception {
    Assert.assertEquals(new Version(10, 2, 15), CmdVersionClient.parseVersion("10.2.15fdsjkf\n8.10.3"));
  }

  @Test(expected = VcsException.class)
  public void testMultilineSecondLineCorrect() throws Exception {
    Assert.assertEquals(new Version(8, 10, 3), CmdVersionClient.parseVersion("10.2.fdsjkf\n8.10.3"));
  }
}
