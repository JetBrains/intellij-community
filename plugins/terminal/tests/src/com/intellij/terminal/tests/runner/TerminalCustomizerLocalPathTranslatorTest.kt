package com.intellij.terminal.tests.runner

import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.impl.wsl.WslConstants
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
import com.intellij.platform.ide.impl.wsl.WslEelDescriptor
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.platform.testFramework.junit5.eel.params.api.Wsl
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.io.delete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Run with `intellij.idea.ultimate.tests.main` classpath to test in WSL/Docker.
 * This classpath is used on the buildserver, so WSL/Docker are tested there.
 *
 * If ijent binaries are missing locally (typically on macOS), refer to IJPL-197291 for resolution.
 */
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
@ParameterizedClass
class TerminalCustomizerLocalPathTranslatorTest(private val eelHolder: EelHolder) {

  private val project: Project by projectFixture()
  private val tempDir: Path by tempPathFixture()
  private val testDisposable: Disposable by disposableFixture()

  @Test
  fun `local dir appended to PATH is translated to remote`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    register(deprecatedCustomizer {
      dir.appendHostTo(PATH, it)
    })
    val result = configureStartupOptions(dir) {
      it[PATH] = ""
    }
    Assertions.assertThat(result.getEnvVarValue(PATH))
      .isEqualTo(dir.remoteDir)
  }

  @Test
  fun `local dir prepended to PATH is translated to remote`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    register(deprecatedCustomizer {
      dir.prependHostTo(PATH, it)
    })
    val result = configureStartupOptions(dir) {
      it[PATH] = "foo"
    }
    Assertions.assertThat(result.getEnvVarValue(PATH))
      .isEqualTo(dir.remoteDirAndSeparator + "foo")
  }

  @Test
  fun `existing PATH with semicolon is kept intact`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    val initialPath = "/foo;bar:/usr/bin"
    register(deprecatedCustomizer {
      dir.prependHostTo(PATH, it)
    })
    val result = configureStartupOptions(dir) {
      it[PATH] = initialPath
    }
    Assertions.assertThat(result.getEnvVarValue(PATH))
      .isEqualTo(dir.remoteDirAndSeparator + initialPath)
  }

  @Test
  fun `local dirs appended and prepended are translated to remote`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir1 = tempDir.asDirectory()
    val dir2 = createTmpDir("dir2").asDirectory()
    register(deprecatedCustomizer {
      dir1.prependHostTo(PATH, it)
      dir2.appendHostTo(PATH, it)
    })
    val result = configureStartupOptions(dir1) {
      it[PATH] = "/foo:/bar"
    }
    Assertions.assertThat(result.getEnvVarValue(PATH))
      .isEqualTo(dir1.remoteDirAndSeparator + "/foo:/bar" + dir2.remoteSeparatorAndDir)
  }

  @Test
  fun `local dirs appended and prepended by several customizers are translated to remote`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir1 = tempDir.asDirectory()
    val dir2 = createTmpDir("dir2").asDirectory()
    register(deprecatedCustomizer {
      dir1.prependHostTo(PATH, it)
      dir2.appendHostTo(PATH, it)
    }, deprecatedCustomizer {
      appendToEnvVar(PATH, it, "foo", LocalEelDescriptor)
      prependToEnvVar(PATH, it, "bar", LocalEelDescriptor)
    })
    val result = configureStartupOptions(dir1) {
      it[PATH] = "/path/to/baz"
    }
    Assertions.assertThat(result.getEnvVarValue(PATH))
      .isEqualTo("bar" + dir1.remoteSeparator + dir1.remoteDirAndSeparator +
                 "/path/to/baz" +
                 dir2.remoteSeparatorAndDir + dir1.remoteSeparator + "foo")
  }

  @Test
  fun `non-deprecated customizers are not translated`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    register(customizer {
      dir.appendHostTo(PATH, it)
    })
    val result = configureStartupOptions(dir) {
      it[PATH] = "C:\\Program Files"
    }
    Assertions.assertThat(result.getEnvVarValue(PATH))
      .isEqualTo("C:\\Program Files" + dir.hostSeparatorAndDir)
  }

  @Test
  fun `local path in _INTELLIJ_FORCE_PREPEND_PATH is translated`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    register(deprecatedCustomizer {
      dir.appendHostTo(IJ_PREPEND_PATH, it)
    })
    val result = configureStartupOptions(dir) {
      it[IJ_PREPEND_PATH] = "foo"
    }
    Assertions.assertThat(result.getEnvVarValue(IJ_PREPEND_PATH))
      .isEqualTo("foo" + dir.remoteSeparatorAndDir)
  }

  @Test
  fun `mix of deprecated and non-deprecated customizers`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir1 = tempDir.asDirectory()
    val dir2 = tempDir.asDirectory()
    register(deprecatedCustomizer {
      dir1.appendHostTo(IJ_PREPEND_PATH, it)
    }, customizer {
      dir2.appendHostTo(IJ_PREPEND_PATH, it)
      dir2.appendHostTo(PATH, it)
    }, customizer {
      dir1.prependHostTo(PATH, it)
      dir2.prependHostTo(PATH, it)
    })
    val result = configureStartupOptions(dir1) {
      it[IJ_PREPEND_PATH] = "foo"
      it[PATH] = "bar"
    }
    Assertions.assertThat(result.getEnvVarValue(IJ_PREPEND_PATH))
      .isEqualTo("foo" + dir1.remoteSeparatorAndDir + dir2.hostSeparatorAndDir)
    Assertions.assertThat(result.getEnvVarValue(PATH))
      .isEqualTo(dir2.hostDirAndSeparator + dir1.hostDirAndSeparator + "bar" + dir2.hostSeparatorAndDir)
  }

  @Test
  fun `deletions from PATH are not translated`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir1 = tempDir.asDirectory()
    val dir2 = createTmpDir("dir2").asDirectory()
    val dir3 = createTmpDir("dir3").asDirectory()
    register(deprecatedCustomizer {
      dir1.appendHostTo(PATH, it)
    }, deprecatedCustomizer {
      it[PATH] = it[PATH]!!.removePrefix("/foo:")
      // the next modification won't be translated, because PATH was modified unexpectedly
      dir2.appendHostTo(PATH, it)
    }, deprecatedCustomizer {
      dir3.prependHostTo(PATH, it)
    })
    val result = configureStartupOptions(dir1) {
      it[PATH] = "/foo:/bar"
    }
    Assertions.assertThat(result.getEnvVarValue(PATH))
      .isEqualTo(dir3.remoteDirAndSeparator + "/bar" + dir1.remoteSeparatorAndDir + dir2.hostSeparatorAndDir)
  }

  @Test
  fun `local Windows dir is translated to mounted WSL dir`(): Unit = timeoutRunBlocking(TIMEOUT) {
    Assumptions.assumeTrue(eelHolder.type is Wsl)

    val localDir = createLocalTmpDir()
    Assertions.assertThat(localDir).satisfies({ OSAgnosticPathUtil.isAbsoluteDosPath(it.toString()) })

    val wslDistribution = (eelHolder.eel.descriptor as WslEelDescriptor).distribution
    val mountedWslDir = checkNotNull(wslDistribution.getWslPath(localDir)) {
      "Failed to translate $localDir to WSL mounted dir"
    }
    register(deprecatedCustomizer {
      appendToEnvVar(PATH, it, localDir.toString(), LocalEelDescriptor)
    })

    val dir = tempDir.asDirectory()
    val result = configureStartupOptions(dir) {
      it[PATH] = "C:\\Windows"
    }
    Assertions.assertThat(result.getEnvVarValue(PATH))
      .isEqualTo("C:\\Windows" + dir.remoteSeparator + mountedWslDir)
  }

  @Test
  fun `translate Windows WSL UNC paths with different prefix`(): Unit = timeoutRunBlocking(TIMEOUT) {
    Assumptions.assumeTrue(eelHolder.type is Wsl)

    val dir = tempDir.asDirectory()
    val dirWithOtherPrefix = Path.of(buildWslUncPathWithOtherPrefix(dir.nioDir.toString()))
    Assumptions.assumeTrue(Files.isDirectory(dirWithOtherPrefix))

    register(deprecatedCustomizer {
      prependToEnvVar(PATH, it, dirWithOtherPrefix.toString(), LocalEelDescriptor)
    })

    val result = configureStartupOptions(dir) {
      it[PATH] = "/usr/bin"
    }
    Assertions.assertThat(result.getEnvVarValue(PATH))
      .isEqualTo(dir.remoteDirAndSeparator + "/usr/bin")
  }

  @Test
  fun `translate Windows WSL UNC paths with different prefix (Unix separators)`(): Unit = timeoutRunBlocking(TIMEOUT) {
    Assumptions.assumeTrue(eelHolder.type is Wsl)

    val dir = tempDir.asDirectory()
    val dirWithOtherPrefix = Path.of(buildWslUncPathWithOtherPrefix(dir.nioDir.toString()))
    Assumptions.assumeTrue(Files.isDirectory(dirWithOtherPrefix))

    register(deprecatedCustomizer {
      prependToEnvVar(PATH, it, FileUtilRt.toSystemIndependentName(dirWithOtherPrefix.toString()), LocalEelDescriptor)
    })

    val result = configureStartupOptions(dir) {
      it[PATH] = "/usr/bin"
    }
    Assertions.assertThat(result.getEnvVarValue(PATH))
      .isEqualTo(dir.remoteDirAndSeparator + "/usr/bin")
  }

  @Test
  fun `ensure relative paths are not translated`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir1 = tempDir.asDirectory()
    val dir2 = createTmpDir("dir2").asDirectory()
    register(deprecatedCustomizer {
      dir1.prependHostTo(PATH, it)
      appendToEnvVar(PATH, it, ".", LocalEelDescriptor)
    }, deprecatedCustomizer {
      appendToEnvVar(PATH, it, "./foo", LocalEelDescriptor)
      prependToEnvVar(PATH, it, "./bar", LocalEelDescriptor)
      dir2.appendHostTo(PATH, it)
    })
    val result = configureStartupOptions(dir1) {
      it[PATH] = "/path/to/baz"
    }
    val remoteSeparator = dir1.remoteSeparator
    Assertions.assertThat(result.getEnvVarValue(PATH))
      .isEqualTo("./bar" + remoteSeparator +
                 dir1.remoteDirAndSeparator +
                 "/path/to/baz" +
                 remoteSeparator + "." +
                 remoteSeparator + "./foo" +
                 dir2.remoteSeparatorAndDir)
  }

  @Test
  fun `Go-related local dirs are translated to remote`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()

    val gopath = "GOPATH"
    val ijForceSetGopath = "_INTELLIJ_FORCE_SET_GOPATH"
    val goroot = "GOROOT"
    val ijForceSetGoroot = "_INTELLIJ_FORCE_SET_GOROOT"

    val gopathDir = createTmpDir(gopath).asDirectory()
    val binDir = createTmpDir("bin").asDirectory()
    val gorootDir = createTmpDir("GOROOT").asDirectory()
    register(deprecatedCustomizer {
      gopathDir.appendHostTo(gopath, it)
      binDir.appendHostTo(IJ_PREPEND_PATH, it)
      gopathDir.appendHostTo(ijForceSetGopath, it)
      it[goroot] = gorootDir.nioDir.toString()
      it[ijForceSetGoroot] = gorootDir.nioDir.toString()
    })
    val result = configureStartupOptions(dir) {
      it[gopath] = ""
      it[IJ_PREPEND_PATH] = ""
      it[ijForceSetGopath] = ""
    }
    Assertions.assertThat(result.getEnvVarValue(gopath))
      .isEqualTo(gopathDir.remoteDir)
    Assertions.assertThat(result.getEnvVarValue(IJ_PREPEND_PATH))
      .isEqualTo(binDir.remoteDir)
    Assertions.assertThat(result.getEnvVarValue(ijForceSetGopath))
      .isEqualTo(gopathDir.remoteDir)
    Assertions.assertThat(result.getEnvVarValue(goroot))
      .isEqualTo(gorootDir.remoteDir)
    Assertions.assertThat(result.getEnvVarValue(ijForceSetGoroot))
      .isEqualTo(gorootDir.remoteDir)
  }

  @Test
  fun `Windows WSL UNC paths with Unix separators are translated`(): Unit = timeoutRunBlocking(TIMEOUT) {
    Assumptions.assumeTrue(eelHolder.type is Wsl)

    val dir1 = tempDir.asDirectory()
    val dir2 = createTmpDir("dir2").asDirectory()

    register(deprecatedCustomizer {
      prependToEnvVar(PATH, it, FileUtilRt.toSystemIndependentName(dir1.nioDir.toString()), LocalEelDescriptor)
      appendToEnvVar(PATH, it, "foo", LocalEelDescriptor)
    }, deprecatedCustomizer {
      appendToEnvVar(PATH, it, FileUtilRt.toSystemIndependentName(dir2.nioDir.toString()), LocalEelDescriptor)
    })

    val initialPath = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games:/usr/local/games"
    val result = configureStartupOptions(dir1) {
      it[PATH] = initialPath
    }
    Assertions.assertThat(result.getEnvVarValue(PATH))
      .isEqualTo(dir1.remoteDirAndSeparator + initialPath + dir1.remoteSeparator + "foo" + dir2.remoteSeparatorAndDir)
  }

  @Test
  fun `translate JEDITERM_SOURCE to remote`(): Unit = timeoutRunBlocking(TIMEOUT) {
    val dir = tempDir.asDirectory()
    val fileToSource = dir.nioDir.resolve("test.sh")
    register(deprecatedCustomizer {
      it[JEDITERM_SOURCE] = fileToSource.toString()
    })
    val result = configureStartupOptions(dir) {
      it.remove(JEDITERM_SOURCE)
    }
    Assertions.assertThat(result.getEnvVarValue(JEDITERM_SOURCE))
      .isEqualTo(fileToSource.asEelPath(dir.descriptor).toString())
  }

  private fun customizer(handler: (envs: MutableMap<String, String>) -> Unit): LocalTerminalCustomizer {
    return object : LocalTerminalCustomizer() {
      override fun customizeCommandAndEnvironment(
        project: Project,
        workingDirectory: String?,
        shellCommand: List<String>,
        envs: MutableMap<String, String>,
        eelDescriptor: EelDescriptor,
      ): List<String?> {
        handler(envs)
        return shellCommand
      }
    }
  }

  private fun deprecatedCustomizer(handler: (envs: MutableMap<String, String>) -> Unit): LocalTerminalCustomizer {
    return object : LocalTerminalCustomizer() {
      @Suppress("OVERRIDE_DEPRECATION")
      override fun customizeCommandAndEnvironment(
        project: Project,
        workingDirectory: String?,
        command: Array<String>,
        envs: MutableMap<String, String>,
      ): Array<String> {
        handler(envs)
        return command
      }
    }
  }

  private fun register(vararg customizers: LocalTerminalCustomizer) {
    ExtensionTestUtil.maskExtensions(
      LocalTerminalCustomizer.EP_NAME,
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

  private suspend fun createLocalTmpDir(prefix: String = "local"): Path {
    val dir = withContext(Dispatchers.IO) {
      Files.createTempDirectory(prefix)
    }
    val realDir = dir.toRealPath()
    Disposer.register(testDisposable) {
      realDir.delete(recursively = true)
    }
    return realDir
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
    return CustomizationResult(configuredOptions.envVariables)
  }

  private class Directory(val nioDir: Path, val eelDir: EelPath, val descriptor: EelDescriptor) {
    fun appendHostTo(envVarName: String, envs: MutableMap<String, String>) {
      appendToEnvVar(envVarName, envs, nioDir.toString(), LocalEelDescriptor)
    }

    fun prependHostTo(envVarName: String, envs: MutableMap<String, String>) {
      prependToEnvVar(envVarName, envs, nioDir.toString(), LocalEelDescriptor)
    }

    val remoteSeparatorAndDir: String
      get() = descriptor.osFamily.pathSeparator + eelDir.toString()

    val remoteDirAndSeparator: String
      get() = eelDir.toString() + descriptor.osFamily.pathSeparator

    val hostSeparatorAndDir: String
      get() = LocalEelDescriptor.osFamily.pathSeparator + nioDir.toString()

    val hostDirAndSeparator: String
      get() = nioDir.toString() + LocalEelDescriptor.osFamily.pathSeparator

    val remoteDir: String
      get() = eelDir.toString()

    val remoteSeparator: String
      get() = descriptor.osFamily.pathSeparator
  }

  private class CustomizationResult(val configuredEnvs: Map<String, String>) {
    fun getEnvVarValue(envVarName: String): String = checkNotNull(configuredEnvs[envVarName])
  }

}

private fun prependToEnvVar(envVarName: String, envs: MutableMap<String, String>, pathToPrepend: String, descriptor: EelDescriptor) {
  envs[envVarName] = joinPaths(pathToPrepend, envs[envVarName].orEmpty(), descriptor)
}

private fun appendToEnvVar(envVarName: String, envs: MutableMap<String, String>, pathToAppend: String, descriptor: EelDescriptor) {
  envs[envVarName] = joinPaths(envs[envVarName].orEmpty(), pathToAppend, descriptor)
}

private fun joinPaths(path1: String, path2: String, descriptor: EelDescriptor): String {
  return if (path1.isNotEmpty() && !path1.endsWith(descriptor.osFamily.pathSeparator) &&
             path2.isNotEmpty() && !path2.startsWith(descriptor.osFamily.pathSeparator)) {
    path1 + descriptor.osFamily.pathSeparator + path2
  }
  else {
    path1 + path2
  }
}

private fun buildWslUncPathWithOtherPrefix(windowsUncPath: String): String {
  val winSepUncPath = FileUtilRt.toSystemDependentName(windowsUncPath, '\\')
  val wslPath = WslPath.parseWindowsUncPath(winSepUncPath)!!
  val prefix = wslPath.wslRoot.removeSuffix(wslPath.distributionId)
  val otherPrefix = (listOf(WslConstants.UNC_PREFIX, "\\\\wsl.localhost\\") - prefix).single()
  check(winSepUncPath.startsWith(prefix))
  return otherPrefix + winSepUncPath.removePrefix(prefix)
}

private const val PATH: String = "PATH"
private const val IJ_PREPEND_PATH: String = "_INTELLIJ_FORCE_PREPEND_PATH"
private const val JEDITERM_SOURCE: String = "JEDITERM_SOURCE"
private val TIMEOUT: Duration = 60.seconds
