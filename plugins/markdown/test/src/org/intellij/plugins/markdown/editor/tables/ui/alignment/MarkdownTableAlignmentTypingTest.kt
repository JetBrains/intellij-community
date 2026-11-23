// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables.ui.alignment

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting")
class MarkdownTableAlignmentTypingTest {
  private val myFixture by codeInsightModuleFixture(projectFixture)

  private var lastChangeWasHandled = false

  @BeforeEach
  fun setUp() {
    FontLayoutService.setInstance(MockFontLayoutService(CHAR_WIDTH, LINE_HEIGHT, DESCENT))
  }

  @AfterEach
  fun tearDown() {
    FontLayoutService.setInstance(null)
  }

  @Test
  fun `test separators hold until the padding really runs out`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configureAlignedRaggedTable()
      val before = separatorPositions()
      // The header's first cell can absorb exactly two characters before it matches "ccc" below it.
      type("x", at = offsetAfter("| a"))
      assertEquals(before, separatorPositions())
      type("y", at = offsetAfter("| ax"))
      assertEquals(before, separatorPositions())
      type("z", at = offsetAfter("| axy"))
      assertTrue(separatorPositions() != before, "the column had to grow, so separators after it should have moved")
    }
  }

  @Test
  fun `test deleting grows the padding instead of pulling the separators back`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configureAlignedRaggedTable()
      val before = separatorPositions()
      val offset = myFixture.editor.document.text.indexOf("ccc")
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        myFixture.editor.document.deleteString(offset, offset + 1)
      }
      assertEquals(before, separatorPositions())
    }
  }

  @Test
  fun `test typing outside a table is not claimed by the single cell path`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
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
  }

  @Test
  fun `test typing works when tables were discovered in reverse document order`(): Unit = timeoutRunBlocking {
    // language=Markdown
    myFixture.configureByText(
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
    withContext(Dispatchers.EDT) {
      val secondTableStart = myFixture.editor.document.text.lastIndexOf("| a | bb |")
      model.refresh(TextRange(secondTableStart, myFixture.editor.document.textLength))
      model.refresh(TextRange(0, secondTableStart))
      startUpdatingEditedCell()

      type("x", at = offsetAfter("| a"))

      assertTrue(lastChangeWasHandled)
    }
  }

  @Test
  fun `test typing inside a collapsed region changes nothing`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configureAlignedRaggedTable()
      val start = myFixture.editor.document.text.indexOf("ccc")
      myFixture.editor.foldingModel.runBatchFoldingOperation {
        val region = requireNotNull(myFixture.editor.foldingModel.addFoldRegion(start, start + 3, "..."))
        region.isExpanded = false
      }
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
      val collapsed = separatorPositions()
      type("qqqq", at = start + 1)
      assertTrue(lastChangeWasHandled, "a change hidden inside a collapsed region should be claimed and ignored")
      assertEquals(collapsed, separatorPositions(), "a change hidden inside a collapsed region changed nothing on screen")
    }
  }

  @Test
  fun `test the single cell path stands down once a line is added`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
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
      assertTrue(lastChangeWasHandled, "a plain edit inside a cell should be handled in place")
      type("\n", at = offsetAfter("Prose."))
      assertFalse(lastChangeWasHandled, "an edit that adds a line cannot be handled in place")
      type("y", at = offsetAfter("| ax"))
      assertFalse(lastChangeWasHandled, "the geometry is still stale, so the fast path must keep standing down")
      commitDocument()
      model.refresh(TextRange(0, myFixture.editor.document.textLength))
      type("z", at = offsetAfter("| axy"))
      assertTrue(lastChangeWasHandled, "a full refresh should make the fast path usable again")
    }
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
    myFixture.configureByText("some.md", text)
    model.refresh(TextRange(0, myFixture.editor.document.textLength))
    startUpdatingEditedCell()
  }

  private fun startUpdatingEditedCell() {
    myFixture.editor.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        lastChangeWasHandled = model.updateEditedCell(event)
      }
    }, myFixture.testRootDisposable)
  }

  private val model: MarkdownTableAlignmentModel by lazy {
    MarkdownTableAlignmentModel(myFixture.editor).also { Disposer.register(myFixture.testRootDisposable, it) }
  }

  private fun offsetAfter(text: String): Int {
    val index = myFixture.editor.document.text.indexOf(text)
    assertTrue(index >= 0, "'$text' not found in ${myFixture.editor.document.text}")
    return index + text.length
  }

  private fun type(text: String, at: Int) {
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      myFixture.editor.document.insertString(at, text)
    }
  }

  private fun commitDocument() {
    PsiDocumentManager.getInstance(myFixture.project).commitDocument(myFixture.editor.document)
  }

  private fun separatorPositions(): List<List<Int>> {
    val document = myFixture.editor.document
    val text = document.charsSequence
    return (0 until document.lineCount)
      .map { line ->
        (document.getLineStartOffset(line) until document.getLineEndOffset(line)).filter { text[it] == '|' }
      }
      .filter { it.size >= 2 }
      .map { borders -> borders.map { renderedX(it) } }
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
  }
}
