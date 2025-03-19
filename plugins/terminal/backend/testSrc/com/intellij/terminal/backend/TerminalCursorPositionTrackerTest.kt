// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.intellij.terminal.backend.util.backendOutputTestFusActivity
import com.intellij.terminal.backend.util.startTestFusActivity
import com.intellij.terminal.backend.util.stopTestFusActivity
import com.intellij.terminal.backend.util.write
import com.intellij.terminal.session.TerminalContentUpdatedEvent
import com.intellij.terminal.session.TerminalCursorPositionChangedEvent
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class TerminalCursorPositionTrackerTest {
  @Before
  fun setUp() {
    startTestFusActivity()
  }

  @After
  fun tearDown() {
    stopTestFusActivity()
  }

  @Test
  fun `cursor position should correspond to existing line in the TextBuffer`() {
    val textBuffer = TerminalTextBuffer(10, 5, StyleState(), 3)
    val discardedHistoryTracker = TerminalDiscardedHistoryTracker(textBuffer)
    val terminalDisplay = TerminalDisplayImpl(DefaultSettingsProvider())
    val contentChangesTracker = TerminalContentChangesTracker(textBuffer, discardedHistoryTracker, backendOutputTestFusActivity!!)
    val cursorPositionTracker = TerminalCursorPositionTracker(textBuffer, discardedHistoryTracker, terminalDisplay)

    // Prepare
    textBuffer.write("prompt", 1, 0)
    terminalDisplay.setCursor(6, 1)
    contentChangesTracker.getContentUpdate()
    cursorPositionTracker.getCursorPositionUpdate()

    // Test: for example, the user pressed enter, and we move the cursor to the next line.
    backendOutputTestFusActivity!!.charProcessingStarted()
    terminalDisplay.setCursor(0, 2)
    backendOutputTestFusActivity!!.charProcessingFinished()

    val contentUpdate = contentChangesTracker.getContentUpdate() ?: error("Content update is null")
    val cursorUpdate = cursorPositionTracker.getCursorPositionUpdate() ?: error("Cursor update is null")

    // We expect that moving cursor position to the next line creates this line in the TextBuffer,
    // and it is being caught by the TerminalContentChangesTracker.
    // The strange 6/5 combination of character indices corresponds to the cursor position change without any characters read.
    assertThat(contentUpdate).isEqualTo(TerminalContentUpdatedEvent("", emptyList(), 1, 6L, 5L))
    assertThat(cursorUpdate).isEqualTo(TerminalCursorPositionChangedEvent(1, 0))
  }
}