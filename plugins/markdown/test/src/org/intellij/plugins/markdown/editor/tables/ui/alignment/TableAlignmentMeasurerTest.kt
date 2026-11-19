// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui.alignment

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting")
class TableAlignmentMeasurerTest {
  private val myFixture by codeInsightModuleFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    FontLayoutService.setInstance(MockFontLayoutService(CHAR_WIDTH, LINE_HEIGHT, DESCENT))
  }

  @AfterEach
  fun tearDown() {
    FontLayoutService.setInstance(null)
  }

  @Test
  fun `test measureRow reports segment widths and origin of a plain row`(): Unit = timeoutRunBlocking {
    configureSimpleTable()
    withContext(Dispatchers.EDT) {
      val segments = requireNotNull(measureRow(myFixture.editor, headerBorders()))
      assertEquals(0, segments.originX)
      // "| ab " is 5 characters, "| cdef " is 7.
      assertEquals(listOf(5 * CHAR_WIDTH, 7 * CHAR_WIDTH), segments.segmentWidths)
    }
  }

  @Test
  fun `test measureRow is unaffected by the padding of this feature`(): Unit = timeoutRunBlocking {
    configureSimpleTable()
    withContext(Dispatchers.EDT) {
      val borders = headerBorders()
      val expected = requireNotNull(measureRow(myFixture.editor, borders))
      addPad(borders[0], width = 13)
      addPad(borders[0] + 1, width = 7)
      addPad(borders[1], width = 29)
      assertEquals(expected, measureRow(myFixture.editor, borders))
    }
  }

  @Test
  fun `test measureRow counts an inlay of another provider as content`(): Unit = timeoutRunBlocking {
    configureSimpleTable()
    withContext(Dispatchers.EDT) {
      val borders = headerBorders()
      val before = requireNotNull(measureRow(myFixture.editor, borders))
      myFixture.editor.inlayModel.addInlineElement(borders[0] + 1, ForeignRenderer(11))
      val after = requireNotNull(measureRow(myFixture.editor, borders))
      assertEquals(before.segmentWidths[0] + 11, after.segmentWidths[0])
      assertEquals(before.segmentWidths[1], after.segmentWidths[1])
    }
  }

  @Test
  fun `test measureRow attributes an inlay at a separator to the segment ending there`(): Unit = timeoutRunBlocking {
    configureSimpleTable()
    withContext(Dispatchers.EDT) {
      val borders = headerBorders()
      val before = requireNotNull(measureRow(myFixture.editor, borders))
      myFixture.editor.inlayModel.addInlineElement(borders[1], ForeignRenderer(11))
      val after = requireNotNull(measureRow(myFixture.editor, borders))
      assertEquals(before.segmentWidths[0] + 11, after.segmentWidths[0])
      assertEquals(before.segmentWidths[1], after.segmentWidths[1])
      assertEquals(before.originX, after.originX)
    }
  }

  @Test
  fun `test measureRow attributes an inlay at the first separator to the origin`(): Unit = timeoutRunBlocking {
    configureSimpleTable()
    withContext(Dispatchers.EDT) {
      val borders = headerBorders()
      val before = requireNotNull(measureRow(myFixture.editor, borders))
      myFixture.editor.inlayModel.addInlineElement(borders[0], ForeignRenderer(11))
      val after = requireNotNull(measureRow(myFixture.editor, borders))
      assertEquals(before.originX + 11, after.originX)
      assertEquals(before.segmentWidths, after.segmentWidths)
    }
  }

  @Test
  fun `test measureRow reports pixel widths, not character counts, for wide characters`(): Unit = timeoutRunBlocking {
    FontLayoutService.setInstance(
      MockFontLayoutService({ codePoint -> if (codePoint == WIDE_CHAR.code) 2.0 * CHAR_WIDTH else CHAR_WIDTH.toDouble() },
                            LINE_HEIGHT, DESCENT)
    )
    // language=Markdown
    myFixture.configureByText(
      "some.md",
      """
      | $WIDE_CHAR$WIDE_CHAR | abc |
      |------|-----|
      """.trimIndent()
    )
    withContext(Dispatchers.EDT) {
      val segments = requireNotNull(measureRow(myFixture.editor, headerBorders()))
      assertEquals(listOf(7 * CHAR_WIDTH, 6 * CHAR_WIDTH), segments.segmentWidths)
    }
  }

  @Test
  fun `test measureRow reflects a collapsed fold region inside a cell`(): Unit = timeoutRunBlocking {
    configureSimpleTable()
    withContext(Dispatchers.EDT) {
      val borders = headerBorders()
      val before = requireNotNull(measureRow(myFixture.editor, borders))
      collapse(borders[1] + 2, borders[1] + 6)
      val after = requireNotNull(measureRow(myFixture.editor, borders))
      assertEquals(before.segmentWidths[0], after.segmentWidths[0])
      assertEquals(before.segmentWidths[1] - CHAR_WIDTH, after.segmentWidths[1])
    }
  }

  @Test
  fun `test measureRow returns null for fewer than two borders`(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "| a |")
    withContext(Dispatchers.EDT) {
      assertNull(measureRow(myFixture.editor, intArrayOf()))
      assertNull(measureRow(myFixture.editor, intArrayOf(0)))
    }
  }

  @Test
  fun `test measureRow returns null when the borders span several visual lines`(): Unit = timeoutRunBlocking {
    configureSimpleTable()
    withContext(Dispatchers.EDT) {
      val document = myFixture.editor.document
      val firstBorderOfHeader = document.getLineStartOffset(0)
      val firstBorderOfLastRow = document.getLineStartOffset(2)
      assertNull(measureRow(myFixture.editor, intArrayOf(firstBorderOfHeader, firstBorderOfLastRow)))
    }
  }

  private fun configureSimpleTable() {
    // language=Markdown
    myFixture.configureByText(
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
    requireNotNull(myFixture.editor.inlayModel.addInlineElement(offset, renderer))
  }

  private fun collapse(startOffset: Int, endOffset: Int) {
    myFixture.editor.foldingModel.runBatchFoldingOperation {
      val region = requireNotNull(myFixture.editor.foldingModel.addFoldRegion(startOffset, endOffset, FOLD_PLACEHOLDER))
      region.isExpanded = false
    }
  }

  private fun headerBorders(): IntArray {
    val document = myFixture.editor.document
    val text = document.charsSequence
    return (document.getLineStartOffset(0) until document.getLineEndOffset(0))
      .filter { text[it] == '|' }
      .toIntArray()
  }

  private class ForeignRenderer(private val width: Int) : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int = width
  }

  private companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    const val CHAR_WIDTH = 10
    const val LINE_HEIGHT = 10
    const val DESCENT = 2
    const val WIDE_CHAR = '中'
    const val FOLD_PLACEHOLDER = "..."
  }
}
