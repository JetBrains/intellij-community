// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui.alignment

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.PlatformTestUtil

@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableAlignmentControllerTest : LightPlatformCodeInsightTestCase() {
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

  fun `test a document change is picked up`() {
    configureRaggedTable()
    awaitPads()
    val widthsBefore = padWidths()
    // Widen the last row's first cell past the current column width, so the column has to grow.
    WriteCommandAction.runWriteCommandAction(project) {
      editor.document.insertString(editor.document.text.indexOf("ccc") + 3, "cccc")
    }
    PlatformTestUtil.waitWithEventsDispatching(
      "padding did not react to the document change",
      { padWidths() != widthsBefore },
      TIMEOUT_SECONDS,
    )
  }

  private fun configureRaggedTable() {
    // language=Markdown
    configureFromFileText(
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
    editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength, TablePadRenderer::class.java)

  private fun padWidths() = allPads().map { it.offset to it.widthInPixels }

  private companion object {
    const val CHAR_WIDTH = 10
    const val LINE_HEIGHT = 10
    const val DESCENT = 2
    const val TIMEOUT_SECONDS = 10
  }
}
