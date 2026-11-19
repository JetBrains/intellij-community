// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.tables

import com.intellij.codeInsight.hints.presentation.BasePresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.TestOnlyThreading
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownTestingUtil.paintPresentation
import org.intellij.plugins.markdown.editor.tables.ui.presentation.HorizontalBarPresentation
import org.intellij.plugins.markdown.editor.tables.ui.presentation.VerticalBarPresentation
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicReference

@TestApplication
class MarkdownTablePresentationStalePsiTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test painting a horizontal table bar does not access PSI outside read action`(): Unit = timeoutRunBlocking {
    // language=Markdown
    myFixture.configureByText(
      "some.md",
      """
      | header |
      |--------|
      | value  |
      """.trimIndent()
    )
    withContext(Dispatchers.EDT) {
      val table = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, MarkdownTable::class.java))
      val presentation = HorizontalBarPresentation(myFixture.editor, table)
      paintPresentation(presentation)
      assertTrue(presentation.width > 0)

      paintInEditor(presentation)
    }
  }

  @Test
  fun `test hovering an invalidated row bar does not access stale PSI`(): Unit = timeoutRunBlocking {
    // language=Markdown
    myFixture.configureByText(
      "some.md",
      """
      | header |
      |--------|
      | value  |
      """.trimIndent()
    )
    withContext(Dispatchers.EDT) {
      val table = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, MarkdownTable::class.java))
      val row = requireNotNull(table.headerRow)
      val presentation = VerticalBarPresentation.create(PresentationFactory(myFixture.editor), myFixture.editor, row, tableRangeMarker = myFixture.editor.document.createRangeMarker(table.textRange))

      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        myFixture.editor.document.setText("plain text")
      }
      PsiDocumentManager.getInstance(myFixture.project).commitDocument(myFixture.editor.document)

      presentation.mouseMoved(
        MouseEvent(myFixture.editor.contentComponent, MouseEvent.MOUSE_MOVED, 0, 0, 0, 0, 0, false),
        Point(),
      )
    }
  }

  @Test
  fun `test painting invalidated table bars does not access stale PSI`(): Unit = timeoutRunBlocking {
    // language=Markdown
    myFixture.configureByText(
      "some.md",
      """
      | header |
      |--------|
      | value  |
      """.trimIndent()
    )
    withContext(Dispatchers.EDT) {
      val table = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, MarkdownTable::class.java))
      val rowPresentation = VerticalBarPresentation(myFixture.editor, requireNotNull(table.getRows(true).lastOrNull()), hover = false, tableRangeMarker = myFixture.editor.document.createRangeMarker(table.textRange))
      val columnPresentation = HorizontalBarPresentation(myFixture.editor, table)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      paintPresentation(columnPresentation)
      assertTrue(rowPresentation.width > 0)
      assertTrue(columnPresentation.width > 0)

      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        myFixture.editor.document.setText("plain text")
      }
      PsiDocumentManager.getInstance(myFixture.project).commitDocument(myFixture.editor.document)

      paintPresentation(rowPresentation)
      paintPresentation(columnPresentation)
    }
  }

  private fun paintInEditor(presentation: BasePresentation) {
    myFixture.editor.inlayModel.addInlineElement(0, object : EditorCustomElementRenderer {
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
          myFixture.editor.contentComponent.paint(graphics)
        }
      }
      assertNull(error.get())
    }
    finally {
      graphics.dispose()
    }
  }
}
