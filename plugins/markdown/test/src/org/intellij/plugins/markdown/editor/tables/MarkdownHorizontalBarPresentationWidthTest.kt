// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownTestingUtil.paintPresentation
import org.intellij.plugins.markdown.editor.tables.ui.presentation.HorizontalBarPresentation
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownHorizontalBarPresentationWidthTest {
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
  fun `test the bar spans the rendered header row, padding included`(): Unit = timeoutRunBlocking {
    // language=Markdown
    myFixture.configureByText(
      "some.md",
      """
      | a | bb |
      |---|----|
      | ccc | d |
      """.trimIndent()
    )
    withContext(Dispatchers.EDT) {
      val table = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, MarkdownTable::class.java))
      val header = requireNotNull(table.headerRow)
      PlatformTestUtil.waitWithEventsDispatching(
        "the alignment controller never padded the header row",
        { renderedWidth(header.textRange.startOffset, header.textRange.endOffset) > 10 * CHAR_WIDTH },
        TIMEOUT_SECONDS,
      )
      val expected = renderedWidth(header.textRange.startOffset, header.textRange.endOffset)

      val presentation = HorizontalBarPresentation(myFixture.editor, table)
      paintPresentation(presentation)
      assertEquals(expected, presentation.width)
    }
  }

  private fun renderedWidth(startOffset: Int, endOffset: Int): Int {
    return renderedX(endOffset) - renderedX(startOffset)
  }

  private fun renderedX(offset: Int): Int {
    return myFixture.editor.offsetToXY(offset, false, false).x +
           myFixture.editor.inlayModel.getInlineElementsInRange(offset, offset).sumOf { it.widthInPixels }
  }

  private companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    const val CHAR_WIDTH = 10
    const val LINE_HEIGHT = 10
    const val DESCENT = 2
    const val TIMEOUT_SECONDS = 10
  }
}
