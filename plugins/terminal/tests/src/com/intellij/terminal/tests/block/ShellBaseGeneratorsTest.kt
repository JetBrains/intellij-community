// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block

import com.intellij.openapi.util.Disposer
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import com.intellij.terminal.tests.block.util.TerminalSessionTestUtil
import com.intellij.terminal.tests.block.util.TerminalSessionTestUtil.getCommandResultFuture
import com.intellij.terminal.tests.block.util.TerminalSessionTestUtil.sendCommandlineToExecuteWithoutAddingToHistory
import com.intellij.testFramework.*
import com.intellij.testFramework.UsefulTestCase.assertNotEmpty
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.testFramework.utils.io.deleteRecursively
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil.toShellName
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.availableCommandsGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.fileSuggestionsGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCachingGeneratorCommandsRunner
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellDataGeneratorsExecutorImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellEnvBasedGenerators.aliasesGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeContextImpl
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.util.ShellType
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.seconds

@RunWith(Parameterized::class)
internal class ShellBaseGeneratorsTest(private val shellPath: Path) {
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

  @Before
  fun removeAllCommandSpecProviders() {
    // Remove all command spec providers to not load command specs in these tests and make them faster.
    // They are requested in availableCommandsGenerator, but we don't really need them for these tests.
    ExtensionTestUtil.maskExtensions(ShellCommandSpecsProvider.EP_NAME, emptyList(), disposableRule.disposable)
  }

  @Test
  fun `get all files from directory`() {
    val session = TerminalSessionTestUtil.startBlockTerminalSession(projectRule.project, shellPath.toString(), disposableRule.disposable)
    Assume.assumeTrue(session.isBashZshPwsh())
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

    // fileSuggestionsGenerator is using 'typedPrefix' property of ShellRuntimeContext to determine the location
    val typedPrefix = testDirectory.toString() + File.separatorChar
    val suggestions = runGenerator(session, fileSuggestionsGenerator(), typedPrefix).filter { !it.isHidden }
    val actualNames = suggestions.map { it.name }.filter { it.isNotEmpty() }
    val expectedNames = expected.map { it.toString() }
    UsefulTestCase.assertSameElements(actualNames, expectedNames)
  }

  @Test
  fun `get available commands`() {
    val session = TerminalSessionTestUtil.startBlockTerminalSession(projectRule.project, shellPath.toString(), disposableRule.disposable)
    Assume.assumeTrue(session.isBashZshPwsh())

    val commands = runGenerator(session, availableCommandsGenerator())
    assertNotEmpty(commands)
  }

  @Test
  fun `get aliases`() {
    val session = TerminalSessionTestUtil.startBlockTerminalSession(projectRule.project, shellPath.toString(), disposableRule.disposable)
    Assume.assumeTrue(session.isBashZshPwsh())

    // Execute a command to create a custom alias
    val aliasName = "myCustomTestAlias"
    val createAliasCommand = if (session.shellIntegration.shellType == ShellType.POWERSHELL) {
      "New-Alias '$aliasName' 'ls'"
    }
    else "alias $aliasName='ls'"
    val resultFuture = getCommandResultFuture(session)
    session.sendCommandlineToExecuteWithoutAddingToHistory(createAliasCommand)
    val commandResult = resultFuture.get(5, TimeUnit.SECONDS)
    assertEquals("Add alias command failed: $commandResult", 0, commandResult.exitCode)

    // Check that we are able to retrieve the created alias
    val aliases = runGenerator(session, aliasesGenerator())
    assertTrue("Created alias is not found: $aliases", aliases.contains(aliasName))
  }

  private fun <T : Any> runGenerator(
    session: BlockTerminalSession,
    generator: ShellRuntimeDataGenerator<T>,
    typedPrefix: String = ""
  ) = runBlocking {
    val executor = ShellDataGeneratorsExecutorImpl(session)
    val context = ShellRuntimeContextImpl(
      currentDirectory = "",
      typedPrefix,
      session.shellIntegration.shellType.toShellName(),
      ShellCachingGeneratorCommandsRunner { command -> session.commandExecutionManager.runGeneratorAsync(command).await() }
    )
    val deferred: Deferred<T> = async(Dispatchers.Default) {
      executor.execute(context, generator)
    }
    withTimeout(5.seconds) { deferred.await() }
  }

  private fun BlockTerminalSession.isBashZshPwsh(): Boolean {
    val type = shellIntegration.shellType
    return type == ShellType.BASH || type == ShellType.ZSH || type == ShellType.POWERSHELL
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
