/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

/**
 * @author traff
 */
public class TerminalShellCommandTest extends TestCase {
  public void testDontAddAnything() {
    doTest(new String[]{"myshell", "someargs", "-i"}, "myshell someargs -i");
    doTest(new String[]{"myshell", "someargs", "--login"}, "myshell someargs --login");
  }

  public void testAddInteractiveOrLogin() {
    if (SystemInfo.isLinux) {
      contains("bash someargs", "-i", "someargs", "bash");
    }
    else if (SystemInfo.isMac) {
      contains("bash someargs", "--login", "someargs", "bash");
    }
  }

  public void testAddRcConfig() {
    hasRcConfig("bash -i", "jediterm-sh.in");
    hasRcConfig("sh --login", "jediterm-sh.in");
  }

  private static void hasRcConfig(String path, String configName) {
    List<String> res = Arrays.asList(
      LocalTerminalDirectRunner.getCommand(path));
    assertEquals("--rcfile", res.get(1));
    assertTrue(res.get(2).contains(configName));
  }

  private static void doTest(String[] expected, String path) {
    assertEquals(Arrays.asList(expected), Arrays.asList(
      LocalTerminalDirectRunner.getCommand(path)));
  }

  private static void contains(String path, String... item) {
    List<String> result = Arrays.asList(
      LocalTerminalDirectRunner.getCommand(path));

    for (String i : item) {
      assertTrue(i + " isn't in " + StringUtil.join(result, " "), result.contains(i));
    }
  }
}
