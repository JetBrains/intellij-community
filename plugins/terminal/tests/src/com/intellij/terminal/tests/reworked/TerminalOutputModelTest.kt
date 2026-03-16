// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked

import com.intellij.openapi.application.EDT
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.restore
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.update
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.updateCursor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jediterm.terminal.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot
import org.jetbrains.plugins.terminal.block.output.TextStyleAdapter
import org.jetbrains.plugins.terminal.block.ui.BlockTerminalColorPalette
import org.jetbrains.plugins.terminal.session.impl.StyleRange
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputModelState
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
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
  fun `update exceeds maxCapacity`(): Unit = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 10)
    var balance = 0
    val events = mutableListOf<TerminalContentChangeEvent>()
    model.addListener(testRootDisposable, object: TerminalOutputModelListener {
      override fun beforeContentChanged(model: TerminalOutputModel) {
        ++balance
        assertThat(balance).isOne()
      }

      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        --balance
        assertThat(balance).isZero()
        events.add(event)
      }
    })

    model.update(0, """
      abcdef
      ghijkl
    """.trimIndent(), emptyList())
    val firstStartOffset = model.startOffset

    assertEquals("""
      def
      ghijkl
    """.trimIndent(), model.document.text)

    model.update(1, """
      mnopqrs
      tuvwxyz
    """.trimIndent(), emptyList())
    val secondStartOffset = model.startOffset

    assertEquals("""
      rs
      tuvwxyz
    """.trimIndent(), model.document.text)
    assertThat(balance).isZero()
    assertThat(firstStartOffset).isEqualTo(TerminalOffset.of(3)) // trimmed: abc
    assertThat(secondStartOffset).isEqualTo(TerminalOffset.of(12)) // trimmed: abcdef + EOL + mnopq (replacing ghijkl)
    assertThat(events).hasSize(4)
    // first insert
    assertThat(events[0].offset).isEqualTo(TerminalOffset.of(0))
    assertThat(events[0].oldText.toString()).isEmpty()
    assertThat(events[0].newText.toString()).isEqualTo("""
      abcdef
      ghijkl
    """.trimIndent())
    // first trimming
    assertThat(events[1].offset).isEqualTo(TerminalOffset.of(0))
    assertThat(events[1].oldText.toString()).isEqualTo("abc")
    assertThat(events[1].newText.toString()).isEmpty()
    // second insert
    assertThat(events[2].offset).isEqualTo(TerminalOffset.of(7)) // trimmed abc + def + EOL
    assertThat(events[2].oldText.toString()).isEqualTo("ghijkl")
    assertThat(events[2].newText.toString()).isEqualTo("""
      mnopqrs
      tuvwxyz
    """.trimIndent())
    // second trimming
    assertThat(events[3].offset).isEqualTo(TerminalOffset.of(3)) // only trimmed abc
    assertThat(events[3].oldText.toString()).isEqualTo("""
      def
      mnopq
    """.trimIndent())
    assertThat(events[3].newText.toString()).isEmpty()
  }

  @Test
  fun `update events ignore unchanged prefix and suffix - only line`(): Unit = runBlocking(Dispatchers.EDT)  {
    val model = TerminalTestUtil.createOutputModel()
    val events = mutableListOf<TerminalContentChangeEvent>()
    model.addListener(testRootDisposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        events += event
      }
    })
    model.update(0, "some long text")
    model.update(0, "some even longer text")
    assertThat(events.last().offset).isEqualTo(TerminalOffset.of(5))
    assertThat(events.last().oldText.toString()).isEqualTo("long")
    assertThat(events.last().newText.toString()).isEqualTo("even longer")
  }

  @Test
  fun `update events ignore unchanged prefix and suffix - second line`(): Unit = runBlocking(Dispatchers.EDT)  {
    val model = TerminalTestUtil.createOutputModel()
    val events = mutableListOf<TerminalContentChangeEvent>()
    model.addListener(testRootDisposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        events += event
      }
    })
    model.update(0, "some long text")
    model.update(1, "another line")
    model.update(1, "another long line")
    assertThat(events.last().offset).isEqualTo(TerminalOffset.of(24))
    assertThat(events.last().oldText.toString()).isEqualTo("")
    assertThat(events.last().newText.toString()).isEqualTo("ong l")
  }

  @Test
  fun `update events ignore unchanged prefix and suffix - longer line, both sides match`(): Unit = runBlocking(Dispatchers.EDT)  {
    val model = TerminalTestUtil.createOutputModel()
    val events = mutableListOf<TerminalContentChangeEvent>()
    model.addListener(testRootDisposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        events += event
      }
    })
    model.update(0, "some long text")
    model.update(1, "another line")
    model.update(1, "another line, long line")
    assertThat(events.last().offset).isEqualTo(TerminalOffset.of(27))
    assertThat(events.last().oldText.toString()).isEqualTo("")
    assertThat(events.last().newText.toString()).isEqualTo(", long line")
  }

  @Test
  fun `update events ignore unchanged prefix and suffix - shorter line, both sides match`(): Unit = runBlocking(Dispatchers.EDT)  {
    val model = TerminalTestUtil.createOutputModel()
    val events = mutableListOf<TerminalContentChangeEvent>()
    model.addListener(testRootDisposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        events += event
      }
    })
    model.update(0, "some long text")
    model.update(1, "another line, long line")
    model.update(1, "another line")
    assertThat(events.last().offset).isEqualTo(TerminalOffset.of(27))
    assertThat(events.last().oldText.toString()).isEqualTo(", long line")
    assertThat(events.last().newText.toString()).isEqualTo("")
  }

  @Test
  fun `update events ignore unchanged prefix and suffix - no-op change`(): Unit = runBlocking(Dispatchers.EDT)  {
    val model = TerminalTestUtil.createOutputModel()
    val events = mutableListOf<TerminalContentChangeEvent>()
    model.addListener(testRootDisposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        events += event
      }
    })
    model.update(0, "some long text")
    model.update(0, "some long text")
    // no assertion for the offset because it can technically be anywhere
    assertThat(events.last().oldText).isEmpty()
    assertThat(events.last().newText).isEmpty()
  }

  @Test
  fun `update events ignore unchanged prefix and suffix - trimmed offsets`(): Unit = runBlocking(Dispatchers.EDT)  {
    val model = TerminalTestUtil.createOutputModel(maxLength = 5)
    val events = mutableListOf<TerminalContentChangeEvent>()
    model.addListener(testRootDisposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        events += event
      }
    })
    model.update(0, "012345")
    model.update(0, "12x45")
    assertThat(events.last().offset).isEqualTo(TerminalOffset.of(3))
    assertThat(events.last().oldText.toString()).isEqualTo("3")
    assertThat(events.last().newText.toString()).isEqualTo("x")
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
    assertEquals(model.startOffset + 1, model.cursorOffset)

    // now check that this specific state can be copied correctly

    val state = model.dumpState()
    val newModel = TerminalTestUtil.createOutputModel(maxLength = 10)
    newModel.restore(state)

    assertEquals("""
      def
      ghijkl
    """.trimIndent(), newModel.document.text)
    assertEquals(model.startOffset + 1, newModel.cursorOffset)

    // ...and modified correctly

    newModel.updateCursor(0, 5)
    assertEquals(model.startOffset + 2, newModel.cursorOffset)
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
  fun `check that lines are added if cursor is beyond the last line`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()

    // Prepare
    model.update(0, "abcde", listOf(styleRange(0, 3), styleRange(3, 5)))

    // Test
    model.updateCursor(1, 0)

    val expectedText = "abcde\n"
    val expectedHighlightings = listOf(highlighting(0, 3), highlighting(3, 5))
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
    assertEquals(model.startOffset + 3, model.cursorOffset)
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
    assertEquals(newModel.startOffset + 3, newModel.cursorOffset)
    assertEquals(9L, newModel.trimmedLinesCount)
    assertEquals(90L, newModel.trimmedCharsCount)

    val expectedHighlightings = listOf(highlighting(0, 5), highlighting(5, 10))
    val expectedHighlightingsSnapshot = TerminalOutputHighlightingsSnapshot(newModel.document, expectedHighlightings)
    assertEquals(expectedHighlightingsSnapshot, newModel.getHighlightings())
  }

  @Test
  fun `offset arithmetics`(): Unit = runBlocking(Dispatchers.EDT) {
    val offset0 = TerminalOffset.of(0L)
    val offset1 = TerminalOffset.of(1L)
    val offset2 = TerminalOffset.of(2L)
    assertThat(offset0).isEqualTo(TerminalOffset.ZERO)
    assertThat(offset0).isLessThan(offset1)
    assertThat(offset1).isGreaterThan(offset0)
    assertThat(offset0).isNotEqualTo(offset1)
    assertThat(offset2 - offset1).isEqualTo(1L)
    assertThat(offset2 - offset2).isZero()
    assertThat(offset0 + 1L).isEqualTo(offset1)
    assertThat(offset0 + 2L).isEqualTo(offset2)
    assertThat(offset2 - 1L).isEqualTo(offset1)
    assertThat(offset2 - 2L).isEqualTo(offset0)
  }

  @Test
  fun `line arithmetics`(): Unit = runBlocking(Dispatchers.EDT) {
    val line0 = TerminalLineIndex.of(0L)
    val line1 = TerminalLineIndex.of(1L)
    val line2 = TerminalLineIndex.of(2L)
    assertThat(line0).isEqualTo(TerminalLineIndex.ZERO)
    assertThat(line0).isLessThan(line1)
    assertThat(line1).isGreaterThan(line0)
    assertThat(line0).isNotEqualTo(line1)
    assertThat(line2 - line1).isEqualTo(1L)
    assertThat(line2 - line2).isZero()
    assertThat(line0 + 1L).isEqualTo(line1)
    assertThat(line0 + 2L).isEqualTo(line2)
    assertThat(line2 - 1L).isEqualTo(line1)
    assertThat(line2 - 2L).isEqualTo(line0)
  }

  @Test
  fun `empty model state`() {
    val sut = TerminalTestUtil.createOutputModel(100)
    assertThat(sut.textLength).isZero()
    assertThat(sut.lineCount).isOne()
    assertThat(sut.startOffset).isEqualTo(TerminalOffset.ZERO)
    assertThat(sut.startOffset).isEqualTo(sut.endOffset)
    assertThat(sut.getText(sut.startOffset, sut.endOffset)).isEmpty()
    assertThat(sut.firstLineIndex).isEqualTo(TerminalLineIndex.ZERO)
    assertThat(sut.firstLineIndex).isEqualTo(sut.lastLineIndex)
    assertThat(sut.getStartOfLine(sut.firstLineIndex)).isEqualTo(sut.startOffset)
    assertThat(sut.getEndOfLine(sut.firstLineIndex)).isEqualTo(sut.startOffset)
    assertThat(sut.getLineByOffset(sut.startOffset)).isEqualTo(sut.firstLineIndex)
  }

  @Test
  fun `empty snapshot state`() {
    val sut = TerminalTestUtil.createOutputModel(100).takeSnapshot()
    assertThat(sut.textLength).isZero()
    assertThat(sut.lineCount).isOne()
    assertThat(sut.startOffset).isEqualTo(TerminalOffset.ZERO)
    assertThat(sut.startOffset).isEqualTo(sut.endOffset)
    assertThat(sut.getText(sut.startOffset, sut.endOffset)).isEmpty()
    assertThat(sut.firstLineIndex).isEqualTo(TerminalLineIndex.ZERO)
    assertThat(sut.firstLineIndex).isEqualTo(sut.lastLineIndex)
    assertThat(sut.getStartOfLine(sut.firstLineIndex)).isEqualTo(sut.startOffset)
    assertThat(sut.getEndOfLine(sut.firstLineIndex)).isEqualTo(sut.startOffset)
    assertThat(sut.getLineByOffset(sut.startOffset)).isEqualTo(sut.firstLineIndex)
  }
}

internal fun styleRange(start: Long, end: Long, textStyle: TextStyle = TextStyle()): StyleRange {
  return StyleRange(start, end, textStyle)
}

private val colorPalette = BlockTerminalColorPalette()

internal fun highlighting(start: Int, end: Int, textStyle: TextStyle = TextStyle()): HighlightingInfo {
  return HighlightingInfo(start, end, TextStyleAdapter(textStyle, colorPalette))
}
