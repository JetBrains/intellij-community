// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.codeInsight.hints.presentation.BasePresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.application.impl.TestOnlyThreading
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.PlatformTestUtil
import org.intellij.plugins.markdown.editor.tables.ui.presentation.HorizontalBarPresentation
import org.intellij.plugins.markdown.editor.tables.ui.presentation.VerticalBarPresentation
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicReference

class MarkdownTablePresentationStalePsiTest : LightPlatformCodeInsightTestCase() {
  fun `test painting a horizontal table bar does not access PSI outside read action`() {
    // language=Markdown
    configureFromFileText(
      "some.md",
      """
      | header |
      |--------|
      | value  |
      """.trimIndent()
    )
    val table = requireNotNull(PsiTreeUtil.findChildOfType(file, MarkdownTable::class.java))
    val presentation = HorizontalBarPresentation(editor, table)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertTrue(presentation.width > 0)

    paintInEditor(presentation)
  }

  fun `test hovering an invalidated row bar does not access stale PSI`() {
    // language=Markdown
    configureFromFileText(
      "some.md",
      """
      | header |
      |--------|
      | value  |
      """.trimIndent()
    )
    val table = requireNotNull(PsiTreeUtil.findChildOfType(file, MarkdownTable::class.java))
    val row = requireNotNull(table.headerRow)
    val presentation = VerticalBarPresentation.create(PresentationFactory(editor), editor, row)

    WriteCommandAction.runWriteCommandAction(project) {
      editor.document.setText("plain text")
    }
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)

    presentation.mouseMoved(
      MouseEvent(editor.contentComponent, MouseEvent.MOUSE_MOVED, 0, 0, 0, 0, 0, false),
      Point(),
    )
  }

  fun `test painting invalidated table bars does not access stale PSI`() {
    // language=Markdown
    configureFromFileText(
      "some.md",
      """
      | header |
      |--------|
      | value  |
      """.trimIndent()
    )
    val table = requireNotNull(PsiTreeUtil.findChildOfType(file, MarkdownTable::class.java))
    val rowPresentation = VerticalBarPresentation(editor, requireNotNull(table.getRows(true).lastOrNull()), hover = false)
    val columnPresentation = HorizontalBarPresentation(editor, table)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertTrue(rowPresentation.width > 0)
    assertTrue(columnPresentation.width > 0)

    WriteCommandAction.runWriteCommandAction(project) {
      editor.document.setText("plain text")
    }
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)

    paint(rowPresentation)
    paint(columnPresentation)
  }

  private fun paint(presentation: BasePresentation) {
    val graphics = BufferedImage(presentation.width, presentation.height, BufferedImage.TYPE_INT_ARGB).createGraphics()
    try {
      presentation.paint(graphics, TextAttributes())
    }
    finally {
      graphics.dispose()
    }
  }

  private fun paintInEditor(presentation: BasePresentation) {
    editor.inlayModel.addInlineElement(0, object : EditorCustomElementRenderer {
      override fun calcWidthInPixels(inlay: Inlay<*>): Int = presentation.width

      override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
        presentation.paint(g, textAttributes)
      }
    })
    val graphics = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB).createGraphics()
    try {
      val error = AtomicReference<Throwable?>()
      LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): MutableSet<Action> {
          error.set(t)
          return Action.NONE
        }
      }) {
        TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
          editor.contentComponent.paint(graphics)
        }
      }
      assertNull(error.get())
    }
    finally {
      graphics.dispose()
    }
  }
}
