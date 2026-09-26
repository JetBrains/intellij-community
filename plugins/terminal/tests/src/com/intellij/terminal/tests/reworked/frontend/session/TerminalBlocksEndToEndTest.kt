// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import com.intellij.terminal.tests.reworked.frontend.session.TerminalBlocksEndToEndTest.Companion.REPRO_ITERATION_TEXT
import com.intellij.terminal.tests.reworked.frontend.session.TerminalBlocksEndToEndTest.Companion.RESIZE_ITERATIONS
import com.intellij.terminal.tests.reworked.frontend.session.TerminalBlocksEndToEndTest.Companion.SETTLE_DELAY
import com.intellij.terminal.tests.reworked.frontend.session.TerminalBlocksEndToEndTest.Companion.printedText
import com.intellij.terminal.tests.reworked.util.ESC
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.text
import com.intellij.terminal.tests.reworked.util.TerminalViewFixture
import com.intellij.terminal.tests.reworked.util.TerminalViewTestCase
import com.intellij.terminal.tests.reworked.util.assertBlocksModelState
import com.intellij.terminal.tests.reworked.util.assertOutputModelState
import com.intellij.terminal.tests.reworked.util.commandFinishedOsc
import com.intellij.terminal.tests.reworked.util.commandStartedOsc
import com.intellij.terminal.tests.reworked.util.promptFinishedOsc
import com.intellij.terminal.tests.reworked.util.promptStartedOsc
import com.intellij.terminal.tests.reworked.util.shellIntegrationInitializedOsc
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlocksModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.getOutputText
import org.jetbrains.plugins.terminal.view.shellIntegration.getTypedCommandText
import org.jetbrains.plugins.terminal.view.shellIntegration.wasExecuted
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * States where the shell integration puts a block boundary, and so where the UI draws separators between executed commands.
 *
 * No shell-integration event carries a position of its own. The consumer reads
 * [TerminalOutputModel.cursorOffset] when it handles the event, see
 * [com.intellij.terminal.frontend.view.impl.TerminalShellIntegrationEventsHandler]. So the session must put the
 * event after the content update that describes the screen at the position of the OSC sequence. It must also put
 * the event before any content that follows.
 *
 * Every case inits the shell integration and asserts the resulting blocks. Most cases run twice, see
 * [doShellIntegrationTest]: once as one chunk of output, and once one chunk per output segment.
 * The single-chunk run is the contract under test because it gives the session no natural boundary to place the event at.
 */
internal class TerminalBlocksEndToEndTest(emulatorType: TerminalEmulatorType) : TerminalViewTestCase(emulatorType) {

  // ---------------------------------------------------------------------------
  // (1) The position of a single event
  // ---------------------------------------------------------------------------

  @Test
  fun `prompt_started marks the cursor before the prompt is printed`() = doShellIntegrationTest(
    segments = listOf(JUNK_LINE, promptStartedOsc(), PROMPT),
    expectedText = "junk\n$ ",
    expectedBlockCount = 2,
  ) { integration ->
    // The new block starts where the cursor was when the command arrived: after "junk\n", not after the prompt.
    assertThat(integration.block(0).endOffset).isEqualTo(integration.offset(5))
    assertThat(integration.block(1).startOffset).isEqualTo(integration.offset(5))
    assertThat(integration.block(1).endOffset).isEqualTo(integration.model.endOffset)
    assertThat(integration.block(1).commandStartOffset).isNull()
  }

  @Test
  fun `prompt_finished marks the end of the prompt`() = doShellIntegrationTest(
    segments = listOf(JUNK_LINE, promptStartedOsc(), PROMPT, promptFinishedOsc(), "pwd"),
    expectedText = "junk\n$ pwd",
    expectedBlockCount = 2,
    converged = { it.blocks.size == 2 && it.commandBlock(1).commandStartOffset != null },
  ) { integration ->
    assertThat(integration.block(1).startOffset).isEqualTo(integration.offset(5))
    assertThat(integration.block(1).commandStartOffset).isEqualTo(integration.offset(7))
    assertThat(integration.promptText(1)).isEqualTo(PROMPT)
    assertThat(integration.block(1).getTypedCommandText(integration.model)).isEqualTo("pwd")
    assertThat(integration.block(1).outputStartOffset).isNull()
  }

