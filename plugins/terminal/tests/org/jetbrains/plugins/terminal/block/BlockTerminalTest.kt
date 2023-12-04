// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.terminal.completion.ShellEnvironment
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.block.testApps.SimpleTextRepeater
import org.jetbrains.plugins.terminal.exp.*
import org.jetbrains.plugins.terminal.exp.completion.IJShellRuntimeDataProvider
import org.jetbrains.plugins.terminal.exp.util.TerminalSessionTestUtil
import org.jetbrains.plugins.terminal.util.ShellType
import org.junit.Assert
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@RunWith(Parameterized::class)
class BlockTerminalTest(private val shellPath: String) {

  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun shells(): List<String> = listOf(
      "/bin/zsh",
      "/bin/bash",
      "/usr/local/bin/fish",
    ).filter { File(it).exists() }
  }

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(projectRule, disposableRule)

  @Test
  fun `test echo output is read`() {
    val session = startBlockTerminalSession(TermSize(20, 10))
    val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
    session.sendCommandToExecuteWithoutAddingToHistory("echo qqq")
    assertCommandResult(0, "qqq\n", outputFuture)
  }

  @Test
  fun `test read from history and screen buffers`() {
    val termSize = TermSize(20, 10)
    val session = startBlockTerminalSession(termSize)
    val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
    val items = listOf(SimpleTextRepeater.Item("Hello, World", false, true, termSize.rows * 3),
                       SimpleTextRepeater.Item("Done", false, true, 1))
    session.sendCommandToExecuteWithoutAddingToHistory(SimpleTextRepeater.Helper.generateCommandLine(items))
    assertCommandResult(0, SimpleTextRepeater.Helper.getExpectedOutput(items), outputFuture)
  }

  @Test
  fun `test large output`() {
    val items = listOf(SimpleTextRepeater.Item(UUID.randomUUID().toString(), false, true, 10_000),
                       SimpleTextRepeater.Item("Done", false, true, 1))
    setTerminalBufferMaxLines(items.sumOf { it.count })
    val session = startBlockTerminalSession(TermSize(200, 100))
    PlatformTestUtil.startPerformanceTest("large output is read", 30000) {
      val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
      session.sendCommandToExecuteWithoutAddingToHistory(SimpleTextRepeater.Helper.generateCommandLine(items))
      assertCommandResult(0, SimpleTextRepeater.Helper.getExpectedOutput(items), outputFuture)
    }.attempts(1).assertTiming()
  }

  @Test
  fun `concurrent command and generator execution`() {
    val session = startBlockTerminalSession()
    // ShellType.FISH doesn't support generators yet
    Assume.assumeTrue(setOf(ShellType.ZSH, ShellType.BASH).contains(session.shellIntegration?.shellType))
    runBlocking {
      for (stepId in 1..100) {
        val startTime = TimeSource.Monotonic.markNow()
        val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
        val generatorCommandSent = createCommandSentDeferred(session)
        val envListDeferred: Deferred<ShellEnvironment?> = this.async(Dispatchers.Default) {
          IJShellRuntimeDataProvider(session).getShellEnvironment()
        }
        withTimeout(20.seconds) { generatorCommandSent.await() }
        delay((1..50).random().milliseconds) // wait a little to start generator
        session.sendCommandToExecute("echo foo")
        val env: ShellEnvironment? = withTimeout(20.seconds) { envListDeferred.await() }
        Assert.assertTrue(env != null && env.envs.isNotEmpty())
        assertCommandResult(0, "foo\n", outputFuture)
        println("#$stepId Done in ${startTime.elapsedNow().inWholeMilliseconds}ms")
      }
    }
  }

  @Test
  fun `long output lines without line breaks`() {
    val termSize = TermSize(20, 10)
    val session = startBlockTerminalSession(termSize)
    val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
    val items = listOf(SimpleTextRepeater.Item("_", true, false, termSize.columns),
                       SimpleTextRepeater.Item.NEW_LINE /* finish with a new line to get rid of trailing '%' in zsh */)
    session.sendCommandToExecuteWithoutAddingToHistory(SimpleTextRepeater.Helper.generateCommandLine(items))
    assertCommandResult(0, SimpleTextRepeater.Helper.getExpectedOutput(items), outputFuture)
  }

  private fun createCommandSentDeferred(session: TerminalSession): CompletableDeferred<Unit> {
    val generatorCommandSent = CompletableDeferred<Unit>()
    val generatorCommandSentDisposable = Disposer.newDisposable().also { disposable ->
      generatorCommandSent.invokeOnCompletion { Disposer.dispose(disposable) }
    }
    session.commandManager.commandExecutionManager.addCommandSentListener(generatorCommandSentDisposable) {
      generatorCommandSent.complete(Unit)
    }
    return generatorCommandSent
  }

  private fun setTerminalBufferMaxLines(maxBufferLines: Int) {
    val terminalBufferMaxLinesCount = "terminal.buffer.max.lines.count"
    val prevValue = AdvancedSettings.getInt(terminalBufferMaxLinesCount)
    AdvancedSettings.setInt(terminalBufferMaxLinesCount, maxBufferLines)
    Disposer.register(disposableRule.disposable) {
      AdvancedSettings.setInt(terminalBufferMaxLinesCount, prevValue)
    }
  }

  private fun startBlockTerminalSession(termSize: TermSize = TermSize(80, 24)) =
    TerminalSessionTestUtil.startBlockTerminalSession(projectRule.project, shellPath, disposableRule.disposable, termSize)

  private fun TerminalSession.sendCommandToExecuteWithoutAddingToHistory(shellCommand: String) {
    this.sendCommandToExecute(" $shellCommand")
  }

  @Suppress("SameParameterValue")
  private fun assertCommandResult(expectedExitCode: Int, expectedOutput: String, actualResultFuture: CompletableFuture<CommandResult>) {
    val actualResult = actualResultFuture.get(20_000, TimeUnit.MILLISECONDS)
    var actualOutput = StringUtil.splitByLinesDontTrim(actualResult.output).joinToString("\n") { it.trimEnd() }
    if (expectedOutput == actualOutput + "\n") {
      actualOutput += "\n"
    }
    Assert.assertEquals(stringify(expectedExitCode, expectedOutput), stringify(actualResult.exitCode, actualOutput))
  }

  private fun stringify(exitCode: Int, output: String): String {
    return "exit_code:$exitCode, output: $output"
  }
}

fun getCommandResultFuture(session: TerminalSession): CompletableFuture<CommandResult> {
  val disposable = Disposer.newDisposable(session)
  val scraper = ShellCommandOutputScraper(session)
  val lastOutput: AtomicReference<StyledCommandOutput> = AtomicReference()
  scraper.addListener(object : ShellCommandOutputListener {
    override fun commandOutputChanged(output: StyledCommandOutput) {
      lastOutput.set(output)
    }
  }, disposable)
  val result: CompletableFuture<CommandResult> = CompletableFuture()
  session.commandManager.addListener(object : ShellCommandListener {
    override fun commandFinished(command: String?, exitCode: Int, duration: Long?) {
      val (text) = scraper.scrapeOutput()
      result.complete(CommandResult(exitCode, text))
      Disposer.dispose(disposable)
    }
  }, disposable)
  return result
}

data class CommandResult(val exitCode: Int, val output: String)