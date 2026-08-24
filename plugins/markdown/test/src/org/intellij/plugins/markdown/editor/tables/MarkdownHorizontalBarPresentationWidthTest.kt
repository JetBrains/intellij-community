// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.PlatformTestUtil
import org.intellij.plugins.markdown.editor.tables.ui.presentation.HorizontalBarPresentation
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable

@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownHorizontalBarPresentationWidthTest : LightPlatformCodeInsightTestCase() {
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

  fun `test the bar spans the rendered header row, padding included`() {
    // language=Markdown
    configureFromFileText(
      "some.md",
      """
      | a | bb |
      |---|----|
      | ccc | d |
      """.trimIndent()
    )
    val table = requireNotNull(PsiTreeUtil.findChildOfType(file, MarkdownTable::class.java))
    val header = requireNotNull(table.headerRow)
    PlatformTestUtil.waitWithEventsDispatching(
      "the alignment controller never padded the header row",
      { renderedWidth(header.textRange.startOffset, header.textRange.endOffset) > 10 * CHAR_WIDTH },
      TIMEOUT_SECONDS,
    )
    val expected = renderedWidth(header.textRange.startOffset, header.textRange.endOffset)

    val presentation = HorizontalBarPresentation(editor, table)
    PlatformTestUtil.waitWithEventsDispatching(
      "the presentation never computed its bounds",
      { presentation.width > 0 },
      TIMEOUT_SECONDS,
    )
    assertEquals(expected, presentation.width)
  }

  private fun renderedWidth(startOffset: Int, endOffset: Int): Int {
    return renderedX(endOffset) - renderedX(startOffset)
  }

  private fun renderedX(offset: Int): Int {
    return editor.offsetToXY(offset, false, false).x +
           editor.inlayModel.getInlineElementsInRange(offset, offset).sumOf { it.widthInPixels }
  }

  private companion object {
    const val CHAR_WIDTH = 10
    const val LINE_HEIGHT = 10
    const val DESCENT = 2
    const val TIMEOUT_SECONDS = 10
  }
}
