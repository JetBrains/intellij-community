// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.terminal.TerminalTitle
import com.intellij.testFramework.*
import com.jediterm.core.util.TermSize
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.exp.BlockTerminalView
import org.jetbrains.plugins.terminal.exp.CommandBlock
import org.jetbrains.plugins.terminal.exp.TerminalOutputModel
import org.jetbrains.plugins.terminal.exp.util.TerminalSessionTestUtil
import org.jetbrains.plugins.terminal.exp.util.TerminalSessionTestUtil.toCommandLine
import org.jetbrains.plugins.terminal.exp.withCommand
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
class BlockTerminalCommandExecutionTest(private val shellPath: Path) {

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
    val session = startBlockTerminalSession()
    val view = BlockTerminalView(projectRule.project, session, JBTerminalSystemSettingsProvider(), TerminalTitle())
    Disposer.register(disposableRule.disposable, view)

    view.outputView.controller.finishCommandBlock(0) // emulate `initialized` event as it's consumed in `startBlockTerminalSession()`
    val count = 50
    val expected = (1..count).map {
      val message = "Hello, World $it"
      CommandResult(listOf("echo", message).toCommandLine(session), message)
    }
    expected.forEach {
      view.sendCommandToExecute(it.command)
    }
    awaitBlocksFinalized(view.outputView.controller.outputModel, count)
    val actual = view.outputView.controller.outputModel.collectCommandResults()
    Assert.assertEquals(expected, actual)
  }

  private fun awaitBlocksFinalized(outputModel: TerminalOutputModel, commandBlocks: Int, duration: Duration = 20.seconds) {
    val latch = CountDownLatch(commandBlocks)
    outputModel.addListener(object : TerminalOutputModel.TerminalOutputListener {
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
  return (0 until getBlocksSize()).mapNotNull {
    val commandBlock = this.getByIndex(it)
    check(commandBlock.isFinalized)
    val command = commandBlock.command
    if (it == 0 && command == null) {
      null // skip the initial block
    }
    else {
      CommandResult(command!!, editor.document.getText(TextRange(commandBlock.outputStartOffset, commandBlock.endOffset)))
    }
  }
}
