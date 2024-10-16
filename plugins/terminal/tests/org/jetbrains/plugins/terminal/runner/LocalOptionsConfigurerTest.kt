// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.runner

import com.intellij.idea.TestFor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.io.deleteRecursively
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder.convertShellPathToCommand
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

@TestFor(classes = [LocalOptionsConfigurer::class])
internal class LocalOptionsConfigurerTest : BasePlatformTestCase() {
  private lateinit var myTarget: LocalOptionsConfigurer

  private lateinit var tempDirectory: Path

  override fun setUp() {
    super.setUp()
    tempDirectory = createTempDirectory("dummy")
    myTarget = LocalOptionsConfigurer(myFixture.project)
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

    val actual = myTarget.configureStartupOptions(
      ShellStartupOptions.Builder()
        .shellCommand(mutableListOf<String>("/bin/zsh"))
        .envVariables(buildMap {
          put("MY_CUSTOM_ENV1", "MY_CUSTOM_ENV_VALUE1")
        })
        .build()
    )

    assertEquals(listOf("/bin/zsh"), actual.shellCommand)
    assertEquals(tempDirectory.pathString, actual.workingDirectory)
    assertEquals("JetBrains-JediTerm", actual.envVariables["TERMINAL_EMULATOR"])
    assertTrue(actual.envVariables["TERM_SESSION_ID"].let { it != null && it.isNotBlank() })
    assertEquals("MY_CUSTOM_ENV_VALUE1", actual.envVariables["MY_CUSTOM_ENV1"])
  }

  fun testBash() {
    TerminalProjectOptionsProvider.getInstance(project).startingDirectory = tempDirectory.pathString

    val actual = myTarget.configureStartupOptions(
      ShellStartupOptions.Builder()
        .shellCommand(mutableListOf<String>("/bin/bash"))
        .envVariables(buildMap {
          put("MY_CUSTOM_ENV1", "MY_CUSTOM_ENV_VALUE1")
        })
        .build()
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

    val actual = myTarget.configureStartupOptions(
      ShellStartupOptions.Builder()
        .envVariables(buildMap {
          put("MY_CUSTOM_ENV1", "MY_CUSTOM_ENV_VALUE1")
        })
        .build()
    )

    assertEquals(convertShellPathToCommand("/bin/bash"), actual.shellCommand)
    assertEquals(tempDirectory.pathString, actual.workingDirectory)
    assertEquals("JetBrains-JediTerm", actual.envVariables["TERMINAL_EMULATOR"])
    assertTrue(actual.envVariables["TERM_SESSION_ID"].let { it != null && it.isNotBlank() })
    assertEquals("MY_CUSTOM_ENV_VALUE1", actual.envVariables["MY_CUSTOM_ENV1"])
  }

}