  @Test
  fun `command_started marks the start of the output`() = doShellIntegrationTest(
    segments = listOf(
      JUNK_LINE, promptStartedOsc(), PROMPT, promptFinishedOsc(), "pwd", "\r\n",
      commandStartedOsc("pwd"), "$WORKING_DIRECTORY\r\n",
    ),
    expectedText = "junk\n$ pwd\n$WORKING_DIRECTORY\n",
    expectedBlockCount = 2,
    converged = { it.blocks.size == 2 && it.commandBlock(1).outputStartOffset != null },
  ) { integration ->
    assertThat(integration.block(1).outputStartOffset).isEqualTo(integration.offset(11))
    assertThat(integration.block(1).executedCommand).isEqualTo("pwd")
    assertThat(integration.block(1).getOutputText(integration.model)).isEqualTo(WORKING_DIRECTORY)
    assertThat(integration.block(1).exitCode).isNull()
  }

  // ---------------------------------------------------------------------------
  // (2) The shape of a whole command
  // ---------------------------------------------------------------------------

  @Test
  fun `one command yields a finished block and a new active block`() = doShellIntegrationTest(
    segments = listOf(JUNK_LINE) +
               commandSegments("pwd", listOf(WORKING_DIRECTORY), exitCode = 2) +
               listOf(promptStartedOsc(), PROMPT),
    expectedText = "junk\n$ pwd\n$WORKING_DIRECTORY\n$ ",
    expectedBlockCount = 3,
  ) { integration ->
    // The output before the first prompt keeps its own block.
    assertThat(integration.block(0).startOffset).isEqualTo(integration.offset(0))
    assertThat(integration.block(0).endOffset).isEqualTo(integration.offset(5))
    assertThat(integration.block(0).commandStartOffset).isNull()
    assertThat(integration.block(0).executedCommand).isNull()

    val executed = integration.block(1)
    assertThat(executed.startOffset).isEqualTo(integration.offset(5))
    assertThat(executed.commandStartOffset).isEqualTo(integration.offset(7))
    assertThat(executed.outputStartOffset).isEqualTo(integration.offset(11))
    assertThat(executed.endOffset).isEqualTo(integration.offset(22))
    assertThat(integration.promptText(1)).isEqualTo(PROMPT)
    assertThat(executed.executedCommand).isEqualTo("pwd")
    assertThat(executed.getTypedCommandText(integration.model)).isEqualTo("pwd")
    assertThat(executed.getOutputText(integration.model)).isEqualTo(WORKING_DIRECTORY)
    // The exit code belongs to the block that ran the command, not to the new one.
    assertThat(executed.exitCode).isEqualTo(2)

    val active = integration.block(2)
    assertThat(active.startOffset).isEqualTo(integration.offset(22))
    assertThat(active.endOffset).isEqualTo(integration.model.endOffset)
    assertThat(active.commandStartOffset).isNull()
    assertThat(active.executedCommand).isNull()
    assertThat(active.exitCode).isNull()
  }

  @Test
  fun `three commands in one chunk keep their boundaries`() = doShellIntegrationTest(
    // The same command three times, with the whole shell integration script in one chunk.
    segments = (0 until REPRO_ITERATIONS).flatMap { commandSegments(REPRO_COMMAND, REPRO_OUTPUT_LINES) } +
               listOf(promptStartedOsc(), PROMPT),
    expectedText = REPRO_ITERATION_TEXT.repeat(REPRO_ITERATIONS) + PROMPT,
    expectedBlockCount = REPRO_ITERATIONS + 1,
  ) { integration ->
    for (index in 0 until REPRO_ITERATIONS) {
      val base = REPRO_ITERATION_LENGTH * index
      val block = integration.block(index)
      assertThat(block.startOffset).describedAs("start of block $index").isEqualTo(integration.offset(base))
      assertThat(block.commandStartOffset).describedAs("command start of block $index").isEqualTo(integration.offset(base + 2))
      assertThat(block.outputStartOffset).describedAs("output start of block $index").isEqualTo(integration.offset(base + 21))
      assertThat(block.endOffset).describedAs("end of block $index").isEqualTo(integration.offset(base + REPRO_ITERATION_LENGTH))
      assertThat(block.executedCommand).describedAs("command of block $index").isEqualTo(REPRO_COMMAND)
      assertThat(block.exitCode).describedAs("exit code of block $index").isEqualTo(0)
      assertThat(block.getOutputText(integration.model))
        .describedAs("output of block $index")
        .isEqualTo(REPRO_OUTPUT_LINES.joinToString("\n"))
    }

    val active = integration.block(REPRO_ITERATIONS)
    assertThat(active.startOffset).isEqualTo(integration.offset(REPRO_ITERATION_LENGTH * REPRO_ITERATIONS))
    assertThat(active.endOffset).isEqualTo(integration.model.endOffset)
    assertThat(active.commandStartOffset).isNull()
  }

