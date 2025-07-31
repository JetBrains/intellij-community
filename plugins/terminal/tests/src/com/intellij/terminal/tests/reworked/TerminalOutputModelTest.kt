// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked

import com.intellij.openapi.application.EDT
import com.intellij.terminal.session.StyleRange
import com.intellij.terminal.session.TerminalOutputModelState
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.restore
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.update
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.updateCursor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jediterm.terminal.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot
import org.jetbrains.plugins.terminal.block.output.TextStyleAdapter
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.block.ui.BlockTerminalColorPalette
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalOutputModelTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `update editor content`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()

    val text = """
      123 sdfsdf sdfsdf
      234234234324 dsfsdfsdfsdf
      2342341 adfasfasfa asdsdasd
      
      asdasdas
      
    """.trimIndent()

    model.update(0, text, emptyList())

    assertEquals(text, model.document.text)
  }

  @Test
  fun `update editor content incrementally with styles`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()

    val text1 = """
      first line
      second line
    """.trimIndent()
    val styles1 = listOf(styleRange(0, 5), styleRange(11, 17), styleRange(18, 22))

    val text2 = """
      replaced second line
      third line
    """.trimIndent()
    val styles2 = listOf(styleRange(0, 8), styleRange(9, 15), styleRange(21, 26))

    model.update(0, text1, styles1)
    model.update(1, text2, styles2)

    val expectedText = """
      first line
      replaced second line
      third line
    """.trimIndent()
    val expectedHighlightings = listOf(highlighting(0, 5), highlighting(11, 19), highlighting(20, 26), highlighting(32, 37))
    val expectedHighlightingsSnapshot = TerminalOutputHighlightingsSnapshot(model.document, expectedHighlightings)

    assertEquals(expectedText, model.document.text)
    assertEquals(expectedHighlightingsSnapshot, model.getHighlightings())
  }

  @Test
  fun `update editor content incrementally with overflow`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 16)

    val text1 = """
      foofoo
      barbar
    """.trimIndent()
    val styles1 = listOf(styleRange(0, 3), styleRange(3, 6), styleRange(7, 10), styleRange(10, 13))

    val text2 = """
      bazbaz
      badbad
    """.trimIndent()
    val styles2 = listOf(styleRange(0, 6), styleRange(7, 13))

    model.update(0, text1, styles1)
    model.update(1, text2, styles2)

    val expectedText = """
      oo
      bazbaz
      badbad
    """.trimIndent()
    val expectedHighlightings = listOf(highlighting(3, 9), highlighting(10, 16))
    val expectedHighlightingsSnapshot = TerminalOutputHighlightingsSnapshot(model.document, expectedHighlightings)

    assertEquals(expectedText, model.document.text)
    assertEquals(expectedHighlightingsSnapshot, model.getHighlightings())
  }

  @Test
  fun `update editor content after overflow`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 16)

    val text1 = """
      foofoo
      barbar
    """.trimIndent()
    val styles1 = listOf(styleRange(0, 3), styleRange(3, 6), styleRange(7, 10), styleRange(10, 13))

    val text2 = """
      bazbaz
      badbad
    """.trimIndent()
    val styles2 = listOf(styleRange(0, 6), styleRange(7, 13))

    val text3 = """
      fadfad
      kadkad
    """.trimIndent()
    val styles3 = listOf(styleRange(0, 6), styleRange(7, 13))

    model.update(0, text1, styles1)
    model.update(1, text2, styles2)
    model.update(2, text3, styles3)

    val expectedText = """
      az
      fadfad
      kadkad
    """.trimIndent()
    val expectedHighlightings = listOf(highlighting(3, 9), highlighting(10, 16))
    val expectedHighlightingsSnapshot = TerminalOutputHighlightingsSnapshot(model.document, expectedHighlightings)

    assertEquals(expectedText, model.document.text)
    assertEquals(expectedHighlightingsSnapshot, model.getHighlightings())
  }

  @Test
  fun `update exceeds maxCapacity`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 10)
    val startOffsets = mutableListOf<Int>()
    model.addListener(testRootDisposable, object: TerminalOutputModelListener {
      override fun afterContentChanged(model: TerminalOutputModel, startOffset: Int, isTypeAhead: Boolean) {
        startOffsets.add(startOffset)
      }
    })

    model.update(0, """
      abcdef
      ghijkl
    """.trimIndent(), emptyList())

    assertEquals("""
      def
      ghijkl
    """.trimIndent(), model.document.text)

    model.update(1, """
      mnopqrs
      tuvwxyz
    """.trimIndent(), emptyList())

    assertEquals("""
      rs
      tuvwxyz
    """.trimIndent(), model.document.text)
    assertEquals(listOf(0, 0), startOffsets)
  }

  @Test
  fun `cursor is on a partially trimmed line`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 10)

    model.update(0, """
      abcdef
      ghijkl
    """.trimIndent(), emptyList())
    model.updateCursor(0, 4)

    assertEquals("""
      def
      ghijkl
    """.trimIndent(), model.document.text)
    // three characters were trimmed, so the new cursor offset is 1
    assertEquals(1, model.cursorOffsetState.value)

    // now check that this specific state can be copied correctly

    val state = model.dumpState()
    val newModel = TerminalTestUtil.createOutputModel(maxLength = 10)
    newModel.restore(state)

    assertEquals("""
      def
      ghijkl
    """.trimIndent(), newModel.document.text)
    assertEquals(1, newModel.cursorOffsetState.value)

    // ...and modified correctly

    newModel.updateCursor(0, 5)
    assertEquals(2, newModel.cursorOffsetState.value)
  }

  @Test
  fun `update editor content from the start when some lines were trimmed already (clear)`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 10)

    // Prepare
    val fillerText = "12345"
    for (lineInd in 0L until 10L) {
      model.update(lineInd, fillerText, emptyList())
    }

    // Test
    model.update(0, "abcde", listOf(styleRange(0, 3), styleRange(3, 5)))

    val expectedText = "abcde"
    val expectedHighlightings = listOf(highlighting(0, 3), highlighting(3, 5))
    val expectedHighlightingsSnapshot = TerminalOutputHighlightingsSnapshot(model.document, expectedHighlightings)

    assertEquals(expectedText, model.document.text)
    assertEquals(expectedHighlightingsSnapshot, model.getHighlightings())
  }

  @Test
  fun `check that spaces are added if cursor is out of line bounds (last line)`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()

    // Prepare
    model.update(0, "abcde", listOf(styleRange(0, 3), styleRange(3, 5)))

    // Test
    model.updateCursor(0, 8)

    val expectedText = "abcde   "
    val expectedHighlightings = listOf(highlighting(0, 3), highlighting(3, 5))
    val expectedHighlightingsSnapshot = TerminalOutputHighlightingsSnapshot(model.document, expectedHighlightings)

    assertEquals(expectedText, model.document.text)
    assertEquals(expectedHighlightingsSnapshot, model.getHighlightings())
  }

  @Test
  fun `check that spaces are added if cursor is out of line bounds (middle line)`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()

    // Prepare
    model.update(0, "12345", listOf(styleRange(0, 5)))
    model.update(1, "abcde", listOf(styleRange(0, 3), styleRange(3, 5)))
    model.update(2, "67890", listOf(styleRange(0, 2), styleRange(2, 5)))

    // Test
    model.updateCursor(1, 8)

    val expectedText = "12345\nabcde   \n67890"
    val expectedHighlightings = listOf(highlighting(0, 5), highlighting(6, 9), highlighting(9, 11), highlighting(15, 17), highlighting(17, 20))
    val expectedHighlightingsSnapshot = TerminalOutputHighlightingsSnapshot(model.document, expectedHighlightings)

    assertEquals(expectedText, model.document.text)
    assertEquals(expectedHighlightingsSnapshot, model.getHighlightings())
  }

  @Test
  fun `check state is dumped correctly`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 10)

    // Prepare
    val line = "a".repeat(9) + "\n"
    val text = line.repeat(10)
    val styles = (0L until 20L).map { styleRange(it * 5L, (it + 1L) * 5L) }
    model.update(0, text, styles)
    model.updateCursor(9, 3)

    // Test
    val state = model.dumpState()

    assertEquals(line, state.text)
    assertEquals(9L, state.trimmedLinesCount)
    assertEquals(90L, state.trimmedCharsCount)
    assertEquals(3, state.cursorOffset)
    assertEquals(listOf(styleRange(90, 95), styleRange(95, 100)), state.highlightings)
  }

  @Test
  fun `check state is restored correctly`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 10)

    val line = "a".repeat(9) + "\n"
    val state = TerminalOutputModelState(
      text = line,
      trimmedLinesCount = 9,
      trimmedCharsCount = 90,
      firstLineTrimmedCharsCount = 10,
      cursorOffset = 3,
      highlightings = listOf(styleRange(90, 95), styleRange(95, 100))
    )

    model.restore(state)

    assertEquals(line, model.document.text)
    assertEquals(3, model.cursorOffsetState.value)
    assertEquals(9L, model.trimmedLinesCount)
    assertEquals(90L, model.trimmedCharsCount)
    assertEquals(10, model.firstLineTrimmedCharsCount)

    val expectedHighlightings = listOf(highlighting(0, 5), highlighting(5, 10))
    val expectedHighlightingsSnapshot = TerminalOutputHighlightingsSnapshot(model.document, expectedHighlightings)
    assertEquals(expectedHighlightingsSnapshot, model.getHighlightings())
  }

  @Test
  fun `check state is restored correctly after applying dumped state`() = runBlocking(Dispatchers.EDT) {
    val sourceModel = TerminalTestUtil.createOutputModel(maxLength = 10)

    // Prepare
    val line = "a".repeat(9) + "\n"
    val text = line.repeat(10)
    val styles = (0L until 20L).map { styleRange(it * 5L, (it + 1L) * 5L) }
    sourceModel.update(0, text, styles)
    sourceModel.updateCursor(9, 3)

    // Test
    val state = sourceModel.dumpState()
    val newModel = TerminalTestUtil.createOutputModel(maxLength = 10)
    newModel.restore(state)

    assertEquals(line, newModel.document.text)
    assertEquals(3, newModel.cursorOffsetState.value)
    assertEquals(9L, newModel.trimmedLinesCount)
    assertEquals(90L, newModel.trimmedCharsCount)

    val expectedHighlightings = listOf(highlighting(0, 5), highlighting(5, 10))
    val expectedHighlightingsSnapshot = TerminalOutputHighlightingsSnapshot(newModel.document, expectedHighlightings)
    assertEquals(expectedHighlightingsSnapshot, newModel.getHighlightings())
  }
}

private fun styleRange(start: Long, end: Long, textStyle: TextStyle = TextStyle()): StyleRange {
  return StyleRange(start, end, textStyle)
}

private val colorPalette = BlockTerminalColorPalette()

internal fun highlighting(start: Int, end: Int, textStyle: TextStyle = TextStyle()): HighlightingInfo {
  return HighlightingInfo(start, end, TextStyleAdapter(textStyle, colorPalette))
}
