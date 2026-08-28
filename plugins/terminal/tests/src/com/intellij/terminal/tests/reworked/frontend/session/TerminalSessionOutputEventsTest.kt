// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import com.intellij.terminal.tests.reworked.util.BEL
import com.intellij.terminal.tests.reworked.util.ESC
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.terminal.tests.reworked.util.TerminalOutputEventCollector
import com.intellij.terminal.tests.reworked.util.aliasesReceivedOsc
import com.intellij.terminal.tests.reworked.util.awaitEvent
import com.intellij.terminal.tests.reworked.util.awaitEventAfter
import com.intellij.terminal.tests.reworked.util.awaitStateBefore
import com.intellij.terminal.tests.reworked.util.commandFinishedOsc
import com.intellij.terminal.tests.reworked.util.commandStartedOsc
import com.intellij.terminal.tests.reworked.util.completionFinishedOsc
import com.intellij.terminal.tests.reworked.util.encodeShellIntegrationValue
import com.intellij.terminal.tests.reworked.util.indexOfEvent
import com.intellij.terminal.tests.reworked.util.promptFinishedOsc
import com.intellij.terminal.tests.reworked.util.promptStartedOsc
import com.intellij.terminal.tests.reworked.util.shellIntegrationInitializedOsc
import com.intellij.terminal.tests.reworked.util.shellIntegrationOsc
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import org.jetbrains.plugins.terminal.session.impl.TerminalAliasesReceivedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalBeepEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandHistoryPathReceivedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCompletionFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalResizeEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalShellIntegrationEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.jetbrains.plugins.terminal.session.impl.dto.MouseFormatDto
import org.jetbrains.plugins.terminal.session.impl.dto.MouseModeDto
import org.jetbrains.plugins.terminal.session.impl.dto.CursorShapeDto
import org.jetbrains.plugins.terminal.session.impl.dto.TextStyleOptionDto
import org.junit.Test

/**
 * Tests the terminal output pipeline end-to-end without a real shell: ANSI/VT sequences are written to a
 * [LoopbackTtyConnector] and the resulting [TerminalOutputEvent]s are asserted on [TerminalSession.getOutputFlow].
 *
 * Every case runs on both VT emulators — see [TerminalSessionTestCase].
 */
internal class TerminalSessionOutputEventsTest(emulatorType: TerminalEmulatorType) : TerminalSessionTestCase(emulatorType) {