  @Test
  fun `a command with no output reports outputStartOffset equal to endOffset`() = doShellIntegrationTest(
    segments = commandSegments("true", emptyList()) + listOf(promptStartedOsc(), PROMPT),
    expectedText = "$ true\n$ ",
    // The first prompt starts at the start offset of the initial block, so it replaces that block
    // instead of splitting it, see TerminalBlocksModelImpl.startNewBlock.
    expectedBlockCount = 2,
  ) { integration ->
    val executed = integration.block(0)
    assertThat(executed.startOffset).isEqualTo(integration.offset(0))
    assertThat(executed.commandStartOffset).isEqualTo(integration.offset(2))
    assertThat(executed.outputStartOffset).isEqualTo(integration.offset(7))
    assertThat(executed.endOffset).isEqualTo(integration.offset(7))
    assertThat(executed.wasExecuted).isTrue()
    assertThat(executed.getOutputText(integration.model)).isEmpty()
    assertThat(executed.exitCode).isEqualTo(0)

    assertThat(integration.block(1).startOffset).isEqualTo(integration.offset(7))
    assertThat(integration.block(1).endOffset).isEqualTo(integration.model.endOffset)
  }

  // ---------------------------------------------------------------------------
  // (3) The forced projection beside the rest of the pipeline
  // ---------------------------------------------------------------------------

  @Test
  fun `output taller than the screen keeps the boundaries`() {
    // 30 output lines on the 24-row grid, so the screen scrolls while the command runs.
    val outputLines = (0 until 30).map { "L%02d".format(it) }
    doShellIntegrationTest(
      segments = commandSegments("seq", outputLines) + listOf(promptStartedOsc(), PROMPT),
      expectedText = "$ seq\n" + outputLines.joinToString("") { "$it\n" } + PROMPT,
      expectedBlockCount = 2,
    ) { integration ->
      val executed = integration.block(0)
      assertThat(executed.startOffset).isEqualTo(integration.offset(0))
      assertThat(executed.commandStartOffset).isEqualTo(integration.offset(2))
      assertThat(executed.outputStartOffset).isEqualTo(integration.offset(6))
      assertThat(executed.endOffset).isEqualTo(integration.offset(126))
      assertThat(executed.getOutputText(integration.model)).isEqualTo(outputLines.joinToString("\n"))

      assertThat(integration.block(1).startOffset).isEqualTo(integration.offset(126))
      assertThat(integration.block(1).endOffset).isEqualTo(integration.model.endOffset)
    }
  }

  @Test
  fun `a redrawn prompt drops the stale blocks`() = doShellIntegrationTest(
    segments = listOf(JUNK_LINE) +
               commandSegments("pwd", listOf(WORKING_DIRECTORY)) +
               // The shell prints a prompt, then redraws over it: it puts the cursor on the second row and
               // erases from there to the end of the screen. Nothing scrolled, so this drops every block
               // after "junk\n".
               listOf(promptStartedOsc(), PROMPT, "$ESC[2;1H$ESC[J", promptStartedOsc(), "> "),
    expectedText = "junk\n> ",
    expectedBlockCount = 2,
    // The erase alone already leaves two blocks. Only the prompt after it replaces the second one with a
    // block that has no command.
    converged = { it.blocks.size == 2 && it.commandBlock(1).commandStartOffset == null },
  ) { integration ->
    assertThat(integration.block(0).startOffset).isEqualTo(integration.offset(0))
    assertThat(integration.block(0).endOffset).isEqualTo(integration.offset(5))
    assertThat(integration.block(0).commandStartOffset).isNull()

    val active = integration.block(1)
    assertThat(active.startOffset).isEqualTo(integration.offset(5))
    assertThat(active.endOffset).isEqualTo(integration.model.endOffset)
    assertThat(active.executedCommand).isNull()
    assertThat(active.exitCode).isNull()
  }

