// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.reworked

import com.intellij.openapi.application.EDT
import com.intellij.terminal.session.StyleRange
import com.intellij.terminal.session.TerminalOutputModelState
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jediterm.terminal.TextStyle
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.output.EmptyTextAttributesProvider
import org.jetbrains.plugins.terminal.block.output.HighlightingInfo
import org.jetbrains.plugins.terminal.block.output.TerminalOutputHighlightingsSnapshot
import org.jetbrains.plugins.terminal.block.output.TextStyleAdapter
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelImpl
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.block.ui.BlockTerminalColorPalette
import org.jetbrains.plugins.terminal.reworked.util.TerminalTestUtil
import org.jetbrains.plugins.terminal.reworked.util.TerminalTestUtil.replace
import org.jetbrains.plugins.terminal.reworked.util.TerminalTestUtil.restore
import org.jetbrains.plugins.terminal.reworked.util.TerminalTestUtil.update
import org.jetbrains.plugins.terminal.reworked.util.TerminalTestUtil.updateCursor
import org.jetbrains.plugins.terminal.reworked.util.parseTextWithReplacement
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
  fun `insert into empty - no highlighting`() =
    testReplace("<|12345>")

  @Test
  fun `insert into empty - with highlighting`() =
    testReplace("<|[12](1)345>")

  @Test
  fun `insert at end - no highlighting`() =
    testReplace("12345\n<|abcd\n>")

  @Test
  fun `insert at end - with highlighting`() =
    testReplace("12345\n<|[ab](1)cd\n>")

  @Test
  fun `insert at start - no highlighting`() =
    testReplace("<|abcd\n>12345\n")

  @Test
  fun `insert at start - with new highlighting`() =
    testReplace("<|[ab](1)cd\n>12345\n")

  @Test
  fun `insert at start - with existing highlighting right after`() =
    testReplace("<|abcd\n>[12](1)345\n")

  @Test
  fun `insert at start - with existing highlighting further away`() =
    testReplace("<|abcd\n>1[23](1)45\n")

  @Test
  fun `insert in the middle - no highlightings`() =
    testReplace("12345\n<|qwer>\nabcd\n")

  @Test
  fun `insert in the middle - with existing highlightings around`() =
    testReplace("[12345\n](1)<|qwer\n>[abcd\n](2)")

  @Test
  fun `insert in the middle - inside existing highlighting`() =
    testReplace("[12345\n<|qwer\n>abcd\n](1)")

  @Test
  fun `insert in the middle - inside existing highlighting with adjacent highlightings around`() =
    testReplace("[1234](1)[5\n<|qwer\n>abc](2)d\n")

  @Test
  fun `insert in the middle - inside existing highlighting with non-adjacent highlightings around`() =
    testReplace("[123](1)4[5\n<|qwer\n>ab](2)cd\n")

  @Test
  fun `delete at start - no highlightings`() =
    testReplace("<12345\n|>abcd\n")

  @Test
  fun `delete at start - whole style range`() =
    testReplace("<[12345\n](1)|>abcd\n")

  @Test
  fun `delete at start - end of the deleted region covered by a style range`() =
    testReplace("<1[2345\n](1)|>abcd\n")

  @Test
  fun `delete at start - start of the deleted region covered by a style range`() =
    testReplace("<[1234](1)5\n|>abcd\n")

  @Test
  fun `delete at start - style range extends beyond`() =
    testReplace("<[12345\n|>a](1)bcd\n")

  @Test
  fun `delete at end - whole style range`() =
    testReplace("12345\n<[abcd\n](1)|>")

  @Test
  fun `delete at end - end of the deleted region covered by a style range`() =
    testReplace("12345\n<a[bcd\n](1)|>")

  @Test
  fun `delete at end - start of the deleted region covered by a style range`() =
    testReplace("12345\n<[abc](1)d\n|>")

  @Test
  fun `delete at end - style range starts before the deleted region`() =
    testReplace("123[45\n<abc](1)d\n|>")

  @Test
  fun `delete in the middle - whole style range with adjacent highlightings`() =
    testReplace("[12345\n](1)<[abcd](2)\n|>[qwer\n](1)")

  @Test
  fun `delete in the middle - whole style range without adjacent highlightings`() =
    testReplace("[12](1)345\n<[abcd](2)\n|>q[wer\n](1)")

  @Test
  fun `delete in the middle - the end of a style range`() =
    testReplace("123[45\n<abcd\n](2)|>qwer\n")

  @Test
  fun `delete in the middle - the beginning of a style range`() =
    testReplace("12345\n<[abcd\n|>q](2)wer\n")

  @Test
  fun `replace at start - no highlighting`() =
    testReplace("<12|abc>345\n")

  @Test
  fun `replace at start - insert highlighting`() =
    testReplace("<12|[abc](1)>345\n")

  @Test
  fun `replace at start - replace highlighting`() =
    testReplace("<[12](1)|[abc](2)>345\n")

  @Test
  fun `replace at start - partially replace highlighting`() =
    testReplace("<[12|[abc](2)>3](1)45\n")

  @Test
  fun `replace at start - remove highlighting`() =
    testReplace("<[12](1)|abc>345\n")

  @Test
  fun `replace at end - no highlighting`() =
    testReplace("123<45\n|abc\n>")

  @Test
  fun `replace at end - insert highlighting`() =
    testReplace("123<45\n|[abc\n](1)>")

  @Test
  fun `replace at end - replace highlighting`() =
    testReplace("123<[45\n](1)|[abc](2)>")

  @Test
  fun `replace at end - partially replace highlighting`() =
    testReplace("12[3<45\n](1)|[abc](2)>")

  @Test
  fun `replace at end - remove highlighting`() =
    testReplace("123<[45\n](1)|abc>")

  @Test
  fun `replace in the middle - no highlighting`() =
    testReplace("12345\n<abcd\n|xyz\n>qwer\n")

  @Test
  fun `replace in the middle - insert highlighting`() =
    testReplace("12345\n<abcd\n|[xyz\n](1)>qwer\n")

  @Test
  fun `replace in the middle - replace highlighting`() =
    testReplace("12345\n<[abcd\n](1)|[xyz\n](2)>qwer\n")

  @Test
  fun `replace in the middle - replace the end of a highlighting region`() =
    testReplace("1234[5\n<abcd\n](1)|[xyz\n](2)>qwer\n")

  @Test
  fun `replace in the middle - replace the start of a highlighting region`() =
    testReplace("12345\n<[abcd\n|[xyz\n](2)>q](1)wer\n")

  @Test
  fun `replace in the middle - replace the middle of a highlighting region`() =
    testReplace("1234[5\n<abcd\n|[xyz\n](2)>q](1)wer\n")

  @Test
  fun `replace in the middle - replace the middle of a highlighting region with adjacent highlightings`() =
    testReplace("[1234](3)[5\n<abcd\n|[xyz\n](2)>q](1)[wer\n](4)")

  @Test
  fun `replace in the middle - replace the middle of a highlighting region with non-adjacent highlightings`() =
    testReplace("[123](3)4[5\n<abcd\n|[xyz\n](2)>q]w(1)[er\n](4)")

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

