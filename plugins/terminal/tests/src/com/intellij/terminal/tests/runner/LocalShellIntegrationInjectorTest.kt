// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.runner

import com.intellij.idea.TestFor
import com.intellij.util.system.OS
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.runner.LocalShellIntegrationInjector
import org.jetbrains.plugins.terminal.runner.LocalShellIntegrationInjector.findAbsolutePath
import org.jetbrains.plugins.terminal.util.ShellIntegration
import org.jetbrains.plugins.terminal.util.ShellType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestFor(classes = [LocalShellIntegrationInjector::class])
internal class LocalShellIntegrationInjectorTest {
  @Test
  fun testZsh() {
    val actual = LocalShellIntegrationInjector.injectShellIntegration(
      ShellStartupOptions.Builder()
        .shellCommand(mutableListOf("/bin/zsh"))
        .envVariables(buildMap {
          put("MY_CUSTOM_ENV1", "MY_CUSTOM_ENV_VALUE1")
        })
        .build(),
      true,
      false
    )

    assertEquals(listOf("/bin/zsh"), actual.shellCommand)
    val expectedCommandBlocks = expectedCommandBlocks()
    assertEquals(ShellIntegration(ShellType.ZSH, expectedCommandBlocks), actual.shellIntegration)
    assertEquals(findAbsolutePath("shell-integrations/zsh/zsh-integration.zsh").parent.toString(), actual.envVariables[LocalShellIntegrationInjector.IJ_ZSH_DIR])
    assertEquals("1".takeIf { expectedCommandBlocks }, actual.envVariables["PROCESS_LAUNCHED_BY_CW"])
    assertEquals("1".takeIf { expectedCommandBlocks }, actual.envVariables["FIG_TERM"])
    assertEquals("1".takeIf { expectedCommandBlocks }, actual.envVariables["PROCESS_LAUNCHED_BY_Q"])
    assertEquals("1".takeIf { expectedCommandBlocks }, actual.envVariables["INTELLIJ_TERMINAL_COMMAND_BLOCKS"])
    assertEquals(findAbsolutePath("shell-integrations/zsh/zdotdir/.zshenv").parent.toString(), actual.envVariables["ZDOTDIR"])
    assertEquals("MY_CUSTOM_ENV_VALUE1", actual.envVariables["MY_CUSTOM_ENV1"])
  }

  @Test
  fun testBash() {
    val actual = LocalShellIntegrationInjector.injectShellIntegration(
      ShellStartupOptions.Builder()
        .shellCommand(listOf("/bin/bash"))
        .envVariables(buildMap {
          put("MY_CUSTOM_ENV1", "MY_CUSTOM_ENV_VALUE1")
        })
        .build(),
      true,
      false
    )

    assertEquals(listOf("/bin/bash", "--rcfile", findAbsolutePath("shell-integrations/bash/bash-integration.bash").toString()), actual.shellCommand)
    val expectedCommandBlocks = expectedCommandBlocks()
    assertEquals(ShellIntegration(ShellType.BASH, expectedCommandBlocks), actual.shellIntegration)
    assertEquals("1".takeIf { expectedCommandBlocks }, actual.envVariables["PROCESS_LAUNCHED_BY_CW"])
    assertEquals("1".takeIf { expectedCommandBlocks }, actual.envVariables["FIG_TERM"])
    assertEquals("1".takeIf { expectedCommandBlocks }, actual.envVariables["PROCESS_LAUNCHED_BY_Q"])
    assertEquals("1".takeIf { expectedCommandBlocks }, actual.envVariables["INTELLIJ_TERMINAL_COMMAND_BLOCKS"])
    assertEquals("MY_CUSTOM_ENV_VALUE1", actual.envVariables["MY_CUSTOM_ENV1"])
  }

}

private fun expectedCommandBlocks(): Boolean {
  // similar to LocalShellIntegrationInjector.isSystemCompatibleWithCommandBlocks
  // but adapted to the buildserver where CI agents have recent Windows 10
  return OS.CURRENT != OS.Windows ||
         System.getProperty("os.name") !in listOf("Windows Server 2016", "Windows Server 2019")
}
