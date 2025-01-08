// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.terminal

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.utils.readWholeText
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.junit5Tests.framework.env.pyVenvFixture
import com.intellij.python.terminal.PyVirtualEnvTerminalCustomizer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.sdk.VirtualEnvReader
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.runner.LocalShellIntegrationInjector
import org.jetbrains.plugins.terminal.util.ShellIntegration
import org.jetbrains.plugins.terminal.util.ShellType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.IOException
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

private const val WHERE_EXE = "where.exe"

/**
 * Run `powershell.exe` with a venv activation script and make sure there are no errors and python is correct
 */
@PyEnvTestCase
class PyVirtualEnvTerminalCustomizerTest {
  private val projectFixture = projectFixture()
  private val tempDirFixture = tempPathFixture(prefix = "some dir with spaces")
  private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

  @Suppress("unused") // we need venv
  private val venvFixture = pySdkFixture().pyVenvFixture(
    where = tempDirFixture,
    addToSdkTable = true,
    moduleFixture = moduleFixture
  )


  private val powerShell =
    PathEnvironmentVariableUtil.findInPath("powershell.exe")?.toPath()
    ?: Path((System.getenv("SystemRoot") ?: "c:\\windows"), "system32", "WindowsPowerShell", "v1.0", "powershell.exe")

  @EnabledOnOs(value = [OS.WINDOWS])
  @Test
  fun powershellActivationTest(): Unit = timeoutRunBlocking(10.minutes) {
    val pythonBinary = VirtualEnvReader.Instance.findPythonInPythonRoot(tempDirFixture.get())!!

    // binary might be like ~8.3, we need to expand it as venv might report both
    val pythonBinaryReal = try {
      pythonBinary.toRealPath()
    }
    catch (_: IOException) {
      pythonBinary
    }
    val shellOptions = getShellStartupOptions()
    val command = shellOptions.shellCommand!!
    val exe = command[0]
    val args = if (command.size == 1) emptyList() else command.subList(1, command.size)

    val execOptions = EelExecApi.ExecuteProcessOptions.Builder(exe)
      .args(args)
      .env(shellOptions.envVariables)
      .build()
    val process = localEel.exec.execute(execOptions).getOrThrow()
    try {
      val stderr = launch {
        val error = process.stderr.readWholeText().getOrThrow()
        Assertions.assertTrue(error.isEmpty(), "Unexpected text in stderr: $error")
      }
      val stdout = async {
        process.stdout.readWholeText().getOrThrow().split("\n").map { it.trim() }
      }

      val where = PathEnvironmentVariableUtil.findInPath(WHERE_EXE)?.toString() ?: WHERE_EXE
      process.stdin.sendWholeText("$where python\nexit\n").getOrThrow()
      stderr.join()
      val output = stdout.await()

      assertThat("We ran `$where`, so we there should be python path", output,
                 anyOf(hasItem(pythonBinary.pathString), hasItem(pythonBinaryReal.pathString)))
      val vendDirName = tempDirFixture.get().name
      assertThat("There must be a line with ($vendDirName)", output, hasItem(containsString("($vendDirName)")))

      process.exitCode.await()
    }
    finally {
      process.kill()
      process.terminate()
      process.exitCode.await()
    }
  }

  private fun getShellStartupOptions(): ShellStartupOptions {
    val sut = PyVirtualEnvTerminalCustomizer()
    val env = mutableMapOf<String, String>()
    val command = sut.customizeCommandAndEnvironment(
      projectFixture.get(),
      tempDirFixture.get().pathString,
      arrayOf(powerShell.pathString),
      env)

    val options = ShellStartupOptions.Builder()
      .envVariables(env)
      .shellCommand(command.toList())
      .shellIntegration(ShellIntegration(ShellType.POWERSHELL, null))
      .build()
    return LocalShellIntegrationInjector.injectShellIntegration(options, true, true)
  }
}