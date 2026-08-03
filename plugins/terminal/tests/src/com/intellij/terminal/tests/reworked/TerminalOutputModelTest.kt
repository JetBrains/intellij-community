// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked

import com.intellij.openapi.application.EDT
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.assertMatches
import com.intellij.terminal.tests.reworked.util.outputPattern
import com.intellij.terminal.tests.reworked.util.updateContent
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
    // The cursor stays at the very start since it's never moved in this test.
    val pattern = outputPattern("<cursor>$text")

    model.updateContent(0, pattern)

    model.assertMatches(pattern)
  }

  @Test
  fun `update editor content incrementally with styles`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()

    model.updateContent(0, outputPattern("<s1>first</s1> line\n<s2>second</s2> <s3>line</s3>"))
    model.updateContent(1, outputPattern("<s4>replaced</s4> <s5>second</s5> line\n<s6>third</s6> line"))

    model.assertMatches(outputPattern("<cursor><s1>first</s1> line\n<s4>replaced</s4> <s5>second</s5> line\n<s6>third</s6> line"))
  }

  @Test
  fun `update editor content incrementally with overflow`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 16)

    model.updateContent(0, outputPattern("<s1>foo</s1><s2>foo</s2>\n<s3>bar</s3><s4>bar</s4>"))
    model.updateContent(1, outputPattern("<s1>bazbaz</s1>\n<s2>badbad</s2>"))

    model.assertMatches(outputPattern("<cursor>oo\n<s1>bazbaz</s1>\n<s2>badbad</s2>"))
  }

  @Test
  fun `update editor content after overflow`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 16)

    model.updateContent(0, outputPattern("<s1>foo</s1><s2>foo</s2>\n<s3>bar</s3><s4>bar</s4>"))
    model.updateContent(1, outputPattern("<s1>bazbaz</s1>\n<s2>badbad</s2>"))
    model.updateContent(2, outputPattern("<s1>fadfad</s1>\n<s2>kadkad</s2>"))

    model.assertMatches(outputPattern("<cursor>az\n<s1>fadfad</s1>\n<s2>kadkad</s2>"))
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

    model.updateContent(0, outputPattern("""
      abcdef
      ghijkl
    """.trimIndent()))
    val firstStartOffset = model.startOffset

    assertEquals("""
      def
      ghijkl
    """.trimIndent(), model.document.text)

    model.updateContent(1, outputPattern("""
      mnopqrs
      tuvwxyz
    """.trimIndent()))
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
    model.updateContent(0, outputPattern("some long text"))
    model.updateContent(0, outputPattern("some even longer text"))
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
    model.updateContent(0, outputPattern("some long text"))
    model.updateContent(1, outputPattern("another line"))
    model.updateContent(1, outputPattern("another long line"))
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
    model.updateContent(0, outputPattern("some long text"))
    model.updateContent(1, outputPattern("another line"))
    model.updateContent(1, outputPattern("another line, long line"))
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
    model.updateContent(0, outputPattern("some long text"))
    model.updateContent(1, outputPattern("another line, long line"))
    model.updateContent(1, outputPattern("another line"))
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
    model.updateContent(0, outputPattern("some long text"))
    model.updateContent(0, outputPattern("some long text"))
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
    model.updateContent(0, outputPattern("012345"))
    model.updateContent(0, outputPattern("12x45"))
    assertThat(events.last().offset).isEqualTo(TerminalOffset.of(3))
    assertThat(events.last().oldText.toString()).isEqualTo("3")
    assertThat(events.last().newText.toString()).isEqualTo("x")
  }

  @Test
  fun `cursor is on a partially trimmed line`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 10)

    model.updateContent(0, outputPattern("abcdef\nghijkl"))
    model.updateCursorPosition(0, 4)

    // three characters were trimmed, so the new (relative) cursor offset is 1
    val expected = outputPattern("d<cursor>ef\nghijkl")
    model.assertMatches(expected)

    // now check that this specific state can be copied correctly

    val state = model.dumpState()
    val newModel = TerminalTestUtil.createOutputModel(maxLength = 10)
    newModel.restoreFromState(state)

    newModel.assertMatches(expected)

    // ...and modified correctly

    newModel.updateCursorPosition(0, 5)
    newModel.assertMatches(outputPattern("de<cursor>f\nghijkl"))
  }

  @Test
  fun `update editor content from the start when some lines were trimmed already (clear)`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 10)

    // Prepare
    for (lineInd in 0L until 10L) {
      model.updateContent(lineInd, outputPattern("12345"))
    }

    // Test: absoluteLineIndex 0 is now far behind the trimmed lines, so this should clear the document first
    val pattern = outputPattern("<cursor><s1>abc</s1><s2>de</s2>")
    model.updateContent(0, pattern)

    model.assertMatches(pattern)
  }

  @Test
  fun `check that spaces are added if cursor is out of line bounds (last line)`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()

    // Prepare
    model.updateContent(0, outputPattern("<s1>abc</s1><s2>de</s2>"))

    // Test
    model.updateCursorPosition(0, 8)

    model.assertMatches(outputPattern("<s1>abc</s1><s2>de</s2>   <cursor>"))
  }

  @Test
  fun `check that spaces are added if cursor is out of line bounds (middle line)`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()

    // Prepare
    model.updateContent(0, outputPattern("<s1>12345</s1>"))
    model.updateContent(1, outputPattern("<s2>abc</s2><s3>de</s3>"))
    model.updateContent(2, outputPattern("<s4>67</s4><s5>890</s5>"))

    // Test
    model.updateCursorPosition(1, 8)

    model.assertMatches(outputPattern("<s1>12345</s1>\n<s2>abc</s2><s3>de</s3>   <cursor>\n<s4>67</s4><s5>890</s5>"))
  }

  @Test
  fun `check that lines are added if cursor is beyond the last line`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()

    // Prepare
    model.updateContent(0, outputPattern("<s1>abc</s1><s2>de</s2>"))

    // Test
    model.updateCursorPosition(1, 0)

    model.assertMatches(outputPattern("<s1>abc</s1><s2>de</s2>\n<cursor>"))
  }

  @Test
  fun `check state is dumped correctly`() = runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel(maxLength = 10)

    // Prepare
    val line = "a".repeat(9) + "\n"
    val text = line.repeat(10)
    val styles = (0L until 20L).map { styleRange(it * 5L, (it + 1L) * 5L) }
    model.updateContent(0, text, styles)
    model.updateCursorPosition(9, 3)

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
      highlightings = listOf(styleRange(90, 95), styleRange(95, 100)),
      osc8Hyperlinks = emptyList(),
    )

    model.restoreFromState(state)

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
    sourceModel.updateContent(0, text, styles)
    sourceModel.updateCursorPosition(9, 3)

    // Test
    val state = sourceModel.dumpState()
    val newModel = TerminalTestUtil.createOutputModel(maxLength = 10)
    newModel.restoreFromState(state)

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
