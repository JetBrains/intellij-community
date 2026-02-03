package com.intellij.terminal.tests.reworked

import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.session.impl.TerminalBlocksModelState
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlockIdImpl
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalCommandBlockImpl
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalShellIntegrationImpl
import org.junit.Rule
import org.junit.Test

internal class TerminalOutputStatusRestoreTest {
  private val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(disposableRule)

  @Test
  fun `check WaitingForPrompt status is restored for block without command start offset`() {
    val block = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(0),
      startOffset = TerminalOffset.ZERO,
      endOffset = TerminalOffset.ZERO,
      commandStartOffset = null,
      outputStartOffset = null,
      workingDirectory = null,
      executedCommand = null,
      exitCode = null
    )
    doTest(block, expectedStatus = TerminalOutputStatus.WaitingForPrompt)
  }

  @Test
  fun `check TypingCommand status is restored for block with command start offset, but no output start offset`() {
    val block = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(0),
      startOffset = TerminalOffset.ZERO,
      endOffset = TerminalOffset.ZERO,
      commandStartOffset = TerminalOffset.ZERO,
      outputStartOffset = null,
      workingDirectory = null,
      executedCommand = null,
      exitCode = null
    )
    doTest(block, expectedStatus = TerminalOutputStatus.TypingCommand)
  }

  @Test
  fun `check ExecutingCommand status is restored for block with output start offset`() {
    val block = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(0),
      startOffset = TerminalOffset.ZERO,
      endOffset = TerminalOffset.ZERO,
      commandStartOffset = TerminalOffset.ZERO,
      outputStartOffset = TerminalOffset.ZERO,
      workingDirectory = null,
      executedCommand = "foo",
      exitCode = null
    )
    doTest(block, expectedStatus = TerminalOutputStatus.ExecutingCommand)
  }

  @Test
  fun `check WaitingForPrompt status is restored in strange case of block with output start offset only`() {
    val block = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(0),
      startOffset = TerminalOffset.ZERO,
      endOffset = TerminalOffset.ZERO,
      commandStartOffset = null,
      outputStartOffset = TerminalOffset.ZERO,
      workingDirectory = null,
      executedCommand = "foo",
      exitCode = null
    )
    doTest(block, expectedStatus = TerminalOutputStatus.WaitingForPrompt)
  }

  private fun doTest(block: TerminalCommandBlock, expectedStatus: TerminalOutputStatus) {
    val state = TerminalBlocksModelState(listOf(block), 1)
    val shellIntegration = createShellIntegration()
    shellIntegration.restoreFromState(state)

    assertThat(shellIntegration.outputStatus.value).isEqualTo(expectedStatus)
  }

  private fun createShellIntegration(): TerminalShellIntegrationImpl {
    val outputModel = TerminalTestUtil.createOutputModel()
    val sessionModel = TerminalSessionModelImpl()
    return TerminalShellIntegrationImpl(outputModel, sessionModel, disposableRule.disposable)
  }
}