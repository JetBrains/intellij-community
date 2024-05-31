// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.terminal.TerminalTitle
import com.intellij.testFramework.*
import com.jediterm.core.util.TermSize
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.block.testApps.SimpleTextRepeater
import org.jetbrains.plugins.terminal.exp.*
import org.jetbrains.plugins.terminal.exp.util.TerminalSessionTestUtil
import org.jetbrains.plugins.terminal.exp.util.TerminalSessionTestUtil.toCommandLine
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunsInEdt
@RunWith(Parameterized::class)
internal class BlockTerminalCommandExecutionTest(private val shellPath: Path) {

  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun shells(): List<Path> = TerminalSessionTestUtil.getShellPaths()
  }

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(EdtRule(), projectRule, disposableRule)

  @Test
  fun `commands are executed in order`() {
    val (session, view) = startSessionAndCreateView()
    val count = 50
    val expected = (1..count).map {
      val message = "Hello, World $it"
      CommandResult(listOf("echo", message).toCommandLine(session), message)
    }
    expected.forEach {
      session.commandExecutionManager.sendCommandToExecute(it.command)
    }
    awaitBlocksFinalized(view.outputView.controller.outputModel, count)
    val actual = view.outputView.controller.outputModel.collectCommandResults()
    Assert.assertEquals(expected, actual)
  }

  @Test
  fun `basic hyperlinks are found`() {
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
    return Pair(session, view)
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

  private fun startBlockTerminalSession(termSize: TermSize = TermSize(80, 24)) =
    TerminalSessionTestUtil.startBlockTerminalSession(projectRule.project, shellPath.toString(), disposableRule.disposable, termSize)

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
