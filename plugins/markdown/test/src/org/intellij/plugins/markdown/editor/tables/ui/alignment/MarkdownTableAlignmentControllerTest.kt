// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui.alignment

import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewReconciler
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertNothingLogged
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewConfiguration
import org.intellij.plugins.markdown.editor.livepreview.computeLivePreviewSpecs
import org.intellij.plugins.markdown.editor.livepreview.setLivePreviewEnabledness
import org.intellij.plugins.markdown.publishSpecsAndWait
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableAlignmentControllerTest {
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
  fun `test a document change is picked up`(): Unit = timeoutRunBlocking {
    configureRaggedTable()
    withContext(Dispatchers.EDT) {
      awaitPads()
      val widthsBefore = padWidths()
      // Widen the last row's first cell past the current column width, so the column has to grow.
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        myFixture.editor.document.insertString(myFixture.editor.document.text.indexOf("ccc") + 3, "cccc")
      }
      PlatformTestUtil.waitWithEventsDispatching(
        "padding did not react to the document change",
        { padWidths() != widthsBefore },
        TIMEOUT_SECONDS,
      )
    }
  }

  @Test
  fun `test live preview folds are aligned inside the table`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertNothingLogged {
        // language=Markdown
        val content = """
          | aaa | x |
          | --- | - |
          | **bb** | y |
          """.trimIndent()
        myFixture.configureByText("some.md", content)
        myFixture.editor.setLivePreviewEnabledness(true)
        myFixture.editor.caretModel.moveToOffset(content.length)

        val reconciler = requireNotNull(MarkdownLivePreviewReconciler.getOrCreate(myFixture.editor))
        reconciler.publishSpecsAndWait(
          computeLivePreviewSpecs(myFixture.file, myFixture.editor, MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION)
        )
        awaitPads()
        assertBordersAligned()

        myFixture.editor.caretModel.moveToOffset(content.indexOf("**bb**") + 2)
        assertBordersAligned()

        myFixture.editor.caretModel.moveToOffset(content.length)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertBordersAligned()
      }
    }
  }

  private fun configureRaggedTable() {
    // language=Markdown
    myFixture.configureByText(
      "some.md",
      """
      | a | bb |
      |---|----|
      | ccc | d |
      """.trimIndent()
    )
  }

  private fun awaitPads() {
    PlatformTestUtil.waitWithEventsDispatching(
      "padding was never added, so the controller did not reach the model",
      { allPads().isNotEmpty() },
      TIMEOUT_SECONDS,
    )
  }

  private fun allPads() =
    myFixture.editor.inlayModel.getInlineElementsInRange(0, myFixture.editor.document.textLength, TablePadRenderer::class.java)

  private fun padWidths() = allPads().map { it.offset to it.widthInPixels }

  private fun assertBordersAligned() {
    val document = myFixture.editor.document
    val text = document.charsSequence
    val bordersByRow = (0 until document.lineCount)
      .map { line ->
        (document.getLineStartOffset(line) until document.getLineEndOffset(line)).filter { text[it] == '|' }
      }
      .filter { it.size >= 2 }
    val separatorCount = bordersByRow.minOf { it.size }
    for (index in 0 until separatorCount) {
      val xs = bordersByRow.map { borders -> renderedX(borders[index]) }
      assertEquals(1, xs.distinct().size, "separator #$index should stay aligned: $xs")
    }
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