  @Test
  fun `a resize between the command and its output keeps the boundaries`() = doTest { fixture ->
    val integration = fixture.initShellIntegration()
    val outputLine = LONG_OUTPUT_LINE

    // Stop right after the first output, so the resize lands inside the running command.
    fixture.connector.feed(
      promptStartedOsc() + PROMPT + promptFinishedOsc() + "pwd" + "\r\n" + commandStartedOsc("pwd") + outputLine
    )
    fixture.assertPrintedText(integration, "$ pwd\n$outputLine")

    // 50 characters no longer fit one row, so the reflow moves the text under the recorded offsets.
    fixture.resize(columns = 40, rows = 24)

    fixture.connector.feed("\r\n" + commandFinishedOsc(0, WORKING_DIRECTORY) + promptStartedOsc() + PROMPT)
    fixture.assertPrintedText(integration, "$ pwd\n$outputLine\n$ ")
    fixture.assertBlocksModelState(integration.blocks) { it.blocks.size == 2 }

    val executed = integration.block(0)
    assertThat(executed.startOffset).isEqualTo(integration.offset(0))
    assertThat(executed.commandStartOffset).isEqualTo(integration.offset(2))
    assertThat(executed.outputStartOffset).isEqualTo(integration.offset(6))
    assertThat(executed.endOffset).isEqualTo(integration.offset(57))
    assertThat(executed.executedCommand).isEqualTo("pwd")
    assertThat(executed.exitCode).isEqualTo(0)
    assertThat(executed.getOutputText(integration.model)).isEqualTo(outputLine)

    assertThat(integration.block(1).startOffset).isEqualTo(integration.offset(57))
    assertThat(integration.block(1).endOffset).isEqualTo(integration.model.endOffset)
  }

  // ---------------------------------------------------------------------------
  // (4) A resize, which reflows the rows under the recorded offsets
  // ---------------------------------------------------------------------------
  //
  // A reflow moves content between rows, but a soft wrap is not a line end, so the document text never
  // changes. Every block offset must therefore hold across any resize. Ghostty only: it reports a resize
  // from its own reflow, where JediTerm needs a following write, so the two cannot share expectations.

  @Test
  fun `a height shrink keeps the block boundaries`() = doResizeTest { fixture ->
    fixture.resizeAndAwait(columns = 80, rows = 5)
  }

  @Test
  fun `a height growth that recovers scrollback keeps the block boundaries`() = doResizeTest { fixture ->
    fixture.resizeAndAwait(columns = 80, rows = 5)
    fixture.resizeAndAwait(columns = 80, rows = 40)
  }

  @Test
  fun `a width shrink keeps the block boundaries`() = doResizeTest { fixture ->
    fixture.resizeAndAwait(columns = 40, rows = 24)
  }

  @Test
  fun `a width growth keeps the block boundaries`() = doResizeTest { fixture ->
    fixture.resizeAndAwait(columns = 200, rows = 24)
  }

  @Test
  fun `a shrink in both dimensions keeps the block boundaries`() = doResizeTest { fixture ->
    fixture.resizeAndAwait(columns = 40, rows = 10)
  }

  @Test
  fun `a growth in both dimensions keeps the block boundaries`() = doResizeTest { fixture ->
    fixture.resizeAndAwait(columns = 200, rows = 40)
  }

  @Test
  fun `several resizes keep the block boundaries`() = doResizeTest { fixture ->
    fixture.resizeAndAwait(columns = 40, rows = 10)
    fixture.resizeAndAwait(columns = 200, rows = 40)
    fixture.resizeAndAwait(columns = 80, rows = 24)
  }

  @Test
  fun `a resize on the alternate screen keeps the block boundaries`() {
    assumeGhostty()
    doTest { fixture ->
      val integration = fixture.feedResizeTestData()

      fixture.connector.feed("$ESC[?1049h")
      fixture.view.sessionModel.terminalState.first { it.isAlternateScreenBuffer }
      fixture.resizeAndAwait(columns = 40, rows = 10)

      // The blocks belong to the regular output model alone, so a resize behind a full-screen program
      // must not touch them. See TerminalTextBufferEventsTest for what the switch does to the models.
      assertResizeBlocks(integration, "after a resize on the alternate screen")
    }
  }

