package com.intellij.terminal.tests.startup

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.createTemporaryDirectory
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.pathSeparator
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.io.delete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecCommandImpl
import org.jetbrains.plugins.terminal.startup.ShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer
import org.junit.jupiter.api.Test
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
  private val testDisposable: Disposable by disposableFixture()

  @Test
  fun `local dir appended to PATH is translated to remote`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    register(customizer {
      it.appendEntryToPATH(dir.nioDir)
    })
    val result = configureStartupOptions(dir) {
      it[PATH] = ""
    }
    result.assertPathLikeEnv(PATH, dir.remoteDir)
  }

  @Test
  fun `local dir prepended to PATH is translated to remote`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    register(customizer {
      it.prependEntryToPATH(dir.nioDir)
    })
    val result = configureStartupOptions(dir) {
      it[PATH] = "foo"
    }
    result.assertPathLikeEnv(PATH, dir.remoteDir, "foo")
  }

  @Test
  fun `existing PATH with semicolon is kept intact`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    val initialPath = "/foo;bar:/usr/bin"
    register(customizer {
      it.prependEntryToPATH(dir.nioDir)
    })
    val result = configureStartupOptions(dir) {
      it[PATH] = initialPath
    }
    result.assertPathLikeEnv(PATH, dir.remoteDir, initialPath)
  }

  @Test
  fun `local dirs appended and prepended are translated to remote`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir1 = tempDir.asDirectory()
    val dir2 = createTmpDir("dir2").asDirectory()
    register(customizer {
      it.prependEntryToPATH(dir1.nioDir)
      it.appendEntryToPATH(dir2.nioDir)
    })
    val result = configureStartupOptions(dir1) {
      it[PATH] = "/foo:/bar"
    }
    result.assertPathLikeEnv(PATH, dir1.remoteDir, "/foo:/bar", dir2.remoteDir)
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
    val result = configureStartupOptions(dir1) {
      it[PATH] = "/path/to/baz"
    }
    Assertions.assertThat(result.shellExecOptions.envs[PATH]).isEqualTo(
      "bar" + LocalEelDescriptor.osFamily.pathSeparator + dir1.remoteDir + eelHolder.eel.descriptor.osFamily.pathSeparator +
      "/path/to/baz" +
      eelHolder.eel.descriptor.osFamily.pathSeparator + dir2.remoteDir + LocalEelDescriptor.osFamily.pathSeparator + "foo"
    )
  }

  @Test
  fun `local path in _INTELLIJ_FORCE_PREPEND_PATH is translated`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    register(customizer {
      it.appendEntryToPathLikeEnv(IJ_PREPEND_PATH, dir.nioDir)
    })
    val result = configureStartupOptions(dir) {
      it[IJ_PREPEND_PATH] = "foo"
    }
    result.assertPathLikeEnv(IJ_PREPEND_PATH, "foo", dir.remoteDir)
  }

  @Test
  fun `prepend to custom Path-like env`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    val customEnvName = "MY_ENV"
    register(customizer {
      it.prependEntryToPathLikeEnv(customEnvName, dir.nioDir)
    })
    val result = configureStartupOptions(dir) {
      it[customEnvName] = "foo"
    }
    result.assertPathLikeEnv(customEnvName, dir.remoteDir, "foo")
  }

  @Test
  fun `env deletion`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    val customEnvName = "MY_ENV"
    register(customizer {
      it.setEnvironmentVariable(customEnvName, null)
    })
    val result = configureStartupOptions(dir) {
      it[customEnvName] = "foo"
    }
    Assertions.assertThat(result.getEnvVarValue(customEnvName)).isNull()
  }

  @Test
  fun `translate JEDITERM_SOURCE to remote`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    val fileToSource = dir.nioDir.resolve("test.sh")
    register(customizer {
      it.setEnvironmentVariableToPath(JEDITERM_SOURCE, fileToSource)
    })
    val result = configureStartupOptions(dir) {
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
      Assertions.assertThat(it.envs[PATH]).endsWith(joinEntries("bar", dir.remoteDir, eelHolder.eel.descriptor))

      it.setExecCommand(newExecCommand)
      Assertions.assertThat(it.execCommand).isEqualTo(newExecCommand)
    })
    val result = configureStartupOptions(tempDir.asDirectory()) {
      it[PATH] = "/path/to/baz"
    }
    Assertions.assertThat(result.shellExecOptions.envs[customEnvName1]).isEqualTo("foo")
    Assertions.assertThat(result.shellExecOptions.envs[customEnvName2]).isEqualTo(dir.remoteDir)
    result.assertPathLikeEnv(PATH, "/path/to/baz", "bar", dir.remoteDir)
    Assertions.assertThat(result.shellExecOptions.execCommand).isEqualTo(newExecCommand)
  }

  private fun customizer(handler: (execOptions: MutableShellExecOptions) -> Unit): ShellExecOptionsCustomizer {
    return object : ShellExecOptionsCustomizer {
      override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
        handler(shellExecOptions)
      }
    }
  }

  private fun register(vararg customizers: ShellExecOptionsCustomizer) {
    ExtensionTestUtil.maskExtensions(
      ShellExecOptionsCustomizer.EP_NAME,
      customizers.toList(),
      testDisposable
    )
  }

  private fun Path.asDirectory(descriptor: EelDescriptor = eelHolder.eel.descriptor): Directory {
    val nioDir = this
    Assertions.assertThat(nioDir.getEelDescriptor()).isEqualTo(descriptor)
    Assertions.assertThat(nioDir).isDirectory()
    val eelDir = nioDir.asEelPath(descriptor)
    Assertions.assertThat(eelDir.descriptor).isEqualTo(descriptor)
    return Directory(nioDir, eelDir, descriptor)
  }

  private suspend fun createTmpDir(prefix: String, eelApi: EelApi = eelHolder.eel): Path {
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
    initialEnvironmentCallback: ((MutableMap<String, String>) -> Unit)? = null,
  ): CustomizationResult {
    fileLogger().info("Running on ${workingDir.descriptor} in ${workingDir.nioDir} (${workingDir.eelDir})")
    val startupOptionsBuilder = ShellStartupOptions.Builder().workingDirectory(workingDir.nioDir.toString())
    initialEnvironmentCallback?.invoke(startupOptionsBuilder.envVariables as MutableMap<String, String>)
    val startupOptions = startupOptionsBuilder.build()
    val terminalRunner = LocalTerminalDirectRunner(project)
    val configuredOptions = terminalRunner.configureStartupOptions(startupOptions)
    return CustomizationResult(configuredOptions.toExecOptions(workingDir.descriptor))
  }

  private class Directory(val nioDir: Path, val eelDir: EelPath, val descriptor: EelDescriptor) {
    val remoteDir: String
      get() = eelDir.toString()
  }

  private class CustomizationResult(val shellExecOptions: ShellExecOptions) {
    fun getEnvVarValue(envVarName: String): String? = shellExecOptions.envs[envVarName]
  }

  private fun CustomizationResult.assertPathLikeEnv(envName: String, vararg expectedEntries: String) {
    val expectedValue = expectedEntries.toList().reduce { result, entries ->
      joinEntries(result, entries, eelHolder.eel.descriptor)
    }
    Assertions.assertThat(shellExecOptions.envs[envName]).isEqualTo(expectedValue)
  }

  private fun CustomizationResult.assertSinglePathEnv(envName: String, expectedPath: Path) {
    val expectedValue = expectedPath.asEelPath(eelHolder.eel.descriptor).toString()
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
private const val IJ_PREPEND_PATH: String = "_INTELLIJ_FORCE_PREPEND_PATH"
private const val JEDITERM_SOURCE: String = "JEDITERM_SOURCE"
private val TIMEOUT: Duration = 60.seconds
