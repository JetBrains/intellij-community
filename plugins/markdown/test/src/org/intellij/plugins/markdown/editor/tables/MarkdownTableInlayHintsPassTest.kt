// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.markdown.backend.editor.livepreview.computeLivePreviewSpecs
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewReconciler
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.util.Disposer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertNothingLogged
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.editor.livepreview.enableLivePreview
import org.intellij.plugins.markdown.editor.tables.ui.presentation.HorizontalBarPresentation
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings

@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableInlayHintsPassTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    FontLayoutService.setInstance(MockFontLayoutService(CHAR_WIDTH, LINE_HEIGHT, DESCENT))
    disableVisualAlignment()
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

  fun `test the pass adds a bar that spans the header row`() {
    configureTable()

    myFixture.doHighlighting()

    assertEquals(expectedBarWidth(), getInlayWidth())
  }

  fun `test the bar follows the header row over the next pass`() {
    configureTable()
    myFixture.doHighlighting()
    val before = getInlayWidth()

    widenHeaderRow()
    myFixture.doHighlighting()

    assertTrue("the bar must grow with the header row, but it stayed $before", getInlayWidth() > before)
    assertEquals(expectedBarWidth(), getInlayWidth())
  }

  fun `test the pass removes the bar when the table loses its borders`() {
    configureTable()
    myFixture.doHighlighting()
    assertNotNull(getInlay())

    val document = myFixture.editor.document
    WriteCommandAction.runWriteCommandAction(project) {
      document.deleteString(0, 1)
    }
    myFixture.doHighlighting()

    assertNull("a table without correct borders must have no bar", getInlay())
  }

  fun `test the mounted bar follows live preview folds`() = assertNothingLogged {
    // language=Markdown
    val content = """
      | **aa** | x |
      | --- | - |
      | value | y |
      """.trimIndent()
    myFixture.configureByText("some.md", content)
    myFixture.doHighlighting()
    myFixture.editor.enableLivePreview()
    myFixture.editor.caretModel.moveToOffset(content.length)

    val reconciler = requireNotNull(MarkdownLivePreviewReconciler.getOrCreate(myFixture.editor))
    reconciler.publishSpecs(computeLivePreviewSpecs(myFixture.file, myFixture.editor))
    val bar = requireNotNull(getInlay())
    val hiddenWidth = bar.widthInPixels

    myFixture.editor.caretModel.moveToOffset(content.indexOf("**aa**") + 2)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertTrue("the mounted bar must include revealed table-cell markers", bar.widthInPixels > hiddenWidth)

    myFixture.editor.caretModel.moveToOffset(content.length)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertEquals(hiddenWidth, bar.widthInPixels)
    assertEquals(content, myFixture.editor.document.text)
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
    WriteCommandAction.runWriteCommandAction(project) {
      document.insertString(document.text.indexOf('a'), "wider ")
    }
  }

  private fun getInlay(): Inlay<*>? {
    val editor = myFixture.editor
    return editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength).singleOrNull()
  }

  private fun getInlayWidth(): Int {
    val bar = requireNotNull(getInlay()) { "the table must have a horizontal bar" }
    assertEquals("HorizontalBarPresentation", bar.renderer.toString())
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
    val settings = MarkdownApplicationSettings.getInstance()
    val previous = settings.alignTableCellsVisually
    settings.alignTableCellsVisually = false
    Disposer.register(testRootDisposable) { settings.alignTableCellsVisually = previous }
  }

  private companion object {
    const val CHAR_WIDTH = 10
    const val LINE_HEIGHT = 10
    const val DESCENT = 2
  }
}