  @Test
  fun `output after a resize joins the active block`() {
    assumeGhostty()
    doTest { fixture ->
      val integration = fixture.feedResizeTestData()

      fixture.resizeAndAwait(columns = 40, rows = 10)
      fixture.connector.feed("tail")
      fixture.assertOutputModelState(integration.model) { it.text.endsWith("tail") }

      // The finished blocks keep their offsets, and the text after the resize extends the active block.
      assertResizeBlocks(integration, "after the resize and the following write")
      assertThat(integration.block(RESIZE_ITERATIONS).getTypedCommandText(integration.model)).isNull()
    }
  }

  @Test
  fun `a resize on the alternate screen keeps the block boundaries after the program exits`() {
    assumeGhostty()
    doTest { fixture ->
      val integration = fixture.feedResizeTestData()

      fixture.connector.feed("$ESC[?1049h")
      fixture.view.sessionModel.terminalState.first { it.isAlternateScreenBuffer }
      fixture.resizeAndAwait(columns = 40, rows = 10)
      fixture.connector.feed("$ESC[?1049l")
      fixture.view.sessionModel.terminalState.first { !it.isAlternateScreenBuffer }

      // The primary screen was reflowed while nothing was reading it, so the projector reports that
      // reflow in one catch-up update when the program exits. A reflow adds no text, so the update must
      // land past every finished block. `a resize on the alternate screen keeps the block boundaries`
      // covers the same resize while the program is still running; this covers the way back.
      fixture.assertBlocksModelState(integration.blocks) { it.blocks.size == RESIZE_ITERATIONS + 1 }
      assertResizeBlocks(integration, "after leaving the alternate screen")
    }
  }

  @Disabled(
    "KNOWN GAP: a width shrink reports more finalized rows than HISTORY_REPLACE_LINES, because " +
    "the history mark measures how far the visible screen expanded, not how much output arrived. The " +
    "projector then replaces the history and anchors at 0, so the blocks model trims every finished " +
    "block: five collapse into one, which also inherits the last command. The text itself survives. " +
    "TerminalEmulatorOutputProjectorTest pins the replacement itself. Enable this once a width shrink " +
    "keeps the anchor."
  )
  @Test
  fun `a width shrink past the replace threshold keeps the finished blocks`() {
    assumeGhostty()
    doTest { fixture ->
      val integration = fixture.initShellIntegration()
      // A 50-row screen of 200-character lines expands to 1250 rows at 8 columns. The history mark
      // follows the old screen top, so it reports 1200 rows finalized, above HISTORY_REPLACE_LINES.
      fixture.resizeAndAwait(columns = 200, rows = 50)
      // Four commands of 21 rows each fill the 50-row screen, which the count depends on: it measures how
      // far the visible screen expands, not how much history exists.
      val wide = List(20) { "W%02d".format(it).padEnd(200, '-') }
      fixture.connector.feed(
        (List(4) { commandSegments("wide", wide) }.flatten() + listOf(promptStartedOsc(), PROMPT))
          .joinToString("")
      )
      fixture.assertBlocksModelState(integration.blocks) { it.blocks.size == 5 }

      fixture.resizeAndAwait(columns = 8, rows = 50)

      // A width shrink adds no output, so the text and all five blocks must survive.
      assertThat(integration.model.text).contains("W00", "W19")
      assertThat(integration.blocks.blocks).hasSize(5)
      for (index in 0 until 4) {
        assertThat(integration.block(index).executedCommand)
          .describedAs("command of block $index after the width shrink")
          .isEqualTo("wide")
      }
    }
  }

  // ---------------------------------------------------------------------------
  // (5) An acknowledged gap: a replaced history collapses the blocks
  // ---------------------------------------------------------------------------

  @Test
  fun `a burst past the replace threshold collapses the finished blocks`() {
    assumeGhostty()
    doTest { fixture ->
      val integration = fixture.initShellIntegration()
      fixture.connector.feed(
        (List(3) { commandSegments("echo hi", listOf("out")) }.flatten() + listOf(promptStartedOsc(), PROMPT))
          .joinToString("")
      )
      fixture.assertBlocksModelState(integration.blocks) { it.blocks.size == 4 }

      // One write that finalizes far more than HISTORY_REPLACE_LINES rows: the projector gives up on the
      // history and reports the screen alone at index 0.
      fixture.connector.feed("\r\n" + (0 until 2_000).joinToString("\r\n") { "burst-$it" })
      fixture.assertOutputModelState(integration.model) { it.text.contains("burst-1999") }

      // Every finished block is gone: one block is left, and it carries the command of the last one that
      // was overwritten.
      fixture.assertBlocksModelState(integration.blocks) { it.blocks.size == 1 }
      assertThat(integration.block(0).executedCommand)
        .describedAs("the surviving block inherits the overwritten command")
        .isEqualTo("echo hi")
    }
  }

