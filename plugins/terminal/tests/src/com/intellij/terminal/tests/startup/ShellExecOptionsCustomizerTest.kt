package com.intellij.terminal.tests.startup

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.fs.createTemporaryDirectory
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.pathSeparator
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.setValueInTest
import com.intellij.terminal.tests.reworked.util.withShellIntegration
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.io.delete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.createEnvVariablesMap
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecCommandImpl
import org.jetbrains.plugins.terminal.startup.ShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizerDisabler
import org.jetbrains.plugins.terminal.util.ShellType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Run with `intellij.idea.ultimate.tests.main` classpath to test in WSL/Docker, like on CI.
 *
 * If ijent binaries are missing locally (typically on macOS), refer to IJPL-197291 for resolution.
 */
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
@ParameterizedClass
class ShellExecOptionsCustomizerTest(private val eelHolder: EelHolder) {

  private val project: Project by projectFixture()
  private val tempDir: Path by tempPathFixture()
  @TestDisposable
  private lateinit var testDisposable: Disposable

  private val eelApi: EelApi
    get() = eelHolder.eel

  @Test
  fun `local dir appended to PATH is translated to remote`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    register(customizer {
      it.appendEntryToPATH(dir.nioDir)
    })
    val result = configureStartupOptions(dir, false) {
      it[PATH] = ""
    }
    result.assertPathLikeEnv(PATH, dir.remoteDir)
  }

  @TestFactory
  fun `local dir prepended to PATH is translated to remote`() = withShellIntegration { shellIntegration, testDisposable ->
    val dir = tempDir.asDirectory()
    register(customizer {
      it.prependEntryToPATH(dir.nioDir)
    }, parentDisposable = testDisposable)
    val result = configureStartupOptions(dir, shellIntegration, testDisposable) {
      it[PATH] = "foo"
    }
    if (shellIntegration) {
      Assertions.assertThat(result.getEnvVarValue(PATH)).isEqualTo("foo")
      Assertions.assertThat(result.getEnvVarValue(IJ_FORCE_PREPEND_PATH)).isEqualTo(dir.remoteDir + eelApi.descriptor.osFamily.pathSeparator)
    }
    else {
      result.assertPathLikeEnv(PATH, dir.remoteDir, "foo")
    }
  }

  @Test
  fun `existing PATH with semicolon is kept intact`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    val initialPath = "/foo;bar:/usr/bin"
    register(customizer {
      it.prependEntryToPATH(dir.nioDir)
    })
    val result = configureStartupOptions(dir, false) {
      it[PATH] = initialPath
    }
    result.assertPathLikeEnv(PATH, dir.remoteDir, initialPath)
  }

  @TestFactory
  fun `local dirs appended and prepended are translated to remote`() = withShellIntegration { shellIntegration, testDisposable ->
    val dir1 = tempDir.asDirectory()
    val dir2 = createTmpDir("dir2").asDirectory()
    register(customizer {
      it.prependEntryToPATH(dir1.nioDir)
      it.appendEntryToPATH(dir2.nioDir)
    }, parentDisposable = testDisposable)
    val result = configureStartupOptions(dir1, shellIntegration, testDisposable) {
      it[PATH] = "/foo:/bar"
    }
    if (shellIntegration) {
      result.assertPathLikeEnv(PATH, "/foo:/bar", dir2.remoteDir)
      Assertions.assertThat(result.getEnvVarValue(IJ_FORCE_PREPEND_PATH)).isEqualTo(dir1.remoteDir + eelApi.descriptor.osFamily.pathSeparator)
    }
    else {
      result.assertPathLikeEnv(PATH, dir1.remoteDir, "/foo:/bar", dir2.remoteDir)
    }
  }

  @Test
  fun `translated and non-translated paths by several customizers`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir1 = tempDir.asDirectory()
    val dir2 = createTmpDir("dir2").asDirectory()
    register(customizer {
      it.prependEntryToPATH(dir1.nioDir)
      it.appendEntryToPATH(dir2.nioDir)
    }, customizer {
      // these paths are not translated:
      it.setEnvironmentVariable(PATH, joinEntries(it.envs[PATH], "foo", LocalEelDescriptor))
      it.setEnvironmentVariable(PATH, joinEntries("bar", it.envs[PATH], LocalEelDescriptor))
    })
    val result = configureStartupOptions(dir1, false) {
      it[PATH] = "/path/to/baz"
    }
    Assertions.assertThat(result.shellExecOptions.envs[PATH]).isEqualTo(
      "bar" + LocalEelDescriptor.osFamily.pathSeparator + dir1.remoteDir + eelApi.descriptor.osFamily.pathSeparator +
      "/path/to/baz" +
      eelApi.descriptor.osFamily.pathSeparator + dir2.remoteDir + LocalEelDescriptor.osFamily.pathSeparator + "foo"
    )
  }

  @TestFactory
  fun `local path prepended to _INTELLIJ_FORCE_PREPEND_${env name} is translated`() = withShellIntegration { shellIntegration, testDisposable ->
    val dir = tempDir.asDirectory()
    register(customizer {
      it.prependEntryToPathLikeEnv(IJ_FORCE_PREPEND_PATH, dir.nioDir)
    }, parentDisposable = testDisposable)
    val result = configureStartupOptions(dir, shellIntegration, testDisposable) {
      it[IJ_FORCE_PREPEND_PATH] = "foo"
    }
    Assertions.assertThat(result.getEnvVarValue(IJ_FORCE_PREPEND_PATH)).isEqualTo(
      joinEntries(dir.remoteDir, "foo", eelApi.descriptor) + eelApi.descriptor.osFamily.pathSeparator
    )
  }

  @Test
  fun `local path appended to _INTELLIJ_FORCE_PREPEND_${env name} is translated`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    register(customizer {
      it.appendEntryToPathLikeEnv(IJ_FORCE_PREPEND_PATH, dir.nioDir)
    })
    val result = configureStartupOptions(dir, false) {
      it[IJ_FORCE_PREPEND_PATH] = "bar"
    }
    Assertions.assertThat(result.getEnvVarValue(IJ_FORCE_PREPEND_PATH)).isEqualTo(
      joinEntries("bar", dir.remoteDir, eelApi.descriptor) + eelApi.descriptor.osFamily.pathSeparator
    )
  }

  @TestFactory
  fun `prepend to custom Path-like env`() = withShellIntegration { shellIntegration, testDisposable ->
    val dir = tempDir.asDirectory()
    val customEnvName = "MY_ENV"
    register(customizer {
      it.prependEntryToPathLikeEnv(customEnvName, dir.nioDir)
    }, parentDisposable = testDisposable)
    val result = configureStartupOptions(dir, shellIntegration, testDisposable) {
      it[customEnvName] = "foo"
    }
    if (shellIntegration) {
      Assertions.assertThat(result.getEnvVarValue(customEnvName)).isEqualTo("foo")
      Assertions.assertThat(result.getEnvVarValue(IJ_FORCE_PREPEND_PREFIX + customEnvName)).isEqualTo(dir.remoteDir + eelApi.descriptor.osFamily.pathSeparator)
    }
    else {
      result.assertPathLikeEnv(customEnvName, dir.remoteDir, "foo")
    }
  }

  @Test
  fun `env deletion`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    val customEnvName = "MY_ENV"
    register(customizer {
      it.setEnvironmentVariable(customEnvName, null)
    })
    val result = configureStartupOptions(dir, false) {
      it[customEnvName] = "foo"
    }
    Assertions.assertThat(result.getEnvVarValue(customEnvName)).isNull()
  }

  @TestFactory
  fun `setEnvironmentVariable also sets (removes) twin under shell integration`() = withShellIntegration { shellIntegration, testDisposable ->
    val dir = tempDir.asDirectory()
    val customEnvName = "MY_ENV"
    register(customizer {
      it.setEnvironmentVariable(customEnvName, "foo")
      Assertions.assertThat(it.envs[customEnvName]).isEqualTo("foo")
      Assertions.assertThat(it.envs[IJ_FORCE_SET_PREFIX + customEnvName]).isEqualTo("foo".takeIf { shellIntegration })
      it.setEnvironmentVariable(customEnvName, null)
    }, parentDisposable = testDisposable)
    val result = configureStartupOptions(dir, shellIntegration, testDisposable)
    Assertions.assertThat(result.getEnvVarValue(customEnvName)).isNull()
    Assertions.assertThat(result.getEnvVarValue(IJ_FORCE_SET_PREFIX + customEnvName)).isNull()
  }

  @TestFactory
  fun `setEnvironmentVariableToPath also sets (removes) twin under shell integration`() = withShellIntegration { shellIntegration, testDisposable ->
    val dir = tempDir.asDirectory()
    val customEnvName = "MY_ENV"
    register(customizer {
      it.setEnvironmentVariableToPath(customEnvName, dir.nioDir)
      Assertions.assertThat(it.envs[customEnvName]).isEqualTo(dir.remoteDir)
      Assertions.assertThat(it.envs[IJ_FORCE_SET_PREFIX + customEnvName]).isEqualTo(if (shellIntegration) dir.remoteDir else null)
      it.setEnvironmentVariableToPath(customEnvName, null)
    }, parentDisposable = testDisposable)
    val result = configureStartupOptions(dir, shellIntegration, testDisposable)
    Assertions.assertThat(result.getEnvVarValue(customEnvName)).isNull()
    Assertions.assertThat(result.getEnvVarValue(IJ_FORCE_SET_PREFIX + customEnvName)).isNull()
  }

  @Test
  fun `translate JEDITERM_SOURCE to remote`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    val fileToSource = dir.nioDir.resolve("test.sh")
    register(customizer {
      it.setEnvironmentVariableToPath(JEDITERM_SOURCE, fileToSource)
    })
    val result = configureStartupOptions(dir, false) {
      it.remove(JEDITERM_SOURCE)
    }
    result.assertSinglePathEnv(JEDITERM_SOURCE, fileToSource)
  }

  @Test
  fun `modifications to execOptions are reflected immediately by read-only accessors`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    val customEnvName1 = "MY_CUSTOM_ENV_1"
    val customEnvName2 = "MY_CUSTOM_ENV_2"
    val newExecCommand = ShellExecCommandImpl(listOf("my-shell", "--myCustomArg"))
    register(customizer {
      it.setEnvironmentVariable(customEnvName1, "foo")
      Assertions.assertThat(it.envs[customEnvName1]).isEqualTo("foo")

      it.setEnvironmentVariableToPath(customEnvName2, dir.nioDir)
      Assertions.assertThat(it.envs[customEnvName2]).isEqualTo(dir.remoteDir)

      it.appendEntryToPATH(Path.of("bar"))
      it.appendEntryToPATH(dir.nioDir)
      Assertions.assertThat(it.envs[PATH]).endsWith(joinEntries("bar", dir.remoteDir, eelApi.descriptor))

      it.setExecCommand(newExecCommand)
      Assertions.assertThat(it.execCommand).isEqualTo(newExecCommand)
    })
    val result = configureStartupOptions(tempDir.asDirectory(), false) {
      it[PATH] = "/path/to/baz"
    }
    Assertions.assertThat(result.shellExecOptions.envs[customEnvName1]).isEqualTo("foo")
    Assertions.assertThat(result.shellExecOptions.envs[customEnvName2]).isEqualTo(dir.remoteDir)
    result.assertPathLikeEnv(PATH, "/path/to/baz", "bar", dir.remoteDir)
    Assertions.assertThat(result.shellExecOptions.execCommand).isEqualTo(newExecCommand)
  }

  @TestFactory
  fun `source custom shell script via shell integration`() = withShellIntegration { shellIntegration, testDisposable ->
    val workingDir = tempDir.asDirectory()
    val customShellScript = workingDir.nioDir.resolve("my-custom-shell-script")
    var shellIntegrationInjected = false
    register(customizer {
      shellIntegrationInjected = it.shellIntegrationConfigurer != null
      it.shellIntegrationConfigurer?.sourceShellScriptAtShellStartup(customShellScript, listOf("my-arg1", "my-arg2"))
    }, parentDisposable = testDisposable)
    val result = configureStartupOptions(workingDir, shellIntegration, testDisposable) {
      it.remove(JEDITERM_SOURCE)
      it.remove(JEDITERM_SOURCE_ARGS)
    }
    Assertions.assertThat(shellIntegrationInjected).isEqualTo(shellIntegration)
    if (shellIntegrationInjected) {
      result.assertSinglePathEnv(JEDITERM_SOURCE, customShellScript)
      Assertions.assertThat(result.getEnvVarValue(JEDITERM_SOURCE_ARGS)).isEqualTo("my-arg1 my-arg2")
    }
    else {
      Assertions.assertThat(result.getEnvVarValue(JEDITERM_SOURCE)).isNull()
      Assertions.assertThat(result.getEnvVarValue(JEDITERM_SOURCE_ARGS)).isNull()
    }
  }

  @Test
  fun `ShellExecOptionsCustomizerDisabler disables PATH edits`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    register(customizer {
      it.appendEntryToPATH(dir.nioDir)
    })

    val disabler = object : ShellExecOptionsCustomizerDisabler {
      override fun shouldDisable(project: Project): Boolean =
        project == this@ShellExecOptionsCustomizerTest.project
    }
    ExtensionTestUtil.maskExtensions(
      ShellExecOptionsCustomizerDisabler.EP_NAME,
      listOf(disabler),
      testDisposable,
    )

    val result = configureStartupOptions(dir, false) {
      it[PATH] = ""
    }
    result.assertPathLikeEnv(PATH, *emptyArray())
  }

  private fun customizer(handler: (execOptions: MutableShellExecOptions) -> Unit): ShellExecOptionsCustomizer {
    return object : ShellExecOptionsCustomizer {
      override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
        handler(shellExecOptions)
      }
    }
  }

  private fun register(vararg customizers: ShellExecOptionsCustomizer, parentDisposable: Disposable = testDisposable) {
    ExtensionTestUtil.maskExtensions(
      ShellExecOptionsCustomizer.EP_NAME,
      customizers.toList(),
      parentDisposable
    )
  }

  private fun Path.asDirectory(descriptor: EelDescriptor = eelApi.descriptor): Directory {
    val nioDir = this
    Assertions.assertThat(nioDir.getEelDescriptor()).isEqualTo(descriptor)
    Assertions.assertThat(nioDir).isDirectory()
    val eelDir = nioDir.asEelPath(descriptor)
    Assertions.assertThat(eelDir.descriptor).isEqualTo(descriptor)
    return Directory(nioDir, eelDir, descriptor)
  }

  private suspend fun createTmpDir(prefix: String): Path {
    val dir = withContext(Dispatchers.IO) {
      eelApi.fs.createTemporaryDirectory().prefix(prefix).eelIt().getOrThrow().asNioPath()
    }
    Disposer.register(testDisposable) {
      dir.delete(recursively = true)
    }
    return dir
  }

  private fun configureStartupOptions(
    workingDir: Directory,
    allowShellIntegration: Boolean,
    testDisposable: Disposable = this.testDisposable,
    initialEnvironmentCallback: ((MutableMap<String, String>) -> Unit)? = null,
  ): CustomizationResult {
    TerminalOptionsProvider.instance::shellIntegration.setValueInTest(allowShellIntegration, testDisposable)
    val shellPathWithShellIntegration = if (eelApi.descriptor.osFamily.isPosix) "zsh" else "powershell.exe"
    TerminalProjectOptionsProvider.getInstance(project)::shellPath.setValueInTest(shellPathWithShellIntegration, testDisposable)
    fileLogger().info("Running on ${workingDir.descriptor} in ${workingDir.nioDir} (${workingDir.eelDir})")

    val envVariables = createEnvVariablesMap(eelApi.descriptor.osFamily)
    initialEnvironmentCallback?.invoke(envVariables)
    val startupOptions = ShellStartupOptions.Builder()
      .workingDirectory(workingDir.nioDir.toString())
      .envVariables(envVariables)
      .build()

    val terminalRunner = LocalTerminalDirectRunner(project)
    val configuredOptions = terminalRunner.configureStartupOptions(startupOptions)
    Assertions.assertThat(configuredOptions.eelDescriptorNotNull).isEqualTo(workingDir.descriptor)
    Assertions.assertThat(configuredOptions.shellIntegration?.shellType).isEqualTo(
      when (eelApi.descriptor.osFamily) {
        EelOsFamily.Posix -> ShellType.ZSH
        EelOsFamily.Windows -> ShellType.POWERSHELL
      }.takeIf { allowShellIntegration }
    )
    return CustomizationResult(configuredOptions.toExecOptions())
  }

  private class Directory(val nioDir: Path, val eelDir: EelPath, val descriptor: EelDescriptor) {
    val remoteDir: String
      get() = eelDir.toString()
  }

  private class CustomizationResult(val shellExecOptions: ShellExecOptions) {
    fun getEnvVarValue(envVarName: String): String? = shellExecOptions.envs[envVarName]
  }

  private fun CustomizationResult.assertPathLikeEnv(envName: String, vararg expectedEntries: String) {
    val expectedValue = expectedEntries.toList().reduceOrNull { result, entries ->
      joinEntries(result, entries, eelApi.descriptor)
    } ?: ""
    Assertions.assertThat(shellExecOptions.envs[envName]).isEqualTo(expectedValue)
  }

  private fun CustomizationResult.assertSinglePathEnv(envName: String, expectedPath: Path) {
    val expectedValue = expectedPath.asEelPath(eelApi.descriptor).toString()
    Assertions.assertThat(shellExecOptions.envs[envName]).isEqualTo(expectedValue)
  }
}

private fun joinEntries(path1: String?, path2: String?, descriptor: EelDescriptor): String {
  return if (path1 != null && path2 != null &&
             path1.isNotEmpty() && !path1.endsWith(descriptor.osFamily.pathSeparator) &&
             path2.isNotEmpty() && !path2.startsWith(descriptor.osFamily.pathSeparator)) {
    path1 + descriptor.osFamily.pathSeparator + path2
  }
  else {
    path1.orEmpty() + path2.orEmpty()
  }
}

private const val PATH: String = "PATH"
private const val IJ_FORCE_SET_PREFIX: String = "_INTELLIJ_FORCE_SET_"
private const val IJ_FORCE_PREPEND_PREFIX: String = "_INTELLIJ_FORCE_PREPEND_"
private const val IJ_FORCE_PREPEND_PATH: String = IJ_FORCE_PREPEND_PREFIX + PATH
private const val JEDITERM_SOURCE: String = "JEDITERM_SOURCE"
private const val JEDITERM_SOURCE_ARGS: String = "JEDITERM_SOURCE_ARGS"
private val TIMEOUT: Duration = 60.seconds
