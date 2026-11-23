// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewReconciler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertNothingLogged
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownTestingUtil.paintInlay
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewConfiguration
import org.intellij.plugins.markdown.editor.livepreview.computeLivePreviewSpecs
import org.intellij.plugins.markdown.editor.livepreview.setLivePreviewEnabledness
import org.intellij.plugins.markdown.editor.tables.ui.presentation.HorizontalBarPresentation
import org.intellij.plugins.markdown.editor.tables.ui.presentation.VerticalBarPresentation
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.publishSpecsAndWait
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Suppress("MarkdownIncorrectTableFormatting")
@TestApplication
class MarkdownTableInlayHintsPassTest {
  private val myFixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    FontLayoutService.setInstance(MockFontLayoutService(CHAR_WIDTH, LINE_HEIGHT, DESCENT))
    disableVisualAlignment()
  }

  @AfterEach
  fun tearDown() {
    FontLayoutService.setInstance(null)
  }

  @Test
  fun `test the pass adds a bar that spans the header row`(): Unit = timeoutRunBlocking {
    configureTable()

    myFixture.doHighlighting()

    withContext(Dispatchers.EDT) {
      assertEquals(expectedBarWidth(), getInlayWidth())
    }
  }

  @Test
  fun `test the bar follows the header row over the next pass`(): Unit = timeoutRunBlocking {
    configureTable()
    myFixture.doHighlighting()
    withContext(Dispatchers.EDT) {
      val before = getInlayWidth()

      widenHeaderRow()
      myFixture.doHighlighting()

      assertTrue(getInlayWidth() > before, "the bar must grow with the header row, but it stayed $before")
      assertEquals(expectedBarWidth(), getInlayWidth())
    }
  }

  @Test
  fun `test a row bar of the next pass is a change only when its look changes`(): Unit = timeoutRunBlocking {
    configureTable()
    withContext(Dispatchers.EDT) {
      val table = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, MarkdownTable::class.java))
      val row = requireNotNull(table.headerRow)
      val tableRange = myFixture.editor.document.createRangeMarker(table.textRange)
      val previous = VerticalBarPresentation(myFixture.editor, row, hover = false, tableRangeMarker = tableRange)

      assertFalse(VerticalBarPresentation(myFixture.editor, row, hover = false, tableRangeMarker = tableRange).updateState(previous))
      assertTrue(VerticalBarPresentation(myFixture.editor, row, hover = true, tableRangeMarker = tableRange).updateState(previous))
    }
  }

  @Test
  fun `test the pass removes the bar when the table loses its borders`() {
    configureTable()
    myFixture.doHighlighting()
    assertNotNull(runReadActionBlocking { getInlay() })

    val document = myFixture.editor.document
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      document.deleteString(0, 1)
    }
    myFixture.doHighlighting()

    assertNull(runReadActionBlocking { getInlay() }, "a table without correct borders must have no bar")
  }

  @Test
  fun `test the mounted bar follows live preview folds`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertNothingLogged {
        // language=Markdown
        val content = """
          | **aa** | x |
          | --- | - |
          | value | y |
          """.trimIndent()
        myFixture.configureByText("some.md", content)
        myFixture.doHighlighting()
        myFixture.editor.setLivePreviewEnabledness(true)
        myFixture.editor.caretModel.moveToOffset(content.length)

        val reconciler = requireNotNull(MarkdownLivePreviewReconciler.getOrCreate(myFixture.editor))
        reconciler.publishSpecsAndWait(
          computeLivePreviewSpecs(myFixture.file, myFixture.editor, MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION)
        )
        val bar = requireNotNull(getInlay())
        paintInlay(bar)
        val hiddenWidth = bar.widthInPixels

        myFixture.editor.caretModel.moveToOffset(content.indexOf("**aa**") + 2)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        paintInlay(bar)
        assertTrue(bar.widthInPixels > hiddenWidth, "the mounted bar must include revealed table-cell markers")

        myFixture.editor.caretModel.moveToOffset(content.length)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        paintInlay(bar)
        assertEquals(hiddenWidth, bar.widthInPixels)
        assertEquals(content, myFixture.editor.document.text)
      }
    }
  }

  @Test
  fun `test the pass adds the bar in dumb mode`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, disposable)
      DumbModeTestUtils.runInDumbModeSynchronously(myFixture.project) {
        configureTable()

        myFixture.doHighlighting()

        assertEquals(expectedBarWidth(), getInlayWidth())
      }
    }
  }

  private fun configureTable() {
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

  private fun widenHeaderRow() {
    val document = myFixture.editor.document
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      document.insertString(document.text.indexOf('a'), "wider ")
    }
  }

  private fun getInlay(): Inlay<*>? {
    val editor = myFixture.editor
    return editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength).singleOrNull()
  }

  @RequiresEdt
  private fun getInlayWidth(): Int {
    val bar = requireNotNull(getInlay()) { "the table must have a horizontal bar" }
    assertEquals("HorizontalBarPresentation", bar.renderer.toString())
    paintInlay(bar)
    return bar.widthInPixels
  }

  /**
   * The bar spans the header row and the pass insets it by [HorizontalBarPresentation.leftPadding].
   * Every character is [CHAR_WIDTH] wide, and the visual alignment adds no padding here.
   */
  private fun expectedBarWidth(): Int {
    val table = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, MarkdownTable::class.java))
    val header = requireNotNull(table.headerRow)
    return HorizontalBarPresentation.leftPadding + header.textRange.length * CHAR_WIDTH
  }

  /** The visual alignment pads the cells with its own inlays after a commit, which makes the bar width move. */
  private fun disableVisualAlignment() {
    val settings = MarkdownSettings.getInstance()
    val previous = settings.alignTableCellsVisually
    settings.alignTableCellsVisually = false
    Disposer.register(disposable) { settings.alignTableCellsVisually = previous }
  }

  private companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    const val CHAR_WIDTH = 10
    const val LINE_HEIGHT = 10
    const val DESCENT = 2
  }
}