  @Test
  fun `clear after several commands collapses the finished blocks`() {
    assumeGhostty()
    doTest { fixture ->
      val integration = fixture.initShellIntegration()
      fixture.connector.feed(
        (List(3) { commandSegments("echo hi", listOf("out")) }.flatten() + listOf(promptStartedOsc(), PROMPT))
          .joinToString("")
      )
      fixture.assertBlocksModelState(integration.blocks) { it.blocks.size == 4 }

      // ED2 plus the E3 extension (erase scrollback), what `clear` sends.
      fixture.connector.feed("$ESC[2J$ESC[3J$ESC[H")
      fixture.assertOutputModelState(integration.model) { it.text.isBlank() }

      // The whole history is gone, so this update is anchored at logical index 0 too - the same outcome
      // as the burst above for the blocks model, though the projector never treats this as a replacement.
      fixture.assertBlocksModelState(integration.blocks) { it.blocks.size == 1 }
      assertThat(integration.model.text)
        .describedAs("no stale command or output may survive clear")
        .doesNotContain("echo hi", "out")
    }
  }

  @Test
  fun `Terminal ClearBuffer after several commands collapses the finished blocks`() {
    assumeGhostty()
    @OptIn(LowLevelLocalMachineAccess::class)
    Assumptions.assumeTrue(OS.CURRENT != OS.Windows, "Terminal.ClearBuffer is disabled on Windows")

    doTest { fixture ->
      val integration = fixture.initShellIntegration()
      fixture.connector.feed(
        (List(3) { commandSegments("echo hi", listOf("out")) }.flatten() + listOf(promptStartedOsc(), PROMPT))
          .joinToString("")
      )
      fixture.assertBlocksModelState(integration.blocks) { it.blocks.size == 4 }

      fixture.invokeAction("Terminal.ClearBuffer")
      fixture.assertOutputModelState(integration.model) { it.text.isBlank() }

      fixture.assertBlocksModelState(integration.blocks) { it.blocks.size == 1 }
      assertThat(integration.model.text)
        .describedAs("no stale command or output may survive Terminal.ClearBuffer")
        .doesNotContain("echo hi", "out")
    }
  }

  // Trimming is not covered here. It needs the output model to evict while the emulator still holds the
  // text, and GhosttyTerminalSession derives its scrollback cap from the same setting, so the two caps move
  // together. TerminalBlocksModelTest states that contract directly on the models instead.

  // ---------------------------------------------------------------------------
  // Harness
  // ---------------------------------------------------------------------------

  /**
   * Feeds [segments] and asserts the same result twice, once per [FeedMode].
   *
   * [FeedMode.PER_SEGMENT] runs first, as the control. It waits [SETTLE_DELAY] after every segment, which is
   * longer than the polling interval of the Ghostty session. So every segment gets its own projection, and the
   * order is correct by construction. A failure here means the expected values are wrong.
   *
   * [FeedMode.ONE_CHUNK] runs second, as the contract under test. The OSC sequence and the text after it reach
   * the emulator in one write, so the session must place the event between them itself. A failure only here
   * means the session put the event at the wrong position.
   *
   * [expectedText] is the whole expected text. Trailing whitespace is ignored, see [printedText].
   *
   * [converged] must describe the final state. The default one uses [expectedBlockCount],
   * which fits every case that ends with a new prompt.
   */
  private fun doShellIntegrationTest(
    segments: List<String>,
    expectedText: String,
    expectedBlockCount: Int,
    converged: (TerminalBlocksModel) -> Boolean = { it.blocks.size == expectedBlockCount },
    assertintegration: (ShellIntegration) -> Unit,
  ) {
    for (feedMode in FeedMode.entries) {
      try {
        doTest { fixture ->
          val integration = fixture.initShellIntegration()
          fixture.feed(segments, feedMode)

          fixture.assertPrintedText(integration, expectedText)
          fixture.assertBlocksModelState(integration.blocks, condition = converged)
          assertThat(integration.blocks.blocks).hasSize(expectedBlockCount)
          assertintegration(integration)
        }
      }
      catch (error: AssertionError) {
        throw AssertionError("$feedMode: ${error.message}", error)
      }
    }
  }

