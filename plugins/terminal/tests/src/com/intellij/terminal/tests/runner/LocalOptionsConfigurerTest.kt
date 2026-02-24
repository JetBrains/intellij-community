// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.runner

import com.intellij.execution.Platform
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.idea.TestFor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.impl.wsl.WslConstants
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.util.EnvironmentUtil
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.system.OS
import kotlinx.coroutines.CompletableDeferred
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.runner.LocalOptionsConfigurer
import org.jetbrains.plugins.terminal.runner.LocalTerminalStartCommandBuilder.convertShellPathToCommand
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.TerminalEnvironment
import org.jetbrains.plugins.terminal.util.TerminalEnvironment.TERMINAL_EMULATOR
import org.jetbrains.plugins.terminal.util.TerminalEnvironment.TERM_SESSION_ID
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString
import kotlin.reflect.KMutableProperty0

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
    setDefaultStartingDirectory(tempDirectory.pathString)

    val actual = LocalOptionsConfigurer.configureStartupOptions(
      ShellStartupOptions.Builder()
        .shellCommand(mutableListOf("/bin/zsh"))
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
    setDefaultStartingDirectory(tempDirectory.pathString)

    val actual = LocalOptionsConfigurer.configureStartupOptions(
      ShellStartupOptions.Builder()
        .shellCommand(mutableListOf("/bin/bash"))
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

  fun testShellTerminalProcessTypeUsesSystemEnvironment() {
    setDefaultStartingDirectory(tempDirectory.pathString)

    val probeName = "TERMINAL_MINIMAL_ENV_PROBE_${System.nanoTime()}"
    assertThat(System.getenv()).doesNotContainKey(probeName)
    setEnvironmentMapForTest(EnvironmentUtil.getEnvironmentMap() + (probeName to "DEFAULT_ENV_VALUE"))

    val actual = LocalOptionsConfigurer.configureStartupOptions(
      ShellStartupOptions.Builder()
        .shellCommand(listOf("some-shell"))
        .processType(TerminalProcessType.SHELL)
        .build(),
      project
    )

    assertThat(actual.envVariables).doesNotContainKey(probeName)
  }

  fun testNonShellTerminalProcessTypeUsesEnvironmentMap() {
    setDefaultStartingDirectory(tempDirectory.pathString)

    val probeName = "TERMINAL_DEFAULT_ENV_PROBE_${System.nanoTime()}"
    val probeValue = "DEFAULT_ENV_VALUE"
    assertThat(System.getenv()).doesNotContainKey(probeName)
    setEnvironmentMapForTest(EnvironmentUtil.getEnvironmentMap() + (probeName to probeValue))

    val actual = LocalOptionsConfigurer.configureStartupOptions(
      ShellStartupOptions.Builder()
        .shellCommand(listOf("non-shell"))
        .processType(TerminalProcessType.NON_SHELL)
        .build(),
      project
    )

    assertEquals(probeValue, actual.envVariables[probeName])
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

  fun testAcceptWorkingDirectoryIfAbsoluteAndExits() {
    val defaultStartingDirectory = tempDirectory.pathString
    setDefaultStartingDirectory(defaultStartingDirectory)
    val defaultShellCommand = listOf("/bin/default-shell")
    setDefaultShellPath(defaultShellCommand.joinToString(separator = " "))
    val customShellCommand = listOf("/bin/custom-shell")
    val tmpDir2 = createTmpDirectory("tmp-dir-2")

    doTestShellPathAndWorkingDirectory(
      null,
      null,
      defaultShellCommand,
      defaultStartingDirectory
    )
    doTestShellPathAndWorkingDirectory(
      null,
      "",
      defaultShellCommand,
      defaultStartingDirectory
    )
    doTestShellPathAndWorkingDirectory(
      customShellCommand,
      ".",
      defaultShellCommand,
      defaultStartingDirectory
    )
    doTestShellPathAndWorkingDirectory(
      customShellCommand,
      "",
      defaultShellCommand,
      defaultStartingDirectory
    )
    doTestShellPathAndWorkingDirectory(
      customShellCommand,
      null,
      customShellCommand,
      defaultStartingDirectory
    )
    doTestShellPathAndWorkingDirectory(
      customShellCommand,
      tmpDir2.pathString,
      customShellCommand,
      tmpDir2.pathString
    )
    doTestShellPathAndWorkingDirectory(
      customShellCommand,
      if (OS.CURRENT.platform == Platform.WINDOWS) "/" else "C:/" /* flipped roots */,
      defaultShellCommand,
      defaultStartingDirectory
    )
    val root = FileSystems.getDefault().rootDirectories.first()
    doTestShellPathAndWorkingDirectory(
      customShellCommand,
      root.pathString,
      customShellCommand,
      root.pathString
    )
  }

  private fun doTestShellPathAndWorkingDirectory(
    requestedShellCommand: List<String>?,
    requestedWorkingDirectory: String?,
    expectedShellCommand: List<String>,
    expectedWorkingDirectory: String,
  ) {
    val actual = LocalOptionsConfigurer.configureStartupOptions(
      ShellStartupOptions.Builder()
        .shellCommand(requestedShellCommand)
        .workingDirectory(requestedWorkingDirectory)
        .build(),
      project
    )
    assertEquals(expectedShellCommand, actual.shellCommand)
    assertEquals(expectedWorkingDirectory, actual.workingDirectory)
  }

  @Suppress("SameParameterValue")
  private fun createTmpDirectory(prefix: String? = null): Path {
    val dir = createTempDirectory(prefix)
    Disposer.register(testRootDisposable) {
      try {
        tempDirectory.deleteRecursively()
      }
      catch (e: Throwable) {
        LOG.warn(e)
      }
    }
    return dir
  }

  private fun setDefaultStartingDirectory(startingDirectory: String) {
    setValueForTest(TerminalProjectOptionsProvider.getInstance(project)::startingDirectory, startingDirectory)
  }

  private fun setDefaultShellPath(shellPath: String) {
    setValueForTest(TerminalProjectOptionsProvider.getInstance(project)::shellPath, shellPath)
  }

  private fun <V> setValueForTest(prop: KMutableProperty0<V>, newValue: V) {
    val prevValue = prop.get()
    prop.set(newValue)
    Disposer.register(testRootDisposable) {
      prop.set(prevValue)
    }
  }

  private fun setEnvironmentMapForTest(environmentMap: Map<String, String>) {
    val previous = EnvironmentUtil.getEnvironmentMap()
    EnvironmentUtil.setEnvironmentLoader(CompletableDeferred(environmentMap))
    Disposer.register(testRootDisposable) {
      EnvironmentUtil.setEnvironmentLoader(CompletableDeferred(previous))
    }
  }
}
