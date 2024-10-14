// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.google.common.collect.Maps;
import com.intellij.idea.TestFor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@TestFor(classes = {LocalTerminalDirectRunner.class, LocalTerminalStartCommandBuilder.class})
public class TerminalShellCommandTest extends BasePlatformTestCase {
  public void testDontAddAnything() {
    if (SystemInfo.isUnix) {
      doTest(new String[]{"myshell", "someargs", "-i"}, "myshell someargs -i", Maps.newHashMap());
      doTest(new String[]{"myshell", "someargs", "--login"}, "myshell someargs --login", Maps.newHashMap());
    }
  }

  public void testAddInteractiveOrLogin() {
    if (SystemInfo.isLinux) {
      contains("bash someargs", true, Maps.newHashMap(), "-i", "someargs", "bash");
      contains("bash someargs", false, Maps.newHashMap(), "-i", "someargs", "bash");
    }
    else if (SystemInfo.isMac) {
      contains("bash someargs", true, Maps.newHashMap(), "someargs", "bash");
      contains("bash someargs", false, Maps.newHashMap(), "--login", "someargs", "bash");
    }
  }

  public void testAddRcConfig() {
    if (SystemInfo.isUnix) {
      hasRcConfig("bash -i", "bash/bash-integration.bash", Maps.newHashMap());
      hasRcConfig("bash --login", "bash/bash-integration.bash", Maps.newHashMap());
      Map<String, String> envs = Maps.newHashMap();
      hasRcConfig("bash --rcfile ~/.bashrc", "bash/bash-integration.bash", envs);
      assertTrue(envs.get("JEDITERM_USER_RCFILE").contains(".bashrc"));
    }
  }

  private List<String> getCommand(@NotNull String shellPath, @NotNull Map<String, String> envs, boolean shellIntegration) {
    List<String> shellCommand = LocalTerminalStartCommandBuilder.convertShellPathToCommand(shellPath);
    if (shellIntegration) {
      var runner = new LocalTerminalDirectRunner(getProject());
      ShellStartupOptions options = runner.injectShellIntegration(shellCommand, envs);
      envs.clear();
      envs.putAll(options.getEnvVariables());
      return Objects.requireNonNull(options.getShellCommand());
    }
    return shellCommand;
  }

  private void hasRcConfig(String path, String configName, Map<String, String> envs) {
    List<String> res = getCommand(path, envs, true);
    assertEquals("--rcfile", res.get(1));
    assertTrue(res.get(2).contains(configName));
  }

  private void doTest(String[] expected, String path, Map<String, String> envs) {
    assertEquals(Arrays.asList(expected), getCommand(path, envs, true));
  }

  private void contains(@NotNull String shellPath, boolean shellIntegration, Map<String, String> envs, String... item) {
    List<String> result = getCommand(shellPath, envs, shellIntegration);
    for (String i : item) {
      assertTrue(i + " isn't in " + StringUtil.join(result, " "), result.contains(i));
    }
  }
}