  @Test
  fun `plain text produces content updated event with cursor position`() = runSessionTest { _, connector, collector ->
    connector.feed("hello")

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("hello") }
    assertThat(event.cursorLogicalLineIndex).isEqualTo(0L)
    assertThat(event.cursorColumnIndex).isEqualTo(5)
    assertThat(collector.documentText()).isEqualTo("hello")
  }

  @Test
  fun `SGR color sequence produces content update with styles`() = runSessionTest { _, connector, collector ->
    connector.feed("${ESC}[31mRED${ESC}[0m")

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("RED") }
    assertThat(event.styles)
      .describedAs("Colored text should produce style ranges")
      .isNotEmpty()
    assertThat(event.styles.any { it.style.foreground != null })
      .describedAs("The colored run should carry a foreground color")
      .isTrue()
  }

  @Test
  fun `cursor movement is reported`() = runSessionTest { _, connector, collector ->
    connector.feed("hello")
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("hello") }

    // Move the cursor within the existing line (row 1, column 3 in 1-based VT coordinates) without changing text.
    connector.feed("${ESC}[1;3H")

    // Depending on the emulator the move is reported either as a standalone cursor event or folded into a content
    // update; accept either, as long as the cursor lands at logical line 0, column 2.
    collector.awaitEvent<TerminalOutputEvent> { event ->
      (event is TerminalCursorPositionChangedEvent && event.logicalLineIndex == 0L && event.columnIndex == 2) ||
      (event is TerminalContentUpdatedEvent && event.cursorLogicalLineIndex == 0L && event.cursorColumnIndex == 2)
    }
  }

  @Test
  fun `hiding and showing the cursor produces state changed events`() = runSessionTest { _, connector, collector ->
    connector.feed("${ESC}[?25l")
    val hidden = collector.awaitEvent<TerminalStateChangedEvent> { !it.state.isCursorVisible }
    assertThat(hidden.state.isCursorVisible).isFalse()

    connector.feed("${ESC}[?25h")
    val shown = collector.awaitEvent<TerminalStateChangedEvent> { it.state.isCursorVisible }
    assertThat(shown.state.isCursorVisible).isTrue()
  }

  @Test
  fun `OSC window title sequence produces state changed event`() = runSessionTest { _, connector, collector ->
    connector.feed("${ESC}]0;My Title${BEL}")

    val event = collector.awaitEvent<TerminalStateChangedEvent> { it.state.windowTitle == "My Title" }
    assertThat(event.state.windowTitle).isEqualTo("My Title")
  }

  @Test
  fun `entering and leaving the alternate screen buffer produces state changed events`() = runSessionTest { _, connector, collector ->
    connector.feed("${ESC}[?1049h")
    val entered = collector.awaitEvent<TerminalStateChangedEvent> { it.state.isAlternateScreenBuffer }
    assertThat(entered.state.isAlternateScreenBuffer).isTrue()

    connector.feed("${ESC}[?1049l")
    val left = collector.awaitEvent<TerminalStateChangedEvent> { !it.state.isAlternateScreenBuffer }
    assertThat(left.state.isAlternateScreenBuffer).isFalse()
  }

  @Test
  fun `bell character produces beep event`() = runSessionTest { _, connector, collector ->
    connector.feed(BEL)
    collector.awaitEvent<TerminalBeepEvent>()
  }

  @Test
  fun `synchronous output is reported as content updated event`() = runSessionTest { _, connector, collector ->
    // Multi-line output wrapped in a DEC 2026 synchronized-update block ("synchronous output"):
    // the whole batch is applied atomically and must still be reported once the block ends.
    connector.feed("${ESC}[?2026h")
    connector.feed("line1\r\nline2\r\nline3")
    connector.feed("${ESC}[?2026l")

    // The cursor ends on the third logical line (index 2), which proves all three lines were applied.
    val event = collector.awaitEvent<TerminalContentUpdatedEvent> {
      it.cursorLogicalLineIndex == 2L && it.cursorColumnIndex == 5
    }
    assertThat(event.text).contains("line3")
    assertThat(collector.documentLines()).isEqualTo(listOf("line1", "line2", "line3"))
  }

  @Test
  fun `alternate screen buffer isolates content from the primary buffer`() = runSessionTest { _, connector, collector ->
    connector.feed("MAIN")
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("MAIN") }

    // Switch to the alternate screen buffer and write content there.
    connector.feed("${ESC}[?1049h")
    collector.awaitEvent<TerminalStateChangedEvent> { it.state.isAlternateScreenBuffer }
    connector.feed("ALT")

    val altContent = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("ALT") }
    assertThat(altContent.text)
      .describedAs("Alternate buffer must not contain the primary buffer content")
      .doesNotContain("MAIN")
    assertThat(collector.alternateBufferText()).contains("ALT").doesNotContain("MAIN")
    assertThat(collector.documentText().trimEnd())
      .describedAs("The alternate-buffer content must not leak into the primary document")
      .isEqualTo("MAIN")

    // Switch back to the primary buffer.
    connector.feed("${ESC}[?1049l")
    val left = collector.awaitEvent<TerminalStateChangedEvent> { !it.state.isAlternateScreenBuffer }
    assertThat(left.state.isAlternateScreenBuffer).isFalse()
    assertThat(collector.documentText().trimEnd()).isEqualTo("MAIN")
  }

  @Test
  fun `a buffer switch and drawing within one chunk keep the buffers isolated`() = runSessionTest { _, connector, collector ->
    connector.feed("MAIN")
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("MAIN") }

    // The switch, cursor homing, and the first alternate frame arrive as one chunk, so they can land in a
    // single projected batch — the state change must still be applied before the content it precedes, or the
    // frame is routed into the primary document. (Homing matters: mode 1049 carries the cursor position over,
    // so without it the frame would start at MAIN's end column.)
    connector.feed("${ESC}[?1049h${ESC}[HALT-FRAME")
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("ALT-FRAME") }

    assertThat(collector.alternateBufferText().trimEnd()).isEqualTo("ALT-FRAME")
    assertThat(collector.documentText().trimEnd())
      .describedAs("the alternate-screen frame leaked into the primary document")
      .isEqualTo("MAIN")
  }

  // ---------------------------------------------------------------------------
  // Shell integration: where a command lands in the event stream
  // ---------------------------------------------------------------------------

  @Test
  fun `prompt_started reports the cursor before the prompt is printed`() = runSessionTest { _, connector, collector ->
    enableShellIntegration(connector, collector)

    connector.feed(JUNK_LINE + promptStartedOsc() + PROMPT)

    val state = collector.awaitStateBefore<TerminalPromptStartedEvent>()
    assertThat(state.cursorOffset)
      .describedAs("the block must start after \"junk\\n\" and before the prompt text, but was %s", state)
      .isEqualTo(5)
    assertThat(state.text).isEqualTo("junk\n")
  }

  @Test
  fun `prompt_finished reports the cursor between the prompt and the typed command`() = runSessionTest { _, connector, collector ->
    enableShellIntegration(connector, collector)

    connector.feed(JUNK_LINE + promptStartedOsc() + PROMPT + promptFinishedOsc() + COMMAND)

    val state = collector.awaitStateBefore<TerminalPromptFinishedEvent>()
    assertThat(state.cursorOffset)
      .describedAs("the typed command must start after \"junk\\n$ \", but was %s", state)
      .isEqualTo(7)
    assertThat(state.text).isEqualTo("junk\n$PROMPT")
  }

  @Test
  fun `command_started reports the cursor at the start of the output`() = runSessionTest { _, connector, collector ->
    enableShellIntegration(connector, collector)

    connector.feed(
      JUNK_LINE + promptStartedOsc() + PROMPT + promptFinishedOsc() + COMMAND + "\r\n" +
      commandStartedOsc(COMMAND) + WORKING_DIRECTORY
    )

    val state = collector.awaitStateBefore<TerminalCommandStartedEvent>()
    assertThat(state.cursorOffset)
      .describedAs("the output must start after \"junk\\n$ pwd\\n\" and before the first output line, but was %s", state)
      .isEqualTo(11)
    assertThat(state.text).isEqualTo("junk\n$PROMPT$COMMAND\n")
  }

  @Test
  fun `the commands of one chunk arrive in order, once each`() = runSessionTest { _, connector, collector ->
    enableShellIntegration(connector, collector)
    val mark = collector.currentEventCount()

    connector.feed(
      promptStartedOsc() + PROMPT + promptFinishedOsc() + COMMAND + "\r\n" +
      commandStartedOsc(COMMAND) + WORKING_DIRECTORY + "\r\n" +
      commandFinishedOsc(exitCode = 3, workingDirectory = WORKING_DIRECTORY)
    )

    // `command_finished` closes the script, so its arrival proves the whole chunk was processed.
    collector.awaitEvent<TerminalCommandFinishedEvent>()

    assertThat(collector.eventsSince(mark).filterIsInstance<TerminalShellIntegrationEvent>())
      .describedAs("the order, the multiplicity and the payload of every command in the chunk")
      .containsExactly(
        TerminalPromptStartedEvent,
        TerminalPromptFinishedEvent,
        TerminalCommandStartedEvent(COMMAND),
        TerminalCommandFinishedEvent(COMMAND, 3, WORKING_DIRECTORY),
      )
  }

  @Test
  fun `command_finished with no started command reports nothing`() = runSessionTest { _, connector, collector ->
    enableShellIntegration(connector, collector)
    val mark = collector.currentEventCount()

    // `prompt_started` follows in the same chunk, so its arrival proves `command_finished` was processed.
    connector.feed(commandFinishedOsc(exitCode = 0, workingDirectory = WORKING_DIRECTORY) + promptStartedOsc())

    collector.awaitEvent<TerminalPromptStartedEvent>()
    assertThat(collector.eventsSince(mark).filterIsInstance<TerminalShellIntegrationEvent>())
      .describedAs("`command_finished` has no command to report, so it must report nothing")
      .containsExactly(TerminalPromptStartedEvent)
  }

  @Test
  fun `initialized is reported before the first command that needs a position`() = runSessionTest { _, connector, collector ->
    // One chunk, and no priming: the frontend creates the shell integration from the session state, and it
    // discards a positional command that arrives before that state.
    connector.feed(shellIntegrationInitializedOsc(WORKING_DIRECTORY) + promptStartedOsc())

    collector.awaitEvent<TerminalPromptStartedEvent>()
    val enabledIndex = collector.indexOfEvent<TerminalStateChangedEvent> { it.state.isShellIntegrationEnabled }
    assertThat(enabledIndex).describedAs("no state reported the shell integration as enabled").isNotNegative()
    assertThat(enabledIndex)
      .describedAs("the state that enables the shell integration must precede the first positional command")
      .isLessThan(collector.indexOfEvent<TerminalPromptStartedEvent>())
  }

  // ---------------------------------------------------------------------------
  // Shell integration: what a command reports
  // ---------------------------------------------------------------------------

  @Test
  fun `initialized enables the shell integration and reports the history path`() = runSessionTest { _, connector, collector ->
    connector.feed(shellIntegrationOsc(
      "initialized;current_directory=${WORKING_DIRECTORY.encodeShellIntegrationValue()}" +
      ";history_path=${HISTORY_PATH.encodeShellIntegrationValue()}"
    ))

    val state = collector.awaitEvent<TerminalStateChangedEvent> { it.state.isShellIntegrationEnabled }
    assertThat(state.state.isShellIntegrationEnabled).isTrue()
    assertThat(collector.awaitEvent<TerminalCommandHistoryPathReceivedEvent>().path).isEqualTo(HISTORY_PATH)
  }

  @Test
  fun `initialized without a history path reports a null path`() = runSessionTest { _, connector, collector ->
    connector.feed(shellIntegrationInitializedOsc(WORKING_DIRECTORY))

    collector.awaitEvent<TerminalStateChangedEvent> { it.state.isShellIntegrationEnabled }
    assertThat(collector.awaitEvent<TerminalCommandHistoryPathReceivedEvent>().path).isNull()
  }

  @Test
  fun `aliases and completion results are decoded`() = runSessionTest { _, connector, collector ->
    enableShellIntegration(connector, collector)

    // Neither command marks a boundary: each one reports a value the frontend stores, so neither needs a
    // position, and one case covers both.
    connector.feed(aliasesReceivedOsc(ALIASES))
    assertThat(collector.awaitEvent<TerminalAliasesReceivedEvent>().aliasesRaw).isEqualTo(ALIASES)

    connector.feed(completionFinishedOsc(COMPLETION_RESULT))
    assertThat(collector.awaitEvent<TerminalCompletionFinishedEvent>().result).isEqualTo(COMPLETION_RESULT)
  }

  // ---------------------------------------------------------------------------
  // Group B: content editing / rendering
  // ---------------------------------------------------------------------------

  @Test
  fun `carriage return overwrites the current line`() = runSessionTest { _, connector, collector ->
    // '\r' returns the cursor to column 0; "XY" then overwrites "ab", leaving "XYc".
    connector.feed("abc\rXY")

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("XYc") }
    assertThat(event.text).contains("XYc")
    assertThat(collector.documentText()).isEqualTo("XYc")
  }

  @Test
  fun `erase in line removes content after the cursor`() = runSessionTest { _, connector, collector ->
    connector.feed("ABCDEF")
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("ABCDEF") }

    // Move the cursor to column 4 (1-based, CHA) and erase from there to the end of the line (ESC [ K).
    connector.feed("${ESC}[4G${ESC}[K")

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("ABC") && !it.text.contains("DEF") }
    assertThat(event.text).doesNotContain("DEF")
    // trimEnd: the erased cells may or may not be reported as trailing spaces, depending on the emulator.
    assertThat(collector.documentText().trimEnd()).isEqualTo("ABC")
  }

  @Test
  fun `output wider than the terminal is fully reported`() = runSessionTest { _, connector, collector ->
    // The terminal is 80 columns wide; the line wraps onto a second row, but stays one logical line.
    connector.feed("a".repeat(85))

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.count { c -> c == 'a' } == 85 }
    assertThat(event.text.count { it == 'a' }).isEqualTo(85)
    assertThat(collector.documentLines()).containsExactly("a".repeat(85))
  }

  @Test
  fun `output beyond the screen height scrolls and advances logical line indices`() = runSessionTest { _, connector, collector ->
    // The terminal is 24 rows tall; 30 lines push the top lines above the visible screen.
    connector.feed((0 until 30).joinToString("\r\n") { "line$it" })

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> { it.cursorLogicalLineIndex == 29L }
    assertThat(event.cursorLogicalLineIndex).isEqualTo(29L)
    assertThat(event.cursorColumnIndex).isEqualTo("line29".length)
    assertThat(collector.documentLines()).isEqualTo((0 until 30).map { "line$it" })
  }

  // ---------------------------------------------------------------------------
  // Group A: terminal modes -> TerminalStateChangedEvent
  // ---------------------------------------------------------------------------

  @Test
  fun `cursor shape sequence updates state`() = runSessionTest { _, connector, collector ->
    // DECSCUSR: ESC [ 4 SP q -> steady underline.
    connector.feed("${ESC}[4 q")

    val event = collector.awaitEvent<TerminalStateChangedEvent> { it.state.cursorShape == CursorShapeDto.STEADY_UNDERLINE }
    assertThat(event.state.cursorShape).isEqualTo(CursorShapeDto.STEADY_UNDERLINE)
  }

  @Test
  fun `mouse reporting mode and format update state`() = runSessionTest { _, connector, collector ->
    connector.feed("${ESC}[?1000h")
    val mode = collector.awaitEvent<TerminalStateChangedEvent> { it.state.mouseMode == MouseModeDto.MOUSE_REPORTING_NORMAL }
    assertThat(mode.state.mouseMode).isEqualTo(MouseModeDto.MOUSE_REPORTING_NORMAL)

    connector.feed("${ESC}[?1006h")
    val format = collector.awaitEvent<TerminalStateChangedEvent> { it.state.mouseFormat == MouseFormatDto.MOUSE_FORMAT_SGR }
    assertThat(format.state.mouseFormat).isEqualTo(MouseFormatDto.MOUSE_FORMAT_SGR)
  }

  @Test
  fun `bracketed paste mode updates state`() = runSessionTest { _, connector, collector ->
    connector.feed("${ESC}[?2004h")

    val event = collector.awaitEvent<TerminalStateChangedEvent> { it.state.isBracketedPasteMode }
    assertThat(event.state.isBracketedPasteMode).isTrue()
  }

  @Test
  fun `application cursor keys and keypad update state`() = runSessionTest { _, connector, collector ->
    // DECCKM: application cursor keys.
    connector.feed("${ESC}[?1h")
    val arrowKeys = collector.awaitEvent<TerminalStateChangedEvent> { it.state.isApplicationArrowKeys }
    assertThat(arrowKeys.state.isApplicationArrowKeys).isTrue()

    // DECKPAM: application keypad.
    connector.feed("${ESC}=")
    val keypad = collector.awaitEvent<TerminalStateChangedEvent> { it.state.isApplicationKeypad }
    assertThat(keypad.state.isApplicationKeypad).isTrue()
  }

  // ---------------------------------------------------------------------------
  // Group C: rich styles -> TerminalContentUpdatedEvent.styles
  // ---------------------------------------------------------------------------

  @Test
  fun `SGR attributes are decoded into the text style`() = runSessionTest { _, connector, collector ->
    // Bold (1), red foreground (31), green background (42).
    connector.feed("${ESC}[1;31;42mSTYLED${ESC}[0m")

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("STYLED") }
    val style = event.styles.first { it.style.foreground?.colorIndex == 1 }.style
    assertThat(style.background?.colorIndex).isEqualTo(2)
    assertThat(style.options).contains(TextStyleOptionDto.BOLD)
  }

  @Test
  fun `256-color foreground is decoded`() = runSessionTest { _, connector, collector ->
    connector.feed("${ESC}[38;5;208mX${ESC}[0m")

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("X") }
    val foreground = event.styles.first { it.style.foreground != null }.style.foreground!!
    // Both emulators resolve a 256-palette color to a concrete RGB: JediTerm eagerly in the emulator, the
    // Ghostty session by resolving the emulator's palette index against the live palette while building
    // the event. (Only the 16 ANSI colors ship as indices, for theme-aware rendering.)
    assertThat(foreground.rgb)
      .describedAs("256-palette color should be resolved to a concrete rgb, was $foreground")
      .isNotNull()
  }

  @Test
  fun `true-color foreground is decoded as an rgb color`() = runSessionTest { _, connector, collector ->
    val expectedRgb = (10 shl 16) or (20 shl 8) or 30
    connector.feed("${ESC}[38;2;10;20;30mX${ESC}[0m")

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("X") }
    val foreground = event.styles.first { it.style.foreground?.rgb != null }.style.foreground
    // rgb may carry an opaque alpha byte, so compare only the RGB channels.
    assertThat(foreground?.rgb?.and(0xFFFFFF)).isEqualTo(expectedRgb)
    assertThat(foreground?.colorIndex).isNull()
  }

  @Test
  fun `adjacent SGR colors produce separate style ranges`() = runSessionTest { _, connector, collector ->
    // Red (31) "RED" immediately followed by green (32) "GRN".
    connector.feed("${ESC}[31mRED${ESC}[32mGRN${ESC}[0m")

    val event = collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("REDGRN") }
    assertThat(event.styles.map { it.style.foreground?.colorIndex }).contains(1, 2)
  }

  // ---------------------------------------------------------------------------
  // Resize (driven via the input channel)
  // ---------------------------------------------------------------------------

  @Test
  fun `resizing re-reports the buffer content`() = runSessionTest { session, connector, collector ->
    connector.feed("hello")
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("hello") }

    // A resize re-reports the (unchanged) buffer content. Skip the events collected before the resize,
    // because that earlier "hello" content event looks identical to the re-reported one.
    val skip = collector.currentEventCount()
    session.getInputChannel().send(TerminalResizeEvent(TerminalGridSize(columns = 40, rows = 10)))

    val event = collector.awaitEventAfter<TerminalContentUpdatedEvent>(skip) { it.text.contains("hello") }
    assertThat(event.text).contains("hello")
  }

  @Test
  fun `resizing preserves multiline content`() = runSessionTest { session, connector, collector ->
    connector.feed("first\r\nsecond\r\nthird")
    collector.awaitEvent<TerminalContentUpdatedEvent> { it.text.contains("third") }

    // Resize to a smaller grid; every line must still be present in the re-reported content.
    val skip = collector.currentEventCount()
    session.getInputChannel().send(TerminalResizeEvent(TerminalGridSize(columns = 20, rows = 10)))

    val event = collector.awaitEventAfter<TerminalContentUpdatedEvent>(skip) { it.text.contains("third") }
    assertThat(event.text).contains("first").contains("second").contains("third")
    // The re-report must leave the document with each line exactly once — neither dropped nor duplicated.
    assertThat(collector.documentLines().map { it.trimEnd() }.filter { it.isNotEmpty() })
      .containsExactly("first", "second", "third")
  }

}

/** The prompt the shell-integration cases print. Two characters, so an expected offset stays readable. */
private const val PROMPT = "$ "

/** Output printed before a prompt, so a position case fails visibly when the event lands at offset 0. */
private const val JUNK_LINE = "junk\r\n"

private const val COMMAND = "pwd"
private const val WORKING_DIRECTORY = "/home/user"
private const val HISTORY_PATH = "/home/user/.bash_history"
private const val ALIASES = "alias ll='ls -l'"
private const val COMPLETION_RESULT = "completion-result"

/**
 * Enables the shell integration and returns once the session reported both of the events that `initialized`
 * produces. Every positional command needs the integration enabled first. Awaiting both events also keeps a
 * later [TerminalOutputEventCollector.currentEventCount] mark free of this step's own events.
 */
private suspend fun enableShellIntegration(connector: LoopbackTtyConnector, collector: TerminalOutputEventCollector) {
  connector.feed(shellIntegrationInitializedOsc(WORKING_DIRECTORY))
  collector.awaitEvent<TerminalStateChangedEvent> { it.state.isShellIntegrationEnabled }
  collector.awaitEvent<TerminalCommandHistoryPathReceivedEvent>()
}
