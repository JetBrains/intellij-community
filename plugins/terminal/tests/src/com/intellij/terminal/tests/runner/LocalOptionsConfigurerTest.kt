// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.runner

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.idea.TestFor
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.impl.wsl.WslConstants
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.util.containers.CollectionFactory
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.runner.LocalOptionsConfigurer
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder.convertShellPathToCommand
import org.jetbrains.plugins.terminal.util.TerminalEnvironment
import org.jetbrains.plugins.terminal.util.TerminalEnvironment.TERMINAL_EMULATOR
import org.jetbrains.plugins.terminal.util.TerminalEnvironment.TERM_SESSION_ID
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

@TestFor(classes = [LocalOptionsConfigurer::class])
internal class LocalOptionsConfigurerTest : BasePlatformTestCase() {
  private lateinit var tempDirectory: Path

  override fun setUp() {
    super.setUp()
    tempDirectory = createTempDirectory("dummy")
    // enable EelApi to not "fix" Unix shell path on Windows
    Registry.get("terminal.use.EelApi").setValue(true, testRootDisposable)
  }

  override fun tearDown() {
    try {
      tempDirectory.deleteRecursively()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testZsh() {
    TerminalProjectOptionsProvider.getInstance(project).startingDirectory = tempDirectory.pathString

    val actual = LocalOptionsConfigurer.configureStartupOptions(
      ShellStartupOptions.Builder()
        .shellCommand(mutableListOf<String>("/bin/zsh"))
        .envVariables(buildMap {
          put("MY_CUSTOM_ENV1", "MY_CUSTOM_ENV_VALUE1")
        })
        .build(),
      project
    )

    assertEquals(listOf("/bin/zsh"), actual.shellCommand)
    assertEquals(tempDirectory.pathString, actual.workingDirectory)
    assertEquals("JetBrains-JediTerm", actual.envVariables["TERMINAL_EMULATOR"])
    assertTrue(actual.envVariables["TERM_SESSION_ID"].let { it != null && it.isNotBlank() })
    assertEquals("MY_CUSTOM_ENV_VALUE1", actual.envVariables["MY_CUSTOM_ENV1"])
  }

  fun testBash() {
    TerminalProjectOptionsProvider.getInstance(project).startingDirectory = tempDirectory.pathString

    val actual = LocalOptionsConfigurer.configureStartupOptions(
      ShellStartupOptions.Builder()
        .shellCommand(mutableListOf<String>("/bin/bash"))
        .envVariables(buildMap {
          put("MY_CUSTOM_ENV1", "MY_CUSTOM_ENV_VALUE1")
        })
        .build(),
      project
    )

    assertEquals(listOf("/bin/bash"), actual.shellCommand)
    assertEquals(tempDirectory.pathString, actual.workingDirectory)
    assertEquals("JetBrains-JediTerm", actual.envVariables["TERMINAL_EMULATOR"])
    assertTrue(actual.envVariables["TERM_SESSION_ID"].let { it != null && it.isNotBlank() })
    assertEquals("MY_CUSTOM_ENV_VALUE1", actual.envVariables["MY_CUSTOM_ENV1"])
  }

  fun testBashDefaults() {
    TerminalProjectOptionsProvider.getInstance(project).startingDirectory = tempDirectory.pathString
    TerminalProjectOptionsProvider.getInstance(project).shellPath = "/bin/bash"

    val actual = LocalOptionsConfigurer.configureStartupOptions(
      ShellStartupOptions.Builder()
        .envVariables(buildMap {
          put("MY_CUSTOM_ENV1", "MY_CUSTOM_ENV_VALUE1")
        })
        .build(),
      project
    )

    assertEquals(convertShellPathToCommand("/bin/bash"), actual.shellCommand)
    assertEquals(tempDirectory.pathString, actual.workingDirectory)
    assertEquals("JetBrains-JediTerm", actual.envVariables["TERMINAL_EMULATOR"])
    assertTrue(actual.envVariables["TERM_SESSION_ID"].let { it != null && it.isNotBlank() })
    assertEquals("MY_CUSTOM_ENV_VALUE1", actual.envVariables["MY_CUSTOM_ENV1"])
  }

  fun testWslEnvSetup() {
    doTestWslEnvSetup(
      listOf(),
      null,
      "$TERMINAL_EMULATOR/u:$TERM_SESSION_ID/u"
    )
    doTestWslEnvSetup(
      null,
      null,
      "$TERMINAL_EMULATOR/u:$TERM_SESSION_ID/u"
    )
    doTestWslEnvSetup(
      listOf("MY_CUSTOM_ENV"),
      null,
      "MY_CUSTOM_ENV/u:$TERMINAL_EMULATOR/u:$TERM_SESSION_ID/u"
    )
    doTestWslEnvSetup(
      listOf("Terminal_Emulator"),
      "BASH_ENV/u",
      "BASH_ENV/u:Terminal_Emulator/u:$TERM_SESSION_ID/u"
    )
    doTestWslEnvSetup(
      listOf("Foo", "FOO", "BAR"),
      "PATH/p:BASH_ENV/u:",
      "PATH/p:BASH_ENV/u:Foo/u:BAR/u:$TERMINAL_EMULATOR/u:$TERM_SESSION_ID/u"
    )
    doTestWslEnvSetup(
      null,
      "BASH_ENV/u:",
      "BASH_ENV/u:$TERMINAL_EMULATOR/u:$TERM_SESSION_ID/u"
    )
  }

  private fun doTestWslEnvSetup(
    userDefinedEnvsToPass: List<String>?,
    initialWslEnvValue: String?,
    expectedResultWslEnvValue: String,
  ) {
    val userEnvData = userDefinedEnvsToPass?.let {
      EnvironmentVariablesData.create(userDefinedEnvsToPass.associateWith { "${it}_VALUE" }, true)
    }
    val resultEnvs = CollectionFactory.createCaseInsensitiveStringMap(
      initialWslEnvValue?.let {
        mapOf(WslConstants.WSLENV to initialWslEnvValue)
      }.orEmpty()
    )
    TerminalEnvironment.doSetWslEnv(userEnvData, resultEnvs)
    assertThat(resultEnvs).isEqualTo(mapOf(WslConstants.WSLENV to expectedResultWslEnvValue))
  }

}
