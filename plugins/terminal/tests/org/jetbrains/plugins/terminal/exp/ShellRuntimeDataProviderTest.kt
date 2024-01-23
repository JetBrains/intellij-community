// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.terminal.completion.ShellEnvironment
import com.intellij.terminal.completion.ShellRuntimeDataProvider
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.UsefulTestCase.assertNotEmpty
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.testFramework.utils.io.deleteRecursively
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.exp.completion.IJShellRuntimeDataProvider
import org.jetbrains.plugins.terminal.exp.completion.ShellCommandExecutorImpl
import org.jetbrains.plugins.terminal.exp.util.TerminalSessionTestUtil
import org.jetbrains.plugins.terminal.util.ShellType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.seconds

@RunWith(Parameterized::class)
class ShellRuntimeDataProviderTest(private val shellPath: Path) {
  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(projectRule, disposableRule)

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun shells(): List<Path> = TerminalSessionTestUtil.getShellPaths()
  }

  @Test
  fun `get all files from directory`() {
    val session = TerminalSessionTestUtil.startBlockTerminalSession(projectRule.project, shellPath.toString(), disposableRule.disposable)
    val testDirectory = createTempDirectory(prefix = "runtime_data")
    Disposer.register(disposableRule.disposable) { testDirectory.deleteRecursively() }

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

    val actual: List<String> = executeRuntimeDataRequest(session) { provider ->
      provider.getFilesFromDirectory(testDirectory.toString())
    }
    val expectedNames = expected.map { it.toString() } +
                        // do not expect cur and parent dir paths in PowerShell, since shell-based completion also does not suggest them
                        if (session.shellIntegration.shellType != ShellType.POWERSHELL) listOf("./", "../") else emptyList()
    UsefulTestCase.assertSameElements(actual, expectedNames)
  }

  @Test
  fun `get shell environment`() {
    val session = TerminalSessionTestUtil.startBlockTerminalSession(projectRule.project, shellPath.toString(), disposableRule.disposable)

    val env: ShellEnvironment = executeRuntimeDataRequest(session) { provider ->
      provider.getShellEnvironment()
    } ?: error("Returned environment is null")

    assertNotEmpty(env.builtins)
    assertNotEmpty(env.functions)
    assertNotEmpty(env.commands)
  }

  private fun <T> executeRuntimeDataRequest(session: BlockTerminalSession,
                                            request: suspend (ShellRuntimeDataProvider) -> T): T = runBlocking {
    val provider = IJShellRuntimeDataProvider(session, ShellCommandExecutorImpl(session))
    val deferred: Deferred<T> = async(Dispatchers.Default) {
      withBackgroundProgress(projectRule.project, "test", cancellable = true) {
        request(provider)
      }
    }
    withTimeout(5.seconds) { deferred.await() }
  }

  private data class FileDescriptor(val name: String, val isDirectory: Boolean = false) {
    override fun toString(): String = if (isDirectory) "$name${File.separatorChar}" else name

    fun create(basePath: Path) {
      if (isDirectory) basePath.createDirectory(name) else basePath.createFile(name)
    }
  }

  private fun file(name: String): FileDescriptor = FileDescriptor(name)
  private fun directory(name: String): FileDescriptor = FileDescriptor(name, isDirectory = true)
}