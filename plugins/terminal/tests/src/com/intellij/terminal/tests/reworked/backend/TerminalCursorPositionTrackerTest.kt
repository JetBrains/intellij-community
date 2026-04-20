// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.backend

import com.intellij.terminal.frontend.session.TerminalContentChangesTracker
import com.intellij.terminal.frontend.session.TerminalContentUpdate
import com.intellij.terminal.frontend.session.TerminalCursorPosition
import com.intellij.terminal.frontend.session.TerminalCursorPositionTracker
import com.intellij.terminal.frontend.session.TerminalDiscardedHistoryTracker
import com.intellij.terminal.frontend.session.TerminalDisplayImpl
import com.intellij.terminal.tests.reworked.util.write
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

internal class TerminalCursorPositionTrackerTest {
  @Test
  fun `cursor position should correspond to existing line in the TextBuffer`() {
    val textBuffer = TerminalTextBuffer(10, 5, StyleState(), 3)
    val discardedHistoryTracker = TerminalDiscardedHistoryTracker(textBuffer)
    val terminalDisplay = TerminalDisplayImpl(DefaultSettingsProvider())
    val contentChangesTracker = TerminalContentChangesTracker(textBuffer, discardedHistoryTracker)
    val cursorPositionTracker = TerminalCursorPositionTracker(textBuffer, discardedHistoryTracker, terminalDisplay)

    // Prepare
    textBuffer.write("prompt", 1, 0)
    terminalDisplay.setCursor(6, 1)
    contentChangesTracker.getContentUpdate()
    cursorPositionTracker.getCursorPositionUpdate()

    // Test: for example, the user pressed enter, and we move the cursor to the next line.
    terminalDisplay.setCursor(0, 2)

    val contentUpdate = contentChangesTracker.getContentUpdate() ?: error("Content update is null")
    val cursorUpdate = cursorPositionTracker.getCursorPositionUpdate() ?: error("Cursor update is null")

    assertThat(contentUpdate).isEqualTo(TerminalContentUpdate("", emptyList(), 1))
    assertThat(cursorUpdate).isEqualTo(TerminalCursorPosition(1, 0))
  }

  @Test
  fun `cursor position correctly reported when cursor is visible`() {
    val textBuffer = TerminalTextBuffer(10, 5, StyleState(), 3)
    val discardedHistoryTracker = TerminalDiscardedHistoryTracker(textBuffer)
    val terminalDisplay = TerminalDisplayImpl(DefaultSettingsProvider())
    val cursorPositionTracker = TerminalCursorPositionTracker(textBuffer, discardedHistoryTracker, terminalDisplay)

    textBuffer.write("hello", 1, 0)
    terminalDisplay.setCursor(5, 1)

    val cursorUpdate = cursorPositionTracker.getCursorPositionUpdate()
    assertThat(cursorUpdate).isEqualTo(TerminalCursorPosition(0, 5))
  }

  @Test
  fun `cursor position doesn't reported when cursor is not visible`() {
    val textBuffer = TerminalTextBuffer(10, 5, StyleState(), 3)
    val discardedHistoryTracker = TerminalDiscardedHistoryTracker(textBuffer)
    val terminalDisplay = TerminalDisplayImpl(DefaultSettingsProvider())
    val cursorPositionTracker = TerminalCursorPositionTracker(textBuffer, discardedHistoryTracker, terminalDisplay)

    terminalDisplay.setCursorVisible(false)

    textBuffer.write("hello", 1, 0)
    terminalDisplay.setCursor(5, 1)

    val cursorUpdate = cursorPositionTracker.getCursorPositionUpdate()
    assertThat(cursorUpdate).isNull()
  }

  @Test
  fun `cursor visible - reported, invisible - not reported, visible again - reported`() {
    val textBuffer = TerminalTextBuffer(10, 5, StyleState(), 3)
    val discardedHistoryTracker = TerminalDiscardedHistoryTracker(textBuffer)
    val terminalDisplay = TerminalDisplayImpl(DefaultSettingsProvider())
    val cursorPositionTracker = TerminalCursorPositionTracker(textBuffer, discardedHistoryTracker, terminalDisplay)

    // Cursor visible: position should be reported
    textBuffer.write("hello", 1, 0)
    terminalDisplay.setCursor(5, 1)
    assertThat(cursorPositionTracker.getCursorPositionUpdate())
      .isEqualTo(TerminalCursorPosition(0, 5))

    // Cursor invisible: position change should not be reported
    terminalDisplay.setCursorVisible(false)
    terminalDisplay.setCursor(3, 1)
    assertThat(cursorPositionTracker.getCursorPositionUpdate()).isNull()

    // Cursor visible again: current position should be reported
    terminalDisplay.setCursorVisible(true)
    assertThat(cursorPositionTracker.getCursorPositionUpdate())
      .isEqualTo(TerminalCursorPosition(0, 3))
  }
}