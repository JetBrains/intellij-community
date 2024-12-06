// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.reworked

import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import junit.framework.TestCase.assertEquals
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalContentChangesTracker
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalDiscardedHistoryTracker
import org.junit.Test

internal class TerminalContentChangesTrackerTest {
  @Test
  fun `test writing`() {
    val textBuffer = createTextBuffer(width = 10, height = 5, maxHistoryLinesCount = 3)
    val contentChangesTracker = createChangesTracker(textBuffer)

    textBuffer.write("12345", 1, 0)
    textBuffer.write("67890", 2, 0)
    textBuffer.write("ABCDE", 3, 0)

    val update = contentChangesTracker.getContentUpdate() ?: error("Update is null")
    val expectedText = """
      12345
      67890
      ABCDE
    """.trimIndent()
    assertEquals(expectedText, update.text)
    assertEquals(0, update.startLineLogicalIndex)
  }

  @Test
  fun `test history overflow with empty history`() {
    val textBuffer = createTextBuffer(width = 10, height = 3, maxHistoryLinesCount = 3)
    val contentChangesTracker = createChangesTracker(textBuffer)

    // prepare
    textBuffer.write("first", 1, 0)
    contentChangesTracker.getContentUpdate()

    // test
    textBuffer.write("second", 2, 0)
    textBuffer.write("third", 3, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fourth", 3, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fifth", 3, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("sixth", 3, 0)

    // Update will be saved there on the next scroll
    var update: TerminalContentUpdatedEvent? = null
    contentChangesTracker.addHistoryOverflowListener {
      update = it
    }

    textBuffer.scrollDown(2)

    update ?: error("Update is null")
    val expectedText = """
      first
      second
      third
      fourth
      fifth
      sixth
    """.trimIndent()
    assertEquals(expectedText, update.text)
    assertEquals(0, update.startLineLogicalIndex)
  }

  @Test
  fun `test history overflow with not empty history`() {
    val textBuffer = createTextBuffer(width = 10, height = 3, maxHistoryLinesCount = 3)
    val contentChangesTracker = createChangesTracker(textBuffer)

    // prepare
    textBuffer.write("first", 1, 0)
    textBuffer.scrollDown(1)
    contentChangesTracker.getContentUpdate()

    // test
    textBuffer.write("second", 1, 0)
    textBuffer.write("third", 2, 0)
    textBuffer.write("fourth", 3, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fifth", 3, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("sixth", 3, 0)

    // Update will be saved there on the next scroll
    var update: TerminalContentUpdatedEvent? = null
    contentChangesTracker.addHistoryOverflowListener {
      update = it
    }

    textBuffer.scrollDown(2)

    update ?: error("Update is null")
    val expectedText = """
      second
      third
      fourth
      fifth
      sixth
    """.trimIndent()
    assertEquals(expectedText, update.text)
    assertEquals(1, update.startLineLogicalIndex)
  }

  @Suppress("SameParameterValue")
  private fun createTextBuffer(width: Int, height: Int, maxHistoryLinesCount: Int): TerminalTextBuffer {
    return TerminalTextBuffer(width, height, StyleState(), maxHistoryLinesCount, null)
  }

  private fun createChangesTracker(textBuffer: TerminalTextBuffer): TerminalContentChangesTracker {
    val discardedHistoryTracker = TerminalDiscardedHistoryTracker(textBuffer)
    return TerminalContentChangesTracker(textBuffer, discardedHistoryTracker)
  }
}