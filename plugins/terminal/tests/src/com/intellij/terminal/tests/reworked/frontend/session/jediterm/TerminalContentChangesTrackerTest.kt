// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.jediterm

import com.intellij.terminal.frontend.session.jediterm.TerminalContentChangesTracker
import com.intellij.terminal.frontend.session.jediterm.TerminalContentUpdate
import com.intellij.terminal.frontend.session.jediterm.TerminalDiscardedHistoryTracker
import com.intellij.terminal.tests.reworked.util.scrollDown
import com.intellij.terminal.tests.reworked.util.write
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jediterm.core.util.CellPosition
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import junit.framework.TestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalContentChangesTrackerTest : BasePlatformTestCase() {
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
    TestCase.assertEquals(0, update.startLineLogicalIndex)
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
    var update: TerminalContentUpdate? = null
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
    var update: TerminalContentUpdate? = null
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

  @Test
  fun `test clearing history`() {
    val textBuffer = createTextBuffer(width = 10, height = 2, maxHistoryLinesCount = 2)
    val contentChangesTracker = createChangesTracker(textBuffer)

    // Prepare
    textBuffer.write("first", 1, 0)
    textBuffer.write("second", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("third", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fourth", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fifth", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("sixth", 2, 0)
    contentChangesTracker.getContentUpdate()

    // Test
    textBuffer.clearHistory()
    val update = contentChangesTracker.getContentUpdate() ?: error("Update is null")

    val expectedText = """
      fifth
      sixth
    """.trimIndent()
    assertEquals(expectedText, update.text)
    TestCase.assertEquals(0, update.startLineLogicalIndex)
  }

  @Test
  fun `test clearing history and screen`() {
    val textBuffer = createTextBuffer(width = 10, height = 2, maxHistoryLinesCount = 2)
    val contentChangesTracker = createChangesTracker(textBuffer)

    // Prepare
    textBuffer.write("first", 1, 0)
    textBuffer.write("second", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("third", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fourth", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fifth", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("sixth", 2, 0)
    contentChangesTracker.getContentUpdate()

    // Test
    textBuffer.clearScreenAndHistoryBuffers()
    textBuffer.write("newFirst", 1, 0)
    val update = contentChangesTracker.getContentUpdate() ?: error("Update is null")

    assertEquals("newFirst", update.text)
    TestCase.assertEquals(0, update.startLineLogicalIndex)
  }

  @Test
  fun `screen top is at line 0 while nothing has scrolled`() {
    val textBuffer = createTextBuffer(width = 10, height = 2, maxHistoryLinesCount = 2)
    val contentChangesTracker = createChangesTracker(textBuffer)

    textBuffer.write("first", 1, 0)

    val update = contentChangesTracker.getContentUpdate() ?: error("Update is null")
    TestCase.assertEquals(0L, update.screenTopLogicalLineIndex)
    TestCase.assertEquals(0, update.screenTopColumnIndex)
  }

  @Test
  fun `screen top counts the lines that scrolled into the history`() {
    val textBuffer = createTextBuffer(width = 10, height = 2, maxHistoryLinesCount = 2)
    val contentChangesTracker = createChangesTracker(textBuffer)

    textBuffer.write("first", 1, 0)
    textBuffer.write("second", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("third", 2, 0)
    textBuffer.scrollDown(1)

    // "first" and "second" are in the history, so the screen starts at the third logical line.
    val update = contentChangesTracker.getContentUpdate() ?: error("Update is null")
    TestCase.assertEquals(2L, update.screenTopLogicalLineIndex)
    TestCase.assertEquals(0, update.screenTopColumnIndex)
  }

  @Test
  fun `screen top counts the lines already discarded from the history`() {
    val textBuffer = createTextBuffer(width = 10, height = 2, maxHistoryLinesCount = 2)
    val contentChangesTracker = createChangesTracker(textBuffer)

    // Six lines through a 2-line history: "first" and "second" are discarded, "third" and "fourth" retained.
    textBuffer.write("first", 1, 0)
    textBuffer.write("second", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("third", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fourth", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fifth", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("sixth", 2, 0)

    // The screen holds "fifth" and "sixth", so it starts at the fifth logical line, index 4.
    val update = contentChangesTracker.getContentUpdate() ?: error("Update is null")
    TestCase.assertEquals(4L, update.screenTopLogicalLineIndex)
    TestCase.assertEquals(0, update.screenTopColumnIndex)
  }

  @Test
  fun `screen top reports a column when a wrapped line straddles it`() {
    val textBuffer = createTextBuffer(width = 5, height = 2, maxHistoryLinesCount = 2)
    val contentChangesTracker = createChangesTracker(textBuffer)

    // One logical line "aaaaabb" wrapped over two rows, then its first row scrolls into the history. The
    // screen now begins in the middle of that line, five characters in.
    textBuffer.write("aaaaa", 1, 0)
    textBuffer.getLine(0).isWrapped = true
    textBuffer.write("bb", 2, 0)
    textBuffer.scrollDown(1)

    val update = contentChangesTracker.getContentUpdate() ?: error("Update is null")
    TestCase.assertEquals(0L, update.screenTopLogicalLineIndex)
    TestCase.assertEquals(5, update.screenTopColumnIndex)
  }

  @Test
  fun `clearing the history resets the screen top to 0`() {
    val textBuffer = createTextBuffer(width = 10, height = 2, maxHistoryLinesCount = 2)
    val contentChangesTracker = createChangesTracker(textBuffer)

    textBuffer.write("first", 1, 0)
    textBuffer.write("second", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("third", 2, 0)
    textBuffer.scrollDown(1)
    contentChangesTracker.getContentUpdate()

    textBuffer.clearHistory()

    val update = contentChangesTracker.getContentUpdate() ?: error("Update is null")
    TestCase.assertEquals(0L, update.screenTopLogicalLineIndex)
    TestCase.assertEquals(0, update.screenTopColumnIndex)
  }

  @Test
  fun `check all text is reported after width increase`() {
    val textBuffer = createTextBuffer(width = 10, height = 2, maxHistoryLinesCount = 2)
    val contentChangesTracker = createChangesTracker(textBuffer)

    // Prepare
    textBuffer.write("first", 1, 0)
    textBuffer.write("second", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("third", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fourth", 2, 0)
    contentChangesTracker.getContentUpdate()

    textBuffer.resize(TermSize(12, 2), CellPosition(6, 2), null)
    val update = contentChangesTracker.getContentUpdate() ?: error("Update is null")

    val expectedText = """
      first
      second
      third
      fourth
    """.trimIndent()
    assertEquals(expectedText, update.text)
    assertEquals(0, update.startLineLogicalIndex)
  }

  @Test
  fun `check all text is reported after width decrease`() {
    val textBuffer = createTextBuffer(width = 10, height = 2, maxHistoryLinesCount = 2)
    val contentChangesTracker = createChangesTracker(textBuffer)

    // Prepare
    textBuffer.write("first", 1, 0)
    textBuffer.write("second", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("third", 2, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fourth", 2, 0)
    contentChangesTracker.getContentUpdate()

    textBuffer.resize(TermSize(5, 2), CellPosition(6, 2), null)
    val update = contentChangesTracker.getContentUpdate() ?: error("Update is null")

    val expectedText = """
      d
      third
      fourth
    """.trimIndent()
    assertEquals(expectedText, update.text)
    assertEquals(0, update.startLineLogicalIndex)
  }

  @Test
  fun `check update is flushed on history overflow after height decrease`() {
    val textBuffer = createTextBuffer(width = 10, height = 3, maxHistoryLinesCount = 2)
    val contentChangesTracker = createChangesTracker(textBuffer)

    var update: TerminalContentUpdate? = null
    contentChangesTracker.addHistoryOverflowListener {
      update = it
    }

    textBuffer.write("first", 1, 0)
    textBuffer.write("second", 2, 0)
    textBuffer.write("third", 3, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fourth", 3, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fifth", 3, 0)

    textBuffer.resize(TermSize(10, 2), CellPosition(5, 3), null)

    update ?: error("Update is null")

    val expectedText = """
      first
      second
      third
      fourth
      fifth
    """.trimIndent()
    assertEquals(expectedText, update.text)
    assertEquals(0, update.startLineLogicalIndex)
  }

  @Test
  fun `check update is flushed on history overflow after width and height resize`() {
    val textBuffer = createTextBuffer(width = 10, height = 3, maxHistoryLinesCount = 2)
    val contentChangesTracker = createChangesTracker(textBuffer)

    // Prepare
    textBuffer.write("first", 1, 0)
    textBuffer.write("second", 2, 0)
    textBuffer.write("third", 3, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fourth", 3, 0)
    textBuffer.scrollDown(1)
    textBuffer.write("fifth", 3, 0)
    contentChangesTracker.getContentUpdate()

    var update: TerminalContentUpdate? = null
    contentChangesTracker.addHistoryOverflowListener {
      update = it
    }

    // Edit width first
    textBuffer.resize(TermSize(12, 3), CellPosition(5, 3), null)
    // Then edit height
    textBuffer.resize(TermSize(12, 2), CellPosition(5, 3), null)

    update ?: error("Update is null")

    val expectedText = """
      first
      second
      third
      fourth
      fifth
    """.trimIndent()
    assertEquals(expectedText, update.text)
    assertEquals(0, update.startLineLogicalIndex)
  }

  @Suppress("SameParameterValue")
  private fun createTextBuffer(width: Int, height: Int, maxHistoryLinesCount: Int): TerminalTextBuffer {
    return TerminalTextBuffer(width, height, StyleState(), maxHistoryLinesCount)
  }

  private fun createChangesTracker(textBuffer: TerminalTextBuffer): TerminalContentChangesTracker {
    val discardedHistoryTracker = TerminalDiscardedHistoryTracker(textBuffer)
    return TerminalContentChangesTracker(textBuffer, discardedHistoryTracker)
  }
}