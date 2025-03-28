// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.terminal

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.utils.readWholeText
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.python.community.impl.venv.tests.pyVenvFixture
import com.intellij.python.community.junit5Tests.framework.conda.CondaEnv
import com.intellij.python.community.junit5Tests.framework.conda.PyEnvTestCaseWithConda
import com.intellij.python.community.junit5Tests.framework.conda.createCondaEnv
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.terminal.PyVirtualEnvTerminalCustomizer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.persist
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.runner.LocalShellIntegrationInjector
import org.jetbrains.plugins.terminal.util.ShellIntegration
import org.jetbrains.plugins.terminal.util.ShellType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

private const val WHERE_EXE = "where.exe"

/**
 * Run `powershell.exe` with a venv activation script and make sure there are no errors and python is correct
 */
@PyEnvTestCaseWithConda
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

  private var sdkToDelete: Sdk? = null

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking {
    sdkToDelete?.let { sdk ->
      edtWriteAction {
        ProjectJdkTable.getInstance().removeJdk(sdk)
      }
    }
  }

  private val powerShell =
    PathEnvironmentVariableUtil.findInPath("powershell.exe")?.toPath()
    ?: Path((System.getenv("SystemRoot") ?: "c:\\windows"), "system32", "WindowsPowerShell", "v1.0", "powershell.exe")

  @EnabledOnOs(value = [OS.WINDOWS])
  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun powershellActivationTest(useConda: Boolean, @CondaEnv condaEnv: PyCondaEnv, @TempDir path: Path): Unit = timeoutRunBlocking(10.minutes) {
    val (pythonBinary, venvDirName) =
      if (useConda) {
        val envDir = path.resolve("some path with spaces")
        val sdk = createCondaEnv(condaEnv, envDir).createSdkFromThisEnv(null, emptyList())
        sdkToDelete = sdk
        sdk.persist()
        ModuleRootModificationUtil.setModuleSdk(moduleFixture.get(), sdk)
        Pair(Path(sdk.homePath!!), envDir.toRealPath().pathString)
      }
      else {
        val venv = VirtualEnvReader.Instance.findPythonInPythonRoot(tempDirFixture.get())!!
        Pair(venv, tempDirFixture.get().name)
      }

    // binary might be like ~8.3, we need to expand it as venv might report both
    val pythonBinaryReal = try {
      pythonBinary.toRealPath()
    }
    catch (_: IOException) {
      pythonBinary
    }
    val shellOptions = getShellStartupOptions(pythonBinary.parent)
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
      assertThat("There must be a line with ($venvDirName)", output, hasItem(containsString("($venvDirName)")))

      process.exitCode.await()
    }
    finally {
      process.kill()
      process.terminate()
      process.exitCode.await()
    }
  }

  private fun getShellStartupOptions(workDir: Path): ShellStartupOptions {
    val sut = PyVirtualEnvTerminalCustomizer()
    val env = mutableMapOf<String, String>()
    val command = sut.customizeCommandAndEnvironment(
      projectFixture.get(),
      workDir.pathString,
      arrayOf(powerShell.pathString),
      env)

    val options = ShellStartupOptions.Builder()
      .envVariables(env)
      .shellCommand(command.toList())
      .shellIntegration(ShellIntegration(ShellType.POWERSHELL, null))
      .build()
    return LocalShellIntegrationInjector.injectShellIntegration(options, false, false)
  }
}