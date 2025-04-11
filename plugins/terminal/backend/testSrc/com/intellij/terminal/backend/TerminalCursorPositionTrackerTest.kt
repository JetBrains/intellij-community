// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.intellij.terminal.backend.util.write
import com.intellij.terminal.session.TerminalCursorPositionChangedEvent
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
    assertThat(cursorUpdate).isEqualTo(TerminalCursorPositionChangedEvent(1, 0))
  }
}