// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.terminal

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.execute
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.runner.LocalShellIntegrationInjector
import org.jetbrains.plugins.terminal.util.ShellIntegration
import org.jetbrains.plugins.terminal.util.ShellType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.io.TempDir
import org.junitpioneer.jupiter.cartesian.CartesianTest
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes


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


  private fun getShellPath(shellType: ShellType): Path = when (shellType) {
    ShellType.POWERSHELL -> PathEnvironmentVariableUtil.findInPath("powershell.exe")?.toPath()
                            ?: Path((System.getenv("SystemRoot")
                                     ?: "c:\\windows"), "system32", "WindowsPowerShell", "v1.0", "powershell.exe")
    ShellType.FISH, ShellType.BASH, ShellType.ZSH -> Path("/usr/bin/${shellType.name.lowercase()}")
  }


  @CartesianTest
  fun shellActivationTest(
    @CartesianTest.Values(booleans = [true, false]) useConda: Boolean,
    @CartesianTest.Enum shellType: ShellType,
    @CondaEnv condaEnv: PyCondaEnv,
    @TempDir venvPath: Path,
  ): Unit = timeoutRunBlocking(10.minutes) {
    when (shellType) {
      ShellType.POWERSHELL -> Assumptions.assumeTrue(SystemInfo.isWindows, "PowerShell is Windows only")
      ShellType.FISH -> Assumptions.abort("Fish terminal activation isn't supported")
      ShellType.ZSH, ShellType.BASH -> Assumptions.assumeFalse(SystemInfo.isWindows, "Unix shells do not work on Windows")
    }

    val shellPath = getShellPath(shellType)
    if (!withContext(Dispatchers.IO) { shellPath.exists() && shellPath.isExecutable() }) {
      when (shellType) {
        ShellType.ZSH -> Assumptions.assumeFalse(SystemInfo.isMac, "Zsh is mandatory on mac")
        ShellType.BASH -> error("$shellPath not found")
        ShellType.FISH -> error("Fish must be ignored")
        ShellType.POWERSHELL -> error("Powershell is mandatory on Windows")
      }
    }


    val (pythonBinary, venvDirName) =
      if (useConda) {
        val envDir = venvPath.resolve("some path with spaces")
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
    val shellOptions = getShellStartupOptions(pythonBinary.parent, shellType)
    val command = shellOptions.shellCommand!!
    val exe = command[0]
    val args = if (command.size == 1) emptyList() else command.subList(1, command.size)

    val execOptions = localEel.exec.execute(exe)
      .args(args)
      .env(shellOptions.envVariables + mapOf(Pair("TERM", "dumb")))
      // Unix shells do not activate with out tty
      .ptyOrStdErrSettings(if (SystemInfo.isWindows) null else EelExecApi.Pty(100, 100, true))
    val process = execOptions.getOrThrow()
    try {
      val stderr = async {
        process.stderr.readWholeText().getOrThrow()
      }
      val stdout = async {
        val separator = if (SystemInfo.isWindows) "\n" else "\r\n"
        process.stdout.readWholeText().getOrThrow().split(separator).map { it.trim() }
      }

      // tool -- where.exe Windows, "type(1)" **nix
      // "$TOOL python" returns $PREFIX [path-to-python] $POSTFIX
      val (locateTool, prefix, postfix) = if (SystemInfo.isWindows) {
        Triple(PathEnvironmentVariableUtil.findInPath("where.exe")?.toString() ?: "where.exe", "", "")
      }
      else {
        // zsh wraps text in ''
        val quot = if (shellType == ShellType.ZSH) "'" else ""
        Triple("type", "python is $quot", quot)
      }
      process.stdin.sendWholeText("$locateTool python\nexit\n").getOrThrow()
      val error = stderr.await()

      Assertions.assertTrue(error.isEmpty(), "Unexpected text in stderr: $error")
      val output = stdout.await()
      fileLogger().info("Output was $output")

      assertThat("We ran `$locateTool`, so we there should be python path", output,
                 anyOf(hasItem(prefix + pythonBinary.pathString + postfix), hasItem(prefix + pythonBinaryReal.pathString + postfix)))
      if (SystemInfo.isWindows) {
        assertThat("There must be a line with ($venvDirName)", output, hasItem(containsString("($venvDirName)")))
      }

      process.exitCode.await()
    }
    finally {
      process.kill()
      process.terminate()
      process.exitCode.await()
    }
  }

  private fun getShellStartupOptions(workDir: Path, shellType: ShellType): ShellStartupOptions {
    val sut = PyVirtualEnvTerminalCustomizer()
    val env = mutableMapOf<String, String>()
    val command = sut.customizeCommandAndEnvironment(
      projectFixture.get(),
      workDir.pathString,
      arrayOf(getShellPath(shellType).pathString),
      env)

    val options = ShellStartupOptions.Builder()
      .envVariables(env)
      .shellCommand(command.toList())
      .shellIntegration(ShellIntegration(shellType, null))
      .build()
    return LocalShellIntegrationInjector.injectShellIntegration(options, false, false)
  }
}