  /**
   * Enables the shell integration and returns the state the case asserts against.
   */
  private suspend fun TerminalViewFixture.initShellIntegration(): ShellIntegration {
    // Blocks exist only for the regular output model, so the case must not read the active one.
    val model = view.outputModels.regular
    connector.feed(shellIntegrationInitializedOsc(WORKING_DIRECTORY))
    val blocks = view.shellIntegrationDeferred.await().blocksModel
    return ShellIntegration(model, blocks, model.endOffset)
  }

  private suspend fun TerminalViewFixture.feed(segments: List<String>, feedMode: FeedMode) {
    when (feedMode) {
      FeedMode.ONE_CHUNK -> connector.feed(segments.joinToString(""))
      FeedMode.PER_SEGMENT -> for (segment in segments) {
        connector.feed(segment)
        delay(SETTLE_DELAY)
      }
    }
  }

  private suspend fun TerminalViewFixture.assertPrintedText(integration: ShellIntegration, expectedText: String) {
    assertOutputModelState(integration.model) { it.printedText(integration.origin) == expectedText.trimEnd() }
  }

  /** The order matters: [doShellIntegrationTest] runs the control before the contract under test. */
  private enum class FeedMode { PER_SEGMENT, ONE_CHUNK }

  // ---------------------------------------------------------------------------
  // The resize harness
  // ---------------------------------------------------------------------------

  /**
   * Feeds [RESIZE_ITERATIONS] commands whose output is taller than the screen, so the earliest rows reach
   * the scrollback, then asserts the blocks, runs [resize] and asserts the same blocks again.
   */
  private fun doResizeTest(resize: suspend (fixture: TerminalViewFixture) -> Unit) {
    assumeGhostty()
    doTest { fixture ->
      val integration = fixture.feedResizeTestData()
      assertResizeBlocks(integration, "before the resize")

      resize(fixture)

      assertResizeBlocks(integration, "after the resize")
    }
  }

  /** Feeds the resize test data and returns the state once every block is in place. */
  private suspend fun TerminalViewFixture.feedResizeTestData(): ShellIntegration {
    val integration = initShellIntegration()
    resize(columns = 80, rows = 24)
    connector.feed(
      ((0 until RESIZE_ITERATIONS).flatMap { commandSegments(RESIZE_COMMAND, RESIZE_OUTPUT_LINES) } +
       listOf(promptStartedOsc(), PROMPT)).joinToString("")
    )
    assertBlocksModelState(integration.blocks) { it.blocks.size == RESIZE_ITERATIONS + 1 }
    return integration
  }

  /** Asserts every block of the resize case. [phase] names the moment, so a failure says which one. */
  private fun assertResizeBlocks(integration: ShellIntegration, phase: String) {
    assertThat(integration.blocks.blocks).describedAs("block count $phase").hasSize(RESIZE_ITERATIONS + 1)
    for (index in 0 until RESIZE_ITERATIONS) {
      val base = RESIZE_ITERATION_LENGTH * index
      val block = integration.block(index)
      assertThat(block.startOffset).describedAs("start of block $index $phase").isEqualTo(integration.offset(base))
      assertThat(block.commandStartOffset)
        .describedAs("command start of block $index $phase").isEqualTo(integration.offset(base + 2))
      assertThat(block.outputStartOffset)
        .describedAs("output start of block $index $phase").isEqualTo(integration.offset(base + 10))
      assertThat(block.endOffset)
        .describedAs("end of block $index $phase").isEqualTo(integration.offset(base + RESIZE_ITERATION_LENGTH))
      assertThat(block.executedCommand).describedAs("command of block $index $phase").isEqualTo(RESIZE_COMMAND)
      assertThat(block.exitCode).describedAs("exit code of block $index $phase").isEqualTo(0)
      assertThat(block.getOutputText(integration.model))
        .describedAs("output of block $index $phase").isEqualTo(RESIZE_OUTPUT_LINES.joinToString("\n"))
    }

    val active = integration.block(RESIZE_ITERATIONS)
    assertThat(active.startOffset)
      .describedAs("start of the active block $phase")
      .isEqualTo(integration.offset(RESIZE_ITERATION_LENGTH * RESIZE_ITERATIONS))
    assertThat(active.endOffset)
      .describedAs("end of the active block $phase").isEqualTo(integration.model.endOffset)
  }

  companion object {
    /** The prompt every script prints. Two characters, so the command starts two characters into the block. */
    private const val PROMPT = "$ "

    /** The working directory the shell integration reports. */
    private const val WORKING_DIRECTORY = "/home/user"

    /**
     * A line printed before the first prompt, so the first block boundary lands at a non-zero offset.
     * That keeps the first prompt from replacing the initial block, see TerminalBlocksModelImpl.startNewBlock.
     */
    private const val JUNK_LINE = "junk\r\n"

    private const val REPRO_COMMAND = "java -version; pwd"

    private val REPRO_OUTPUT_LINES = listOf("openjdk 21", WORKING_DIRECTORY)

    private const val REPRO_ITERATIONS = 3

    /** What one repro iteration prints. */
    private const val REPRO_ITERATION_TEXT = "$ java -version; pwd\nopenjdk 21\n/home/user\n"

    /** The length of [REPRO_ITERATION_TEXT], and so the distance between two repro block boundaries. */
    private const val REPRO_ITERATION_LENGTH = 43L

    /**
     * How long [FeedMode.PER_SEGMENT] waits after a segment. Longer than the polling interval of the Ghostty
     * session, so the session projects every segment on its own.
     */
    private val SETTLE_DELAY: Duration = 100.milliseconds

    /**
     * The segments a shell prints for one command: the prompt, the typed command, the output, and the exit code.
     * A segment ends where the next shell-integration command starts.
     */
    private fun commandSegments(command: String, outputLines: List<String>, exitCode: Int = 0): List<String> =
      buildList {
        add(promptStartedOsc())
        add(PROMPT)
        add(promptFinishedOsc())
        add(command)
        add("\r\n")
        add(commandStartedOsc(command))
        if (outputLines.isNotEmpty()) {
          add(outputLines.joinToString("") { "$it\r\n" })
        }
        add(commandFinishedOsc(exitCode, WORKING_DIRECTORY))
      }

    /** The command the resize case runs. Seven characters, so its output starts ten characters in. */
    private const val RESIZE_COMMAND = "echo hi"

    /**
     * The output of one resize case command: ten 100-character lines. They soft-wrap at 80 columns and
     * re-wrap at every other width the resize cases use, so each case really reflows.
     */
    private val RESIZE_OUTPUT_LINES: List<String> = List(10) { "O$it".padEnd(100, '-') }

    /** How many commands the resize case runs. Their output is taller than the 24-row screen. */
    private const val RESIZE_ITERATIONS = 3

    /**
     * The characters one resize case command prints: `"$ echo hi\n"` is 10, and each of the ten output
     * lines is 101 with its line end.
     */
    private const val RESIZE_ITERATION_LENGTH = 1020L

    /** A 50-character line of repeating letters. It fits an 80-column row, but reflows onto two 40-column rows. */
    private val LONG_OUTPUT_LINE: String = (0 until 50).map { 'a' + it % 26 }.joinToString("")

    /**
     * Everything printed: the text from [origin] to the end, without trailing whitespace.
     *
     * The emulators differ on the trailing whitespace, so no case may depend on it. JediTerm keeps the rows an
     * erase blanked as empty lines, and Ghostty drops them. The trailing space of the last prompt behaves the
     * same way.
     */
    private fun TerminalOutputModel.printedText(origin: TerminalOffset): String =
      getText(origin.coerceAtLeast(startOffset), endOffset).toString().trimEnd()

    private fun TerminalBlocksModel.commandBlock(index: Int): TerminalCommandBlock =
      blocks[index] as TerminalCommandBlock
  }

  /** The blocks a case asserts, the output model they index into, and the offset the script started at. */
  private class ShellIntegration(
    val model: TerminalOutputModel,
    val blocks: TerminalBlocksModel,
    val origin: TerminalOffset,
  ) {
    /** The block at [index]. Every block in this suite is a command block. */
    fun block(index: Int): TerminalCommandBlock = blocks.blocks[index] as TerminalCommandBlock

    /** [origin] plus [chars]: the form every offset expectation in this suite is written in. */
    fun offset(chars: Long): TerminalOffset = origin + chars

    /** The prompt of the block at [index], that is, its text before the command starts. */
    fun promptText(index: Int): String {
      val block = block(index)
      return model.getText(block.startOffset, block.commandStartOffset!!).toString()
    }
  }
}
