// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.intellij.plugins.markdown.editor.tables.ui.presentation.HorizontalBarPresentation
import org.intellij.plugins.markdown.editor.tables.ui.presentation.TableInlayProperties
import org.intellij.plugins.markdown.editor.tables.ui.presentation.VerticalBarPresentation
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.junit.jupiter.api.Test
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage

@TestApplication
internal class MarkdownTableSelectionTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project get() = projectFixture.get()

  private fun multiSelectModifier(): Int = if (ClientSystemInfo.isMac()) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK

  @Test
  fun `control click toggles rows and shift click selects a range`(): Unit = timeoutRunBlocking {
    val source = """
      | h1 | h2 | h3 |
      |----|----|----|
      | a  | b  | c  |
      | d  | e  | f  |
    """.trimIndent()
    val file = readAction {
      PsiFileFactory.getInstance(project).createFileFromText("table.md", MarkdownFileType.INSTANCE, source)
    }
    val table = readAction { requireNotNull(PsiTreeUtil.findChildOfType(file, MarkdownTable::class.java)) }
    val rows = readAction {
      buildList<PsiElement> {
        addAll(table.getRows(true))
        table.separatorRow?.let(::add)
      }.sortedBy { it.textRange.startOffset }
    }
    val document = EditorFactory.getInstance().createDocument(source)

    withContext(Dispatchers.EDT) {
      val editor = EditorFactory.getInstance().createEditor(document, project)
      try {
        table.selectRow(editor, rows.first())
        val presentation = VerticalBarPresentation.create(
          PresentationFactory(editor), editor, rows[2], editor.document.createRangeMarker(table.textRange)
        )
        presentation.mouseClickAfterPress(editor, mouseClickEvent(editor, multiSelectModifier()), Point(3, 3))
        val toggledRows = editor.selectionRanges()
        assertThat(toggledRows).containsExactlyInAnyOrder(rows.first().textRange, rows[2].textRange)

        presentation.mouseClickAfterPress(editor, mouseClickEvent(editor, multiSelectModifier(), clickCount = 2), Point(3, 3))
        assertThat(editor.selectionRanges()).containsExactly(rows.first().textRange)

        presentation.mouseClickAfterPress(editor, mouseClickEvent(editor, multiSelectModifier(), clickCount = 3), Point(3, 3))
        assertThat(editor.selectionRanges()).containsExactlyElementsOf(toggledRows)

        val lastPresentation = VerticalBarPresentation.create(
          PresentationFactory(editor), editor, rows.last(), editor.document.createRangeMarker(table.textRange)
        )
        lastPresentation.mouseClickAfterPress(editor, mouseClickEvent(editor, InputEvent.SHIFT_DOWN_MASK), Point(3, 3))
        assertThat(editor.selectionRanges()).containsExactly(TextRange(rows[2].textRange.startOffset, rows.last().textRange.endOffset))

        editor.caretModel.removeSecondaryCarets()
        editor.caretModel.currentCaret.removeSelection()
        val firstPresentation = VerticalBarPresentation.create(
          PresentationFactory(editor), editor, rows.first(), editor.document.createRangeMarker(table.textRange)
        )
        firstPresentation.mouseClickAfterPress(editor, mouseClickEvent(editor, multiSelectModifier()), Point(3, 3))
        assertThat(editor.selectionRanges()).containsExactly(rows.first().textRange)
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }

  @Test
  fun `control click toggles columns and shift click selects a range`(): Unit = timeoutRunBlocking {
    val source = """
      | h1 | h2 | h3 |
      |----|----|----|
      | a  | b  | c  |
      | d  | e  | f  |
    """.trimIndent()
    val file = readAction {
      PsiFileFactory.getInstance(project).createFileFromText("table.md", MarkdownFileType.INSTANCE, source)
    }
    val table = readAction { requireNotNull(PsiTreeUtil.findChildOfType(file, MarkdownTable::class.java)) }
    val document = EditorFactory.getInstance().createDocument(source)

    withContext(Dispatchers.EDT) {
      val editor = EditorFactory.getInstance().createEditor(document, project)
      try {
        table.selectColumn(editor, 0, withHeader = true, withSeparator = true, withBorders = true)
        table.selectColumn(
          editor,
          2,
          withHeader = true,
          withSeparator = true,
          withBorders = true,
          selectionMode = mouseClickMode(editor, multiSelectModifier())
        )
        val separateColumns = editor.selectionTexts()
        assertThat(separateColumns).hasSize(8)
        assertThat(separateColumns.joinToString()).contains("h1", "h3").doesNotContain("h2")

        table.selectColumn(
          editor,
          1,
          withHeader = true,
          withSeparator = true,
          withBorders = true,
          selectionMode = mouseClickMode(editor, InputEvent.SHIFT_DOWN_MASK)
        )
        val columnRange = editor.selectionTexts()
        assertThat(columnRange).hasSize(4)
        assertThat(columnRange.joinToString()).contains("h2", "h3").doesNotContain("h1")
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }

  @Test
  fun `column bar clicks preserve selection when the editor clears it on press`(): Unit = timeoutRunBlocking {
    val source = """
      | h1 | h2 | h3 |
      |----|----|----|
      | a  | b  | c  |
      | d  | e  | f  |
    """.trimIndent()
    val file = readAction {
      PsiFileFactory.getInstance(project).createFileFromText("table.md", MarkdownFileType.INSTANCE, source)
    }
    val table = readAction { requireNotNull(PsiTreeUtil.findChildOfType(file, MarkdownTable::class.java)) }
    val document = EditorFactory.getInstance().createDocument(source)

    withContext(Dispatchers.EDT) {
      val editor = EditorFactory.getInstance().createEditor(document, project)
      val presentation = HorizontalBarPresentation.create(PresentationFactory(editor), editor, table)
      val graphics = BufferedImage(500, 30, BufferedImage.TYPE_INT_ARGB).createGraphics()
      try {
        table.selectColumn(editor, 0, withHeader = true, withSeparator = true, withBorders = true)
        presentation.paint(graphics, TextAttributes())

        val secondColumnPoint = columnBarPoint(editor, table, 1)
        presentation.mouseClickAfterPress(editor, mouseClickEvent(editor, multiSelectModifier()), secondColumnPoint)
        val toggledColumns = editor.selectionTexts().joinToString()
        assertThat(toggledColumns).contains("h1", "h2").doesNotContain("h3")

        val thirdColumnPoint = columnBarPoint(editor, table, 2)
        presentation.mouseClickAfterPress(editor, mouseClickEvent(editor, InputEvent.SHIFT_DOWN_MASK), thirdColumnPoint)
        val columnRange = editor.selectionTexts().joinToString()
        assertThat(columnRange).contains("h2", "h3").doesNotContain("h1")
      }
      finally {
        graphics.dispose()
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }

  @Test
  fun `column range selects available cells in a short row`(): Unit = timeoutRunBlocking {
    val source = """
      | h1 | h2 | h3 |
      |----|----|----|
      | a  | b  |
    """.trimIndent()
    val file = readAction {
      PsiFileFactory.getInstance(project).createFileFromText("table.md", MarkdownFileType.INSTANCE, source)
    }
    val table = readAction { requireNotNull(PsiTreeUtil.findChildOfType(file, MarkdownTable::class.java)) }
    val document = EditorFactory.getInstance().createDocument(source)

    withContext(Dispatchers.EDT) {
      val editor = EditorFactory.getInstance().createEditor(document, project)
      try {
        table.selectColumn(editor, 0, withHeader = true, withSeparator = true, withBorders = true)
        table.selectColumn(
          editor,
          2,
          withHeader = true,
          withSeparator = true,
          withBorders = true,
          selectionMode = mouseClickMode(editor, InputEvent.SHIFT_DOWN_MASK)
        )

        val selectedText = editor.selectionTexts()
        assertThat(selectedText).hasSize(3)
        assertThat(selectedText.joinToString()).contains("a", "b")
      }
      finally {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }

  private fun Editor.selectionRanges(): List<TextRange> = caretModel.allCarets.asSequence()
    .filter { it.hasSelection() }
    .map { TextRange(it.selectionStart, it.selectionEnd) }
    .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))
    .toList()

  private fun Editor.selectionTexts(): List<String> = selectionRanges().map(document::getText)

  private fun InlayPresentation.mouseClickAfterPress(editor: Editor, event: MouseEvent, point: Point) {
    UIUtil.dispatchAllInvocationEvents()
    mousePressed(mouseClickEvent(editor, event.modifiersEx, event.clickCount, MouseEvent.MOUSE_PRESSED), point)
    editor.caretModel.removeSecondaryCarets()
    editor.caretModel.currentCaret.removeSelection()
    mouseClicked(event, point)
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun columnBarPoint(editor: Editor, table: MarkdownTable, columnIndex: Int): Point {
    val offset = requireNotNull(table.headerRow?.getCell(columnIndex)).textRange.startOffset
    return editor.offsetToXY(offset).apply {
      translate(HorizontalBarPresentation.leftPadding, TableInlayProperties.topDownPadding + 1)
    }
  }

  private fun mouseClickMode(editor: Editor, modifiers: Int): TableSelectionMode = mouseClickEvent(editor, modifiers).toTableSelectionMode()

  private fun mouseClickEvent(
    editor: Editor,
    modifiers: Int,
    clickCount: Int = 1,
    id: Int = MouseEvent.MOUSE_CLICKED
  ): MouseEvent = MouseEvent(
    editor.contentComponent,
    id,
    0,
    modifiers,
    0,
    0,
    clickCount,
    false,
    MouseEvent.BUTTON1
  )
}
