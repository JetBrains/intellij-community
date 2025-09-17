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
        .shellCommand(listOf("/bin/zsh"))
        .envVariables(buildMap {
          put(MY_CUSTOM_ENV_NAME, MY_CUSTOM_ENV_VALUE)
        })
        .build(),
      false,
      true
    )
    val expectedCommandBlocks = expectedCommandBlocks()
    val expected = ShellStartupOptions.Builder()
      .shellCommand(listOf("/bin/zsh"))
      .shellIntegration(ShellIntegration(ShellType.ZSH, expectedCommandBlocks))
      .envVariables(getExpectedCommonEnv(expectedCommandBlocks) + buildMap {
        val zDotDir = findAbsolutePath("shell-integrations/zsh/zdotdir/.zshenv").parent
        put(LocalShellIntegrationInjector.ZDOTDIR, zDotDir.toString())
        put(LocalShellIntegrationInjector.IJ_ZSH_DIR, zDotDir.parent.toString())
        put(MY_CUSTOM_ENV_NAME, MY_CUSTOM_ENV_VALUE)
      })
      .build()
    assertEquals(expected, actual)
  }

  @Test
  fun testBash() {
    val actual = LocalShellIntegrationInjector.injectShellIntegration(
      ShellStartupOptions.Builder()
        .shellCommand(listOf("/bin/bash"))
        .envVariables(buildMap {
          put(MY_CUSTOM_ENV_NAME, MY_CUSTOM_ENV_VALUE)
        })
        .build(),
      false,
      true
    )

    val expectedCommandBlocks = expectedCommandBlocks()
    val expected = ShellStartupOptions.Builder()
      .shellCommand(listOf("/bin/bash", "--rcfile", findAbsolutePath("shell-integrations/bash/bash-integration.bash").toString()))
      .shellIntegration(ShellIntegration(ShellType.BASH, expectedCommandBlocks))
      .envVariables(getExpectedCommonEnv(expectedCommandBlocks) + mapOf(MY_CUSTOM_ENV_NAME to MY_CUSTOM_ENV_VALUE))
      .build()

    assertEquals(expected, actual)
  }

  private fun assertEquals(expected: ShellStartupOptions, actual: ShellStartupOptions) {
    assertEquals(expected.shellCommand, actual.shellCommand)
    assertEquals(expected.shellIntegration, actual.shellIntegration)
    assertEquals(expected.envVariables, actual.envVariables)
    assertEquals(expected.workingDirectory, actual.workingDirectory)
    assertEquals(expected.initialTermSize, actual.initialTermSize)
  }

  companion object {

    private const val MY_CUSTOM_ENV_NAME: String = "MY_CUSTOM_ENV1"
    private const val MY_CUSTOM_ENV_VALUE: String = "MY_CUSTOM_ENV_VALUE1"

    private fun expectedCommandBlocks(): Boolean {
      // similar to LocalShellIntegrationInjector.isSystemCompatibleWithCommandBlocks
      // but adapted to the buildserver where CI agents have recent Windows 10
      return OS.CURRENT != OS.Windows ||
             System.getProperty("os.name") !in listOf("Windows Server 2016", "Windows Server 2019")
    }

    private fun getExpectedCommonEnv(commandBlocks: Boolean): Map<String, String> {
      return listOf(
        "PROCESS_LAUNCHED_BY_CW",
        "FIG_TERM",
        "PROCESS_LAUNCHED_BY_Q",
        "INTELLIJ_TERMINAL_COMMAND_BLOCKS_REWORKED"
      ).takeIf { commandBlocks }.orEmpty().associateWith { "1" }
    }
  }
}
