// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.intellij.terminal.backend.util.scrollDown
import com.intellij.terminal.backend.util.write
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
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
    assertEquals(0, update.startLineLogicalIndex)
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