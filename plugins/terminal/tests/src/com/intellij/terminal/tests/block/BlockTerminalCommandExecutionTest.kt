// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.idea.TestFor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.TextRange
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.tests.block.testApps.SimpleTextRepeater
import com.intellij.terminal.tests.block.util.TerminalSessionTestUtil
import com.intellij.terminal.tests.block.util.TerminalSessionTestUtil.toCommandLine
import com.intellij.testFramework.*
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TerminalCustomCommandListener
import junit.framework.TestCase.failNotEquals
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.block.BlockTerminalView
import org.jetbrains.plugins.terminal.block.output.*
import org.jetbrains.plugins.terminal.block.prompt.ShellEditorBufferReportShellCommandListener
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.util.ShellType
import org.junit.Assert
import org.junit.Assume
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.fail
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.function.BiPredicate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunsInEdt
@RunWith(Parameterized::class)
internal class BlockTerminalCommandExecutionTest(private val shellPath: Path) {

  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(EdtRule(), projectRule, disposableRule)

  @Test
  fun `commands are executed in order`() {
    Assume.assumeFalse(SystemInfo.isWindows)

    val (session, view) = startSessionAndCreateView()
    val count = 50
    val expected = (1..count).map {
      val message = "Hello, World $it"
      CommandResult(listOf("echo", message).toCommandLine(session), message)
    }
    expected.forEach {
      session.commandExecutionManager.sendCommandToExecute(it.command)
    }
    awaitBlocksFinalized(view.outputView.controller.outputModel, count, 60.seconds)
    val actual = view.outputView.controller.outputModel.collectCommandResults()
    Assert.assertEquals(expected, actual)
  }

  @TestFor(issues = ["IJPL-156363"])
  @Test
  fun `multiline commands with bracketed mode`() {
    Assume.assumeFalse(SystemInfo.isWindows)

    val (session, view) = startSessionAndCreateView()
    assumeTrue(session.model.isBracketedPasteMode)
    val expected = listOf(
      CommandResult("echo 1\necho 2", "1\n2")
    )
    expected.forEach {
      session.commandExecutionManager.sendCommandToExecute(it.command)
    }
    awaitBlocksFinalized(view.outputView.controller.outputModel, expected.size)
    val actual = view.outputView.controller.outputModel.collectCommandResults()
    Assert.assertEquals(expected, actual)
  }

  @TestFor(issues = ["IJPL-156274"])
  @Test
  fun `multiline commands without bracketed mode`() {
    Assume.assumeFalse(SystemInfo.isWindows)

    val (session, view) = startSessionAndCreateView()
    Assume.assumeFalse(session.model.isBracketedPasteMode)
    session.commandExecutionManager.sendCommandToExecute("echo 1\necho 2")
    val expected = listOf(
      // The first block is created when the command is sent to execute.
      CommandResult("echo 1\necho 2", "1"),
      // The second block is created by command_started event.
      CommandResult("echo 2", "2"),
    )
    awaitBlocksFinalized(view.outputView.controller.outputModel, expected.size)
    val actual = view.outputView.controller.outputModel.collectCommandResults()
    assertListEquals(expected, actual) { a, b ->
      /*
      It was a flaky test.
      Sometimes for the last block, there is an unexpected newline in output.
      So I trim the output to avoid this.
      */
      Objects.equals(a.command, b.command) && Objects.equals(a.output.trim(), b.output.trim())
    }
  }

  @Test
  fun `shell integration sends correct events`() {
    Assume.assumeFalse(SystemInfo.isWindows)

    val actual = mutableListOf<String?>()
    val session = startBlockTerminalSession(disableSavingHistory = false) {
      val eventName = it.getOrNull(0)
      actual.add(eventName)
    }

    session.commandExecutionManager.sendCommandToExecute("echo 1")
    session.commandExecutionManager.sendCommandToExecute("echo 2")
    session.commandExecutionManager.sendCommandToExecute("exit")

    session.terminalStarterFuture.get()!!.ttyConnector.waitFor()

    val expected = listOf(
      "command_history",
      "initialized",
      "prompt_state_updated",
      "command_started",
      "command_finished",
      "prompt_state_updated",
      "command_started",
      "command_finished",
      "prompt_state_updated",
      "command_started",
    )
    assertListEquals(expected, actual, ignoreUnexpectedEntriesOf = listOf("prompt_shown"))
  }

  @TestFor(issues = ["IJPL-101408", "IJPL-165429"], classes = [ShellEditorBufferReportShellCommandListener::class])
  @Test
  fun `shell shell editor buffer reporting`() {
    Assume.assumeFalse(SystemInfo.isWindows)

    val actual = mutableListOf<String?>()
    val session = startBlockTerminalSession(disableSavingHistory = false) {
      val eventName = it.getOrNull(0)
      actual.add(eventName)
    }

    assumeTrue(session.shellIntegration.shellType in listOf(ShellType.ZSH, ShellType.BASH))

    val completableFuture = CompletableFuture<String>()

    ShellEditorBufferReportShellCommandListener(session) { buffer ->
      completableFuture.complete(buffer)
    }
      .also { it -> session.commandManager.addListener(it, session) }

    session.terminalOutputStream.sendString("echo 1\nfoo", true)
    try {
      val buffer = completableFuture.get(10000, MILLISECONDS)
      assert(buffer != null)
    }
    catch (e: Exception) {
      fail("Buffer was not reported", e)
    }
  }

  @Test
  fun `basic hyperlinks are found`() {
    Assume.assumeFalse(SystemInfo.isWindows)

    ExtensionTestUtil.maskExtensions<ConsoleFilterProvider>(
      ConsoleFilterProvider.FILTER_PROVIDERS,
      listOf(ConsoleFilterProvider { arrayOf(MyHyperlinkFilter("foo")) }),
      disposableRule.disposable)

    val (session, view) = startSessionAndCreateView()
    val fooItem = SimpleTextRepeater.Item("foo", true, true, 5)
    val commandLine = SimpleTextRepeater.Helper.generateCommand(listOf(fooItem)).toCommandLine(session)

    view.sendCommandToExecute(commandLine)
    val outputModel = view.outputView.controller.outputModel
    awaitBlocksFinalized(outputModel, 1)
    val expected = listOf(CommandResult(commandLine, SimpleTextRepeater.Helper.getExpectedOutput(listOf(fooItem)).trimEnd()))
    val actual = view.outputView.controller.outputModel.collectCommandResults()
    Assert.assertEquals(expected, actual)

    val hyperlinkSupport = EditorHyperlinkSupport.get(outputModel.editor)
    hyperlinkSupport.waitForPendingFilters(10_000)
    val links = hyperlinkSupport.getAllHyperlinks(0, outputModel.editor.document.textLength)
    Assert.assertEquals(fooItem.count, links.size)
  }

  @Test
  fun `command with empty output`() {
    Assume.assumeFalse(SystemInfo.isWindows)

    val (session, view) = startSessionAndCreateView()
    val commandLine = listOf("cd", ".").toCommandLine(session)

    view.sendCommandToExecute(commandLine)
    val outputModel = view.outputView.controller.outputModel
    awaitBlocksFinalized(outputModel, 1)
    val expected = listOf(CommandResult(commandLine, ""))
    val actual = view.outputView.controller.outputModel.collectCommandResults()
    Assert.assertEquals(expected, actual)
  }

  private fun startSessionAndCreateView(): Pair<BlockTerminalSession, BlockTerminalView> {
    val session = startBlockTerminalSession()
    val view = BlockTerminalView(projectRule.project, session, JBTerminalSystemSettingsProvider(), TerminalTitle())
    Disposer.register(disposableRule.disposable, view)
    view.outputView.controller.finishCommandBlock(0) // emulate `initialized` event as it's consumed in `startBlockTerminalSession()`
    awaitFirstBlockFinalizedOrRemoved(view.outputView.controller.outputModel)
    return Pair(session, view)
  }

  private fun awaitFirstBlockFinalizedOrRemoved(outputModel: TerminalOutputModel, duration: Duration = 5.seconds) {
    val future = CompletableFuture<Unit>()
    outputModel.addListener(object : TerminalOutputModelListener {
      override fun blockFinalized(block: CommandBlock) {
        future.complete(Unit)
      }

      override fun blockRemoved(block: CommandBlock) {
        future.complete(Unit)
      }
    })
    val error = { "Timed out waiting for initial block finalized or removed" }
    PlatformTestUtil.waitWithEventsDispatching(error, { future.isDone }, duration.inWholeSeconds.toInt())
  }

  private fun awaitBlocksFinalized(outputModel: TerminalOutputModel, commandBlocks: Int, duration: Duration = 20.seconds) {
    val latch = CountDownLatch(commandBlocks)
    outputModel.addListener(object : TerminalOutputModelListener {
      override fun blockFinalized(block: CommandBlock) {
        if (block.withCommand) {
          latch.countDown()
        }
      }
    })
    val error = { "Timed out waiting for command blocks: finished: ${commandBlocks - latch.count}, expected: $commandBlocks" }
    PlatformTestUtil.waitWithEventsDispatching(error, { latch.count.toInt() == 0 }, duration.inWholeSeconds.toInt())
  }

  private fun startBlockTerminalSession(
    termSize: TermSize = TermSize(80, 24),
    disableSavingHistory: Boolean = true,
    terminalCustomCommandListener: TerminalCustomCommandListener = TerminalCustomCommandListener { }
  ) = TerminalSessionTestUtil.startBlockTerminalSession(
    projectRule.project,
    shellPath.toString(),
    disposableRule.disposable,
    termSize,
    disableSavingHistory,
    terminalCustomCommandListener
  )

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun shells(): List<Path> = TerminalSessionTestUtil.getShellPaths()

    fun <T> assertListEquals(
      expected: List<T>,
      actual: List<T>,
      equals: BiPredicate<T, T> = BiPredicate { a, b -> Objects.equals(a, b) },
    ) {
      if (expected.size != actual.size) failNotEquals("", expected, actual)
      if (expected.zip(actual).any { !equals.test(it.first, it.second) }) failNotEquals("", expected, actual)
    }
  }

}

private data class CommandResult(val command: String, val output: String)

private fun TerminalOutputModel.collectCommandResults(): List<CommandResult> {
  return blocks.withIndex().mapNotNull { (ind, block) ->
    check(block.isFinalized)
    val command = block.command
    if (ind == 0 && command == null) {
      null // skip the initial block
    }
    else {
      CommandResult(command!!, editor.document.getCommandBlockOutput(block))
    }
  }
}

internal fun Document.getCommandBlockOutput(block: CommandBlock): String {
  return if (block.withOutput) getText(TextRange(block.outputStartOffset, block.endOffset)) else ""
}

private open class MyHyperlinkFilter(val linkText: String) : Filter, DumbAware {

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val startInd = line.indexOf(linkText)
    if (startInd == -1) return null
    Thread.sleep(5) // emulate time-consuming hyperlink filter
    val startOffset = entireLength - line.length + startInd
    val endOffset = startOffset + linkText.length
    return Filter.Result(listOf(ResultItem(startOffset, endOffset, NopHyperlinkInfo)))
  }

  private object NopHyperlinkInfo : HyperlinkInfo {
    override fun navigate(project: Project) {}
  }
}

/**
 * Works almost as `assertEquals`.
 * Skips unexpected entries if they accidentally appear in [actual] and listed in [ignoreUnexpectedEntriesOf].
 */
private fun assertListEquals(expected: List<String?>, actual: List<String?>, ignoreUnexpectedEntriesOf: Collection<String>) {
  var actualIndex = 0
  var expectedIndex = 0
  while (actualIndex < actual.size && expectedIndex < expected.size) {
    if (expected[expectedIndex] == actual[actualIndex]) {
      expectedIndex++
      actualIndex++
    }
    else if (actual[actualIndex] in ignoreUnexpectedEntriesOf) {
      actualIndex++
    }
    else {
      failNotEquals(null, expected, actual)
    }
  }
  while (actualIndex < actual.size && actual[actualIndex] in ignoreUnexpectedEntriesOf) {
    actualIndex++
  }
  // fail if some list is not ingested yet
  if (actualIndex < actual.size || expectedIndex < expected.size) {
    failNotEquals(null, expected, actual)
  }
}
