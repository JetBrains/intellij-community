// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.runner

import com.intellij.idea.TestFor
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.runner.LocalShellIntegrationInjector.findAbsolutePath
import org.jetbrains.plugins.terminal.shell_integration.CommandBlockIntegration
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
    assertEquals(ShellIntegration(ShellType.ZSH, CommandBlockIntegration(false)), actual.shellIntegration)
    assertEquals(findAbsolutePath("shell-integrations/zsh/zsh-integration.zsh").parent.toString(), actual.envVariables[LocalShellIntegrationInjector.IJ_ZSH_DIR])
    assertEquals("1", actual.envVariables["PROCESS_LAUNCHED_BY_CW"])
    assertEquals("1", actual.envVariables["FIG_TERM"])
    assertEquals("1", actual.envVariables["PROCESS_LAUNCHED_BY_Q"])
    assertEquals("1", actual.envVariables["INTELLIJ_TERMINAL_COMMAND_BLOCKS"])
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
    assertEquals(ShellIntegration(ShellType.BASH, CommandBlockIntegration(false)), actual.shellIntegration)
    assertEquals("1", actual.envVariables["PROCESS_LAUNCHED_BY_CW"])
    assertEquals("1", actual.envVariables["FIG_TERM"])
    assertEquals("1", actual.envVariables["PROCESS_LAUNCHED_BY_Q"])
    assertEquals("1", actual.envVariables["INTELLIJ_TERMINAL_COMMAND_BLOCKS"])
    assertEquals("MY_CUSTOM_ENV_VALUE1", actual.envVariables["MY_CUSTOM_ENV1"])
  }

}