private fun List<HighlightingInfo>.getHighlightingAtOffset(offset: Int): HighlightingInfo? =
  filter { offset in it.startOffset until it.endOffset }
    .let { 
      when (it.size) {
        0 -> null
        1 -> it.single()
        else -> throw AssertionError("Highlightings overlap: $it")
      }
    }

private fun TerminalOutputHighlightingsSnapshot.getHighlightingAtOffset(offset: Int): HighlightingInfo? =
  findHighlightingIndex(offset)
    .let { index -> if (index in 0 until size) get(index) else null }
    ?.takeIf { offset in it.startOffset until it.endOffset && it.textAttributesProvider !is EmptyTextAttributesProvider}

/**
 * Runs a single text replacement test.
 *
 * Argument syntax:
 *
 * - Style regions are indicated with Markdown-like syntax:
 * ```
 * abcd[ef](2)gh
 * ```
 * meaning that the "ef" part will have some highlighting style. There are different styles numbered from 1 to 4.
 * - The (only) replacement region is indicated with `<>`, where the texts "before" and "after" are separated with `|`, for example:
 * ```
 * ab<cd|ef>gh
 * ```
 * meaning "replace cd with ef."
 * - Style regions, excluding the "after" part, which has separate styles, may not be nested, but they can cross the `<>` boundaries, for example:
 * ```
 * [ab<cd|ef>gh](2)
 * ```
 * meaning that "before" will be `[abcdgh](2)` and "after" will be `[ab](2)cd[gh](2)` (because the replacement has no highlighting here).
 * - Style regions in the "after" part can have their own highlighting regions, for example:
 * ```
 * ab<c[d|[gh](2)>ef](1)
 * ```
 * meaning, in `abc[def](1)` replace "cd" with `[gh](2)`, so the end result will be `ab[gh](2)[ef](1)`.
 */
