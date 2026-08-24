// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui.alignment

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.MockFontLayoutService

@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableAlignmentTypingTest : LightPlatformCodeInsightTestCase() {
  private var lastChangeWasHandled = false

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

  fun `test separators hold until the padding really runs out`() {
    configureAlignedRaggedTable()
    val before = separatorPositions()
    // The header's first cell can absorb exactly two characters before it matches "ccc" below it.
    type("x", at = offsetAfter("| a"))
    assertEquals(before, separatorPositions())
    type("y", at = offsetAfter("| ax"))
    assertEquals(before, separatorPositions())
    type("z", at = offsetAfter("| axy"))
    assertTrue("the column had to grow, so separators after it should have moved", separatorPositions() != before)
  }

  fun `test deleting grows the padding instead of pulling the separators back`() {
    configureAlignedRaggedTable()
    val before = separatorPositions()
    val offset = editor.document.text.indexOf("ccc")
    WriteCommandAction.runWriteCommandAction(project) {
      editor.document.deleteString(offset, offset + 1)
    }
    assertEquals(before, separatorPositions())
  }

  fun `test typing outside a table is not claimed by the single cell path`() {
    // language=Markdown
    configureAndAlign(
      """
      Some prose.

      | a | bb |
      |---|----|
      | ccc | d |
      """.trimIndent()
    )
    val before = separatorPositions()
    type("!", at = offsetAfter("Some prose"))
    assertFalse(lastChangeWasHandled)
    assertEquals(before, separatorPositions())
  }

  fun `test typing works when tables were discovered in reverse document order`() {
    // language=Markdown
    configureFromFileText(
      "some.md",
      """
      | a | bb |
      |---|----|
      | ccc | d |

      | a | bb |
      |---|----|
      | ccc | d |
      """.trimIndent()
    )
    val secondTableStart = editor.document.text.lastIndexOf("| a | bb |")
    model.refresh(TextRange(secondTableStart, editor.document.textLength))
    model.refresh(TextRange(0, secondTableStart))
    startUpdatingEditedCell()

    type("x", at = offsetAfter("| a"))

    assertTrue(lastChangeWasHandled)
  }

  fun `test typing inside a collapsed region changes nothing`() {
    configureAlignedRaggedTable()
    val start = editor.document.text.indexOf("ccc")
    editor.foldingModel.runBatchFoldingOperation {
      val region = requireNotNull(editor.foldingModel.addFoldRegion(start, start + 3, "..."))
      region.isExpanded = false
    }
    model.refresh(TextRange(0, editor.document.textLength))
    val collapsed = separatorPositions()
    type("qqqq", at = start + 1)
    assertTrue("a change hidden inside a collapsed region should be claimed and ignored", lastChangeWasHandled)
    assertEquals("a change hidden inside a collapsed region changed nothing on screen", collapsed, separatorPositions())
  }

  fun `test the single cell path stands down once a line is added`() {
    // language=Markdown
    configureAndAlign(
      """
      Prose.

      | a | bb |
      |---|----|
      | ccc | d |
      """.trimIndent()
    )
    type("x", at = offsetAfter("| a"))
    assertTrue("a plain edit inside a cell should be handled in place", lastChangeWasHandled)
    type("\n", at = offsetAfter("Prose."))
    assertFalse("an edit that adds a line cannot be handled in place", lastChangeWasHandled)
    type("y", at = offsetAfter("| ax"))
    assertFalse("the geometry is still stale, so the fast path must keep standing down", lastChangeWasHandled)
    commitDocument()
    model.refresh(TextRange(0, editor.document.textLength))
    type("z", at = offsetAfter("| axy"))
    assertTrue("a full refresh should make the fast path usable again", lastChangeWasHandled)
  }

  private fun configureAlignedRaggedTable() {
    // language=Markdown
    configureAndAlign(
      """
      | a | bb |
      |---|----|
      | ccc | d |
      """.trimIndent()
    )
  }

  private fun configureAndAlign(text: String) {
    configureFromFileText("some.md", text)
    model.refresh(TextRange(0, editor.document.textLength))
    startUpdatingEditedCell()
  }

  private fun startUpdatingEditedCell() {
    editor.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        lastChangeWasHandled = model.updateEditedCell(event)
      }
    }, testRootDisposable)
  }

  private val model: MarkdownTableAlignmentModel by lazy {
    MarkdownTableAlignmentModel(editor).also { Disposer.register(testRootDisposable, it) }
  }

  private fun offsetAfter(text: String): Int {
    val index = editor.document.text.indexOf(text)
    assertTrue("'$text' not found in ${editor.document.text}", index >= 0)
    return index + text.length
  }

  private fun type(text: String, at: Int) {
    WriteCommandAction.runWriteCommandAction(project) {
      editor.document.insertString(at, text)
    }
  }

  private fun commitDocument() {
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
  }

  private fun separatorPositions(): List<List<Int>> {
    val document = editor.document
    val text = document.charsSequence
    return (0 until document.lineCount)
      .map { line ->
        (document.getLineStartOffset(line) until document.getLineEndOffset(line)).filter { text[it] == '|' }
      }
      .filter { it.size >= 2 }
      .map { borders -> borders.map { renderedX(it) } }
  }

  private fun renderedX(offset: Int): Int {
    return editor.offsetToXY(offset, false, false).x +
           editor.inlayModel.getInlineElementsInRange(offset, offset).sumOf { it.widthInPixels }
  }

  private companion object {
    const val CHAR_WIDTH = 10
    const val LINE_HEIGHT = 10
    const val DESCENT = 2
  }
}
