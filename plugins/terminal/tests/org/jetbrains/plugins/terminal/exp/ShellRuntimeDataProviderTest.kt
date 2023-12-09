// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.terminal.completion.ShellEnvironment
import com.intellij.terminal.completion.ShellRuntimeDataProvider
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.exp.completion.IJShellRuntimeDataProvider
import org.jetbrains.plugins.terminal.exp.util.TerminalSessionTestUtil
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.seconds

@RunWith(JUnit4::class)
class ShellRuntimeDataProviderTest : BasePlatformTestCase() {
  private lateinit var session: BlockTerminalSession
  private lateinit var testDirectory: Path

  override fun tearDown() {
    try {
      // Can be not initialized if there is no required Shell
      if (this::session.isInitialized) {
        Disposer.dispose(session)
      }
      if (this::testDirectory.isInitialized) {
        FileUtil.deleteRecursively(testDirectory)
      }
    }
    catch (t: Throwable) {
      addSuppressedException(t)
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun `get all files from directory (zsh)`() {
    doGetFilesTest(shellPath = "/bin/zsh")
  }

  @Test
  fun `get all files from directory (bash)`() {
    doGetFilesTest(shellPath = "/bin/bash")
  }

  @Test
  fun `get shell environment (zsh)`() {
    doGetEnvironmentTest("/bin/zsh")
  }

  @Test
  fun `get shell environment (bash)`() {
    doGetEnvironmentTest("/bin/bash")
  }

  private fun doGetFilesTest(shellPath: String) {
    Assume.assumeTrue("Shell is not found in '$shellPath'", File(shellPath).exists())
    session = TerminalSessionTestUtil.startBlockTerminalSession(project, shellPath, testRootDisposable)
    testDirectory = createTempDirectory(prefix = "runtime_data")

    val expected = listOf(
      file("abcde.txt"),
      file("aghsdml"),
      file("bcde.zip"),
      directory("acde"),
      file("aeufsgf"),
      directory("cedrtuysa"),
      directory("aftyrt")
    )
    expected.forEach { it.create(testDirectory) }

    val actual: List<String> = executeRuntimeDataRequest { provider ->
      provider.getFilesFromDirectory(testDirectory.toString())
    }
    UsefulTestCase.assertSameElements(actual, expected.map { it.toString() } + listOf("./", "../"))
  }

  private fun doGetEnvironmentTest(shellPath: String) {
    Assume.assumeTrue("Shell is not found in '$shellPath'", File(shellPath).exists())
    session = TerminalSessionTestUtil.startBlockTerminalSession(project, shellPath, testRootDisposable)

    val env: ShellEnvironment = executeRuntimeDataRequest { provider ->
      provider.getShellEnvironment()
    } ?: error("Returned environment is null")

    assertNotEmpty(env.envs)
    assertNotEmpty(env.keywords)
    assertNotEmpty(env.builtins)
    assertNotEmpty(env.functions)
    assertNotEmpty(env.commands)
  }

  private fun <T> executeRuntimeDataRequest(request: suspend (ShellRuntimeDataProvider) -> T): T = runBlocking {
    val provider = IJShellRuntimeDataProvider(session)
    val deferred: Deferred<T> = async(Dispatchers.Default) {
      withBackgroundProgress(project, "test", cancellable = true) {
        request(provider)
      }
    }
    withTimeout(5.seconds) { deferred.await() }
  }

  private data class FileDescriptor(val name: String, val isDirectory: Boolean = false) {
    override fun toString(): String = if (isDirectory) "$name/" else name

    fun create(basePath: Path) {
      if (isDirectory) basePath.createDirectory(name) else basePath.createFile(name)
    }
  }

  private fun file(name: String): FileDescriptor = FileDescriptor(name)
  private fun directory(name: String): FileDescriptor = FileDescriptor(name, isDirectory = true)
}