// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.*
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil.toShellName
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.availableCommandsGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCachingGeneratorCommandsRunner
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellDataGeneratorsExecutorImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeContextImpl
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.session.ShellCommandSentListener
import org.jetbrains.plugins.terminal.block.testApps.MoveCursorToLineEndAndPrint
import org.jetbrains.plugins.terminal.block.testApps.SimpleTextRepeater
import org.jetbrains.plugins.terminal.block.util.CommandResult
import org.jetbrains.plugins.terminal.block.util.TerminalSessionTestUtil
import org.jetbrains.plugins.terminal.block.util.TerminalSessionTestUtil.assertCommandResult
import org.jetbrains.plugins.terminal.block.util.TerminalSessionTestUtil.getCommandResultFuture
import org.jetbrains.plugins.terminal.block.util.TerminalSessionTestUtil.sendCommandToExecuteWithoutAddingToHistory
import org.jetbrains.plugins.terminal.block.util.TerminalSessionTestUtil.sendCommandlineToExecuteWithoutAddingToHistory
import org.jetbrains.plugins.terminal.util.ShellType
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@RunWith(Parameterized::class)
internal class BlockTerminalTest(private val shellPath: Path) {

  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun shells(): List<Path> = TerminalSessionTestUtil.getShellPaths()
  }

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(projectRule, disposableRule)

  /**
   * Remove all command spec providers to not load command specs in these tests and make them faster.
   * They are requested in [availableCommandsGenerator], but we don't really need them for these tests.
   */
  @Before
  fun removeCommandSpecs() {
    ExtensionTestUtil.maskExtensions(ShellCommandSpecsProvider.EP_NAME, emptyList(), disposableRule.disposable)
  }

  @Test
  fun `test echo output is read`() {
    val session = startBlockTerminalSession(TermSize(20, 10))
    val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
    session.sendCommandlineToExecuteWithoutAddingToHistory("echo qqq")
    assertCommandResult(0, "qqq\n", outputFuture)
  }

  @Test
  fun `test read from history and screen buffers`() {
    val termSize = TermSize(20, 10)
    val session = startBlockTerminalSession(termSize)
    val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
    val items = listOf(SimpleTextRepeater.Item("Hello, World", false, true, termSize.rows * 3),
                       SimpleTextRepeater.Item("Done", false, true, 1))
    session.sendCommandToExecuteWithoutAddingToHistory(SimpleTextRepeater.Helper.generateCommand(items))
    assertCommandResult(0, SimpleTextRepeater.Helper.getExpectedOutput(items), outputFuture)
  }

  @Test
  fun `test large output`() {
    val items = listOf(SimpleTextRepeater.Item(UUID.randomUUID().toString(), false, true, 10_000),
                       SimpleTextRepeater.Item("Done", false, true, 1))
    setTerminalBufferMaxLines(items.sumOf { it.count })
    val session = startBlockTerminalSession(TermSize(200, 100))
    Benchmark.newBenchmark("$shellPath - large output is read") {
      val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
      session.sendCommandToExecuteWithoutAddingToHistory(SimpleTextRepeater.Helper.generateCommand(items))
      assertCommandResult(0, SimpleTextRepeater.Helper.getExpectedOutput(items), outputFuture)
    }.attempts(1).start()
  }

  val LOG = Logger.getInstance(BlockTerminalTest::class.java)

  @Test
  fun `concurrent command and generator execution`() {
    val session = startBlockTerminalSession()
    // ShellType.FISH doesn't support generators yet
    Assume.assumeTrue(setOf(ShellType.ZSH, ShellType.BASH, ShellType.POWERSHELL).contains(session.shellIntegration.shellType))
    runBlocking {
      for (stepId in 1..100) {
        val startTime = TimeSource.Monotonic.markNow()

        // At first, schedule the generator
        val generatorCommandSent = createCommandSentDeferred(session)
        // Create generator and context each time, because default implementations are caching
        val generatorsExecutor = ShellDataGeneratorsExecutorImpl(session)
        val context = ShellRuntimeContextImpl(
          "",
          "",
          session.shellIntegration.shellType.toShellName(),
          ShellCachingGeneratorCommandsRunner { command -> session.commandExecutionManager.runGeneratorAsync(command).await() }
        )
        val commandsListDeferred: Deferred<List<ShellCommandSpec>> = async(Dispatchers.Default) {
          generatorsExecutor.execute(context, availableCommandsGenerator())
        }
        withTimeout(20.seconds) { generatorCommandSent.await() }
        delay((1..50).random().milliseconds) // wait a little to start the generator

        // Then schedule the user command
        val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
        session.sendCommandlineToExecuteWithoutAddingToHistory("echo foo")

        // Wait until the generator is finished
        val commands: List<ShellCommandSpec> = withTimeout(20.seconds) { commandsListDeferred.await() }
        Assert.assertTrue(commands.isNotEmpty())

        // Wait until the user command is finished
        assertCommandResult(0, "foo\n", outputFuture)
        LOG.debug("#$stepId Done in ${startTime.elapsedNow().inWholeMilliseconds}ms")
      }
    }
  }

  @Test
  fun `long output lines without line breaks`() {
    val termSize = TermSize(20, 10)
    val session = startBlockTerminalSession(termSize)
    val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
    val items = listOf(SimpleTextRepeater.Item("_", true, false, termSize.columns),
                       /* finish with a new line to get rid of trailing '%' in zsh */
                       SimpleTextRepeater.Helper.newLine(session.shellIntegration))
    session.sendCommandToExecuteWithoutAddingToHistory(SimpleTextRepeater.Helper.generateCommand(items))
    assertCommandResult(0, SimpleTextRepeater.Helper.getExpectedOutput(items), outputFuture)
  }

  @Test
  fun `mix of empty area and text #1`() {
    val termSize = TermSize(10, 10)
    val session = startBlockTerminalSession(termSize)
    val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
    val textsToPrint = listOf("foo")
    session.sendCommandToExecuteWithoutAddingToHistory(MoveCursorToLineEndAndPrint.Helper.generateCommand(textsToPrint))
    assertCommandResult(0, MoveCursorToLineEndAndPrint.Helper.getExpectedOutput(termSize, textsToPrint), outputFuture)
  }

  @Test
  fun `mix of empty area and text #2`() {
    val termSize = TermSize(10, 10)
    val session = startBlockTerminalSession(termSize)
    val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
    val textsToPrint = listOf("foo", "a".repeat(termSize.columns  + 1), "b")
    session.sendCommandToExecuteWithoutAddingToHistory(MoveCursorToLineEndAndPrint.Helper.generateCommand(textsToPrint))
    assertCommandResult(0, MoveCursorToLineEndAndPrint.Helper.getExpectedOutput(termSize, textsToPrint), outputFuture)
  }

  @Test
  fun `mix of empty area and text #3`() {
    val termSize = TermSize(15, 15)
    val session = startBlockTerminalSession(termSize)
    val outputFuture: CompletableFuture<CommandResult> = getCommandResultFuture(session)
    val textsToPrint = (1..<termSize.columns).map {
      'a'.toString().repeat(it) + it.toString()
    }
    session.sendCommandToExecuteWithoutAddingToHistory(MoveCursorToLineEndAndPrint.Helper.generateCommand(textsToPrint))
    assertCommandResult(0, MoveCursorToLineEndAndPrint.Helper.getExpectedOutput(termSize, textsToPrint), outputFuture)
  }

  private fun createCommandSentDeferred(session: BlockTerminalSession): CompletableDeferred<Unit> {
    val generatorCommandSent = CompletableDeferred<Unit>()
    val generatorCommandSentDisposable = Disposer.newDisposable(session)
    session.commandExecutionManager.addListener(object : ShellCommandSentListener {
      override fun generatorCommandSent(generatorCommand: String) {
        generatorCommandSent.complete(Unit)
        Disposer.dispose(generatorCommandSentDisposable)
      }
    }, generatorCommandSentDisposable)
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
    TerminalSessionTestUtil.startBlockTerminalSession(projectRule.project, shellPath.toString(), disposableRule.disposable, termSize)
}