private fun testReplace(
  textWithReplacement: String,
) {
  runBlocking(Dispatchers.EDT) {
    val model = TerminalTestUtil.createOutputModel()
    val textWithReplacement = parseTextWithReplacement(textWithReplacement)
    model.replace(0, 0, textWithReplacement.originalText.text, textWithReplacement.originalText.styles)
    model.replace(
      textWithReplacement.removedIndex,
      textWithReplacement.removedLength,
      textWithReplacement.insertedText.text,
      textWithReplacement.insertedText.styles,
    )
    assertText(model, textWithReplacement.modifiedText.text)
    assertHighlightings(
      model,
      textWithReplacement.modifiedText.styles.map {
        highlighting(it.startOffset.toInt(), it.endOffset.toInt(), it.style)
      }
    )
  }
}

private fun assertText(model: TerminalOutputModelImpl, expectedText: String) {
  assertEquals(expectedText, model.document.text)
}

private fun assertHighlightings(model: TerminalOutputModelImpl, expectedHighlightings: List<HighlightingInfo>) {
  val actualHighlightings = model.getHighlightings()
  assertActualHighlightingsCoverTheDocument(model, actualHighlightings)
  assertActualHighlightingsMatchExpected(model, expectedHighlightings, actualHighlightings)
}

private fun assertActualHighlightingsCoverTheDocument(model: TerminalOutputModelImpl, actualHighlightings: TerminalOutputHighlightingsSnapshot) {
  var previousEnd = 0
  val validRange = 0..model.document.textLength
  for (i in 0 until actualHighlightings.size) {
    val highlighting = actualHighlightings[i]
    assertThat(highlighting.startOffset).`as`("highlightings[$i].startOffset")
      .isBetween(validRange.first, validRange.last)
      .isEqualTo(previousEnd)
    assertThat(highlighting.endOffset).`as`("highlightings[$i].endOffset")
      .isBetween(validRange.first, validRange.last)
      .isGreaterThanOrEqualTo(highlighting.startOffset)
    previousEnd = highlighting.endOffset
  }
  assertThat(previousEnd).`as`("last end index").isEqualTo(model.document.textLength)
}

private fun assertActualHighlightingsMatchExpected(
  model: TerminalOutputModelImpl,
  expectedHighlightings: List<HighlightingInfo>,
  actualHighlightings: TerminalOutputHighlightingsSnapshot,
) {
  for (offset in 0 until model.document.textLength) {
    val expectedHighlighting = expectedHighlightings.getHighlightingAtOffset(offset)?.textAttributesProvider
    val actualHighlighting = actualHighlightings.getHighlightingAtOffset(offset)?.textAttributesProvider
    assertThat(actualHighlighting).`as`("highlighting at offset %d", offset).isEqualTo(expectedHighlighting)
  }
}

private fun styleRange(start: Long, end: Long, textStyle: TextStyle = TextStyle()): StyleRange {
  return StyleRange(start, end, textStyle)
}

private val colorPalette = BlockTerminalColorPalette()

private fun highlighting(start: Int, end: Int, textStyle: TextStyle = TextStyle()): HighlightingInfo {
  return HighlightingInfo(start, end, TextStyleAdapter(textStyle, colorPalette))
}
