// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui.alignment

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.MockFontLayoutService

@Suppress("MarkdownIncorrectTableFormatting")
class TableAlignmentMeasurerTest : LightPlatformCodeInsightTestCase() {
  override fun setUp() {
    super.setUp()
    FontLayoutService.setInstance(MockFontLayoutService(CHAR_WIDTH, LINE_HEIGHT, DESCENT))
  }

  override fun tearDown() {
    try {
      FontLayoutService.setInstance(null)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test measureRow reports segment widths and origin of a plain row`() {
    configureSimpleTable()
    val segments = requireNotNull(measureRow(editor, headerBorders()))
    assertEquals(0, segments.originX)
    // "| ab " is 5 characters, "| cdef " is 7.
    assertEquals(listOf(5 * CHAR_WIDTH, 7 * CHAR_WIDTH), segments.segmentWidths)
  }

  fun `test measureRow is unaffected by the padding of this feature`() {
    configureSimpleTable()
    val borders = headerBorders()
    val expected = requireNotNull(measureRow(editor, borders))
    addPad(borders[0], width = 13)
    addPad(borders[0] + 1, width = 7)
    addPad(borders[1], width = 29)
    assertEquals(expected, measureRow(editor, borders))
  }

  fun `test measureRow counts an inlay of another provider as content`() {
    configureSimpleTable()
    val borders = headerBorders()
    val before = requireNotNull(measureRow(editor, borders))
    editor.inlayModel.addInlineElement(borders[0] + 1, ForeignRenderer(11))
    val after = requireNotNull(measureRow(editor, borders))
    assertEquals(before.segmentWidths[0] + 11, after.segmentWidths[0])
    assertEquals(before.segmentWidths[1], after.segmentWidths[1])
  }

  fun `test measureRow attributes an inlay at a separator to the segment ending there`() {
    configureSimpleTable()
    val borders = headerBorders()
    val before = requireNotNull(measureRow(editor, borders))
    editor.inlayModel.addInlineElement(borders[1], ForeignRenderer(11))
    val after = requireNotNull(measureRow(editor, borders))
    assertEquals(before.segmentWidths[0] + 11, after.segmentWidths[0])
    assertEquals(before.segmentWidths[1], after.segmentWidths[1])
    assertEquals(before.originX, after.originX)
  }

  fun `test measureRow attributes an inlay at the first separator to the origin`() {
    configureSimpleTable()
    val borders = headerBorders()
    val before = requireNotNull(measureRow(editor, borders))
    editor.inlayModel.addInlineElement(borders[0], ForeignRenderer(11))
    val after = requireNotNull(measureRow(editor, borders))
    assertEquals(before.originX + 11, after.originX)
    assertEquals(before.segmentWidths, after.segmentWidths)
  }

  fun `test measureRow reports pixel widths, not character counts, for wide characters`() {
    FontLayoutService.setInstance(
      MockFontLayoutService({ codePoint -> if (codePoint == WIDE_CHAR.code) 2.0 * CHAR_WIDTH else CHAR_WIDTH.toDouble() },
                            LINE_HEIGHT, DESCENT)
    )
    // language=Markdown
    configureFromFileText(
      "some.md",
      """
      | $WIDE_CHAR$WIDE_CHAR | abc |
      |------|-----|
      """.trimIndent()
    )
    val segments = requireNotNull(measureRow(editor, headerBorders()))
    assertEquals(listOf(7 * CHAR_WIDTH, 6 * CHAR_WIDTH), segments.segmentWidths)
  }

  fun `test measureRow reflects a collapsed fold region inside a cell`() {
    configureSimpleTable()
    val borders = headerBorders()
    val before = requireNotNull(measureRow(editor, borders))
    collapse(borders[1] + 2, borders[1] + 6)
    val after = requireNotNull(measureRow(editor, borders))
    assertEquals(before.segmentWidths[0], after.segmentWidths[0])
    assertEquals(before.segmentWidths[1] - CHAR_WIDTH, after.segmentWidths[1])
  }

  fun `test measureRow returns null for fewer than two borders`() {
    configureFromFileText("some.md", "| a |")
    assertNull(measureRow(editor, intArrayOf()))
    assertNull(measureRow(editor, intArrayOf(0)))
  }

  fun `test measureRow returns null when the borders span several visual lines`() {
    configureSimpleTable()
    val document = editor.document
    val firstBorderOfHeader = document.getLineStartOffset(0)
    val firstBorderOfLastRow = document.getLineStartOffset(2)
    assertNull(measureRow(editor, intArrayOf(firstBorderOfHeader, firstBorderOfLastRow)))
  }

  private fun configureSimpleTable() {
    // language=Markdown
    configureFromFileText(
      "some.md",
      """
      | ab | cdef |
      |----|------|
      | g  | h    |
      """.trimIndent()
    )
  }

  private fun addPad(offset: Int, width: Int) {
    val renderer = TablePadRenderer(width)
    requireNotNull(editor.inlayModel.addInlineElement(offset, renderer))
  }

  private fun collapse(startOffset: Int, endOffset: Int) {
    editor.foldingModel.runBatchFoldingOperation {
      val region = requireNotNull(editor.foldingModel.addFoldRegion(startOffset, endOffset, FOLD_PLACEHOLDER))
      region.isExpanded = false
    }
  }

  private fun headerBorders(): IntArray {
    val document = editor.document
    val text = document.charsSequence
    return (document.getLineStartOffset(0) until document.getLineEndOffset(0))
      .filter { text[it] == '|' }
      .toIntArray()
  }

  private class ForeignRenderer(private val width: Int) : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int = width
  }

  private companion object {
    const val CHAR_WIDTH = 10
    const val LINE_HEIGHT = 10
    const val DESCENT = 2
    const val WIDE_CHAR = '中'
    const val FOLD_PLACEHOLDER = "..."
  }
}
