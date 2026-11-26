// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.idea.TestFor
import com.intellij.markdown.frontend.editor.livepreview.CodeFenceRenderingService
import com.intellij.markdown.frontend.editor.livepreview.CodeFenceView
import com.intellij.markdown.frontend.editor.livepreview.MarkdownBlockQuotePainter
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewBulletInlayRenderer
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewCheckboxInlayRenderer
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewCodePaddingInlayRenderer
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewImageInlayRenderer
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewListIndentInlayRenderer
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewReconciler
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FoldingKeys
import com.intellij.openapi.editor.impl.SoftWrapModelImpl
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType
import com.intellij.openapi.editor.impl.view.IterationState
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TrustedProjectsTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.assertNothingLogged
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.EditorMouseFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.intellij.plugins.markdown.publishSpecsAndWait
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.abs

@TestApplication
class MarkdownLivePreviewFoldingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  private var configuration = MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION

  @BeforeEach
  fun setUp() {
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorCreated(event: EditorFactoryEvent) {
        if (event.editor.editorKind == EditorKind.UNTYPED) event.editor.setLivePreviewEnabledness(true, configuration)
      }
    }, disposable)
  }

  @Test
  fun testMarkupIsHiddenWhileTheCaretIsElsewhere(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("Some **bold**, *italic* and `code` here<caret>")
      assertEquals("Some bold, italic and code here", visibleText())
      assertTrue(concealedLivePreviewRegions(myFixture.editor).none { FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND.isIn(it) })
    }
  }

  @Test
  fun testCodeSpanBackgroundStartsAfterTheHiddenBacktick(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("a `x    y` b<caret>")
      val editor = myFixture.editor
      editor.settings.isCaretRowShown = false
      editor.colorsScheme.setAttributes(MarkdownHighlighterColors.CODE_SPAN, TextAttributes(null, Color.RED, null, null, Font.PLAIN))

      val image = MarkdownTestingUtil.paintEditor(editor)
      val content = editor.offsetToXY(3)
      val y = content.y + editor.lineHeight / 2

      assertEquals(Color.RED.rgb, image.getRGB(editor.offsetToXY(5).x, y), "A space inside the span must have the code background")
      assertTrue(Color.RED.rgb != image.getRGB(content.x - 2, y), "The space before the hidden backtick must keep the editor background")
    }
  }

  @Test
  fun testTopLevelHeadingsUseHtmlHeadingStyles(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = (1..6).joinToString("\n") { "#".repeat(it) + " title" } + "\n\nbody\nmore body"
      configure("$content<caret>")

      val document = myFixture.editor.document
      val folds = headingFolds()
      assertEquals((0..5).map { document.getLineStartOffset(it) }, folds.map { it.startOffset })
      assertEquals((0..5).map { document.getLineEndOffset(it) }, folds.map { it.endOffset })
      val heights = folds.map { it.heightInPixels }
      assertEquals(heights.sortedDescending(), heights)
      assertTrue(heights.first() > heights.last(), "The HTML h1 style must be larger than the h6 style")
      assertEquals(content, document.text)
    }
  }

  @Test
  fun testHeadingRevealKeepsSurroundingLinesAtEveryCaretOffset(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val heading = "# A **bold** heading ###"
      val content = "before\n$heading\n\nafter"
      configure("$content<caret>")
      val renderer = headingFolds().single().renderer
      val beforeY = myFixture.editor.offsetToXY(0).y
      val afterY = myFixture.editor.offsetToXY(content.indexOf("after")).y
      for (offset in "before\n".length..content.indexOf("\n\n")) {
        myFixture.editor.caretModel.moveToOffset(offset)
        assertThat(headingFolds()).describedAs("The source must appear immediately").isEmpty()
        assertEquals(offset, myFixture.editor.caretModel.offset)
        assertEquals(beforeY, myFixture.editor.offsetToXY(0).y)
        assertEquals(afterY, myFixture.editor.offsetToXY(content.indexOf("after")).y)
        moveCaretTo(content.length)
        assertEquals(renderer, headingFolds().single().renderer)
        assertEquals(afterY, myFixture.editor.offsetToXY(content.indexOf("after")).y)
      }
    }
  }

  @Test
  fun testLateSpecsKeepViewportAtDocumentTop(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val text = "Some long text goes here"
      val content = "# Heading\n\n$text" + "\nline".repeat(60)
      val specs = configureWithLateSpecs(content, content.indexOf(text))
      val editor = myFixture.editor
      editor.scrollingModel.scrollVertically(0)

      MarkdownLivePreviewReconciler.getExisting(editor)!!.publishSpecsAndWait(specs)

      assertTrue(headingFolds().single().heightInPixels > editor.lineHeight, "The heading must be taller than a line")
      assertEquals(0, editor.scrollingModel.verticalScrollOffset)
    }
  }

  @Test
  fun testLateSpecsKeepCaretScreenPositionBelowTop(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val text = "Some long text goes here"
      val content = "# Heading\n\n" + "line\n".repeat(20) + text + "\nline".repeat(60)
      val specs = configureWithLateSpecs(content, content.indexOf(text))
      val editor = myFixture.editor
      val caretLine = editor.caretModel.visualPosition.line
      editor.scrollingModel.scrollVertically(editor.visualLineToY(caretLine - 3))
      val caretScreenY = editor.visualLineToY(caretLine) - editor.scrollingModel.verticalScrollOffset

      MarkdownLivePreviewReconciler.getExisting(editor)!!.publishSpecsAndWait(specs)

      assertTrue(headingFolds().single().heightInPixels > editor.lineHeight, "The heading must be taller than a line")
      assertEquals(caretScreenY, editor.visualLineToY(caretLine) - editor.scrollingModel.verticalScrollOffset)
    }
  }

  @Test
  fun testHeadingSelectionAndMultipleCaretsKeepTheSourceVisible(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "before\n# title\n\nafter"
      configure("$content<caret>")
      val editor = myFixture.editor
      val afterY = editor.offsetToXY(content.indexOf("after")).y
      select(0, content.indexOf("title") + 2)
      assertThat(headingFolds()).isEmpty()
      assertEquals(afterY, editor.offsetToXY(content.indexOf("after")).y)
      editor.selectionModel.removeSelection()
      moveCaretTo(content.length)
      val caret = editor.caretModel.addCaret(editor.offsetToVisualPosition(content.indexOf('#')))!!
      assertThat(headingFolds()).isEmpty()
      assertEquals(afterY, editor.offsetToXY(content.indexOf("after")).y)
      editor.caretModel.removeCaret(caret)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      assertEquals(1, headingFolds().size)
      assertEquals(afterY, editor.offsetToXY(content.indexOf("after")).y)
    }
  }

  @Test
  fun testHeadingReplacesAnExpandedFoldThatStartsOnItsLineAndKeepsACollapsedOne(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "# one\n\nbody\n\n# two\n\nmore"
      configure("<caret>$content")
      val editor = myFixture.editor
      lateinit var expanded: FoldRegion
      editor.foldingModel.runBatchFoldingOperation { expanded = editor.foldingModel.addFoldRegion(0, content.length, "...")!! }
      moveCaretTo(content.length)
      assertEquals(2, headingFolds().size)
      assertFalse(expanded.isValid)

      moveCaretTo(0)
      lateinit var collapsed: FoldRegion
      editor.foldingModel.runBatchFoldingOperation {
        collapsed = editor.foldingModel.addFoldRegion(0, content.indexOf("body\n") + 5, "...")!!
        collapsed.isExpanded = false
      }
      moveCaretTo(content.length)
      assertEquals(listOf(content.indexOf("# two")), headingFolds().map { it.startOffset })
      assertTrue(collapsed.isValid)
      assertFalse(collapsed.isExpanded)
    }
  }

  @Test
  fun testHeadingsReplaceTheSectionFoldsOfTheCodeFoldingBuilder(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "# one\n\nbody\n\n## two\n\nmore\n\n# three\n\ntail"
      configure("<caret>$content")
      val editor = myFixture.editor
      EditorTestUtil.buildInitialFoldingsInBackground(editor, null)
      val section = editor.foldingModel.getFoldRegion(0, content.indexOf("\n\n# three"))!!
      assertFalse(section is CustomFoldRegion)

      moveCaretTo(content.length)
      assertEquals(3, headingFolds().size)
      assertFalse(section.isValid)

      EditorTestUtil.buildInitialFoldingsInBackground(editor, null)
      assertEquals(3, headingFolds().size)
      assertEquals(content, editor.document.text)
    }
  }

  @Test
  fun testHeadingBulkUpdateReplacesItsResources(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("# title\n\ntail<caret>")
      moveCaretTo(0)
      val spacer = myFixture.editor.inlayModel.getBlockElementsInRange(0, 7).single()
      writeCommandAction(myFixture.project).run<RuntimeException> {
        DocumentUtil.executeInBulk(myFixture.editor.document, true) {
          myFixture.editor.document.setText("plain\n\ntail")
        }
      }
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      assertThat(headingFolds()).isEmpty()
      assertFalse(spacer.isValid)
    }
  }

  @Test
  fun testHeadingClickAndArrowNavigationRevealTheSource(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "before\n# title\n\nafter"
      configure("$content<caret>")
      val editor = myFixture.editor as EditorImpl
      EditorTestUtil.setEditorVisibleSize(editor, 80, 12)
      val fold = headingFolds().single()
      val location = fold.location!!
      EditorMouseFixture(editor).clickAtXY(location.x + 5, location.y + fold.heightInPixels / 2)
      assertThat(headingFolds()).isEmpty()
      assertTrue(editor.caretModel.offset in content.indexOf('#')..content.indexOf("\n\n"))
      moveCaretTo(0)
      moveCaretDown()
      assertThat(headingFolds()).isEmpty()
    }
  }

  @Test
  fun testHeadingTypingDeletionUndoAndClipboardUseSource(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "before\n# title\n\nafter"
      configure("$content<caret>")
      moveCaretTo(content.indexOf("title"))
      myFixture.type("new ")
      myFixture.checkResult("before\n# new <caret>title\n\nafter")
      myFixture.performEditorAction(IdeActions.ACTION_UNDO)
      myFixture.checkResult("before\n# <caret>title\n\nafter")
      select(content.indexOf('#'), content.indexOf("\n\n"))
      myFixture.performEditorAction(IdeActions.ACTION_COPY)
      myFixture.editor.selectionModel.removeSelection()
      moveCaretTo(content.length)
      myFixture.performEditorAction(IdeActions.ACTION_PASTE)
      assertEquals("$content# title", myFixture.editor.document.text)
      myFixture.performEditorAction(IdeActions.ACTION_UNDO)
      moveCaretTo(content.indexOf('#') + 1)
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      assertThat(headingFolds()).isEmpty()
      assertThat(myFixture.editor.inlayModel.getBlockElementsInRange(0, content.length - 1)).isEmpty()
    }
  }

  @Test
  fun testHeadingRendererSurvivesUnrelatedEditsAndRejectsStaleSpecs(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "before\n# title\n\nafter"
      configure("$content<caret>")
      val renderer = headingFolds().single().renderer
      val stale = computeLivePreviewSpecs(myFixture.file, myFixture.editor, configuration)
      moveCaretTo(0)
      myFixture.type("more ")
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      assertSame(renderer, headingFolds().single().renderer)
      MarkdownLivePreviewReconciler.getExisting(myFixture.editor)!!.publishSpecsAndWait(stale)
      assertSame(renderer, headingFolds().single().renderer)
      assertTrue(MarkdownLivePreviewReconciler.getExisting(myFixture.editor)!!.hasCurrentSpecs())
    }
  }

  @Test
  fun testHeadingSpacerIsDisposedWhenPreviewIsDisabled(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("# title\n\ntail<caret>")
      moveCaretTo(0)
      val inlay = myFixture.editor.inlayModel.getBlockElementsInRange(0, 7).single()
      myFixture.editor.setLivePreviewEnabledness(false, configuration)
      MarkdownLivePreviewReconciler.getExisting(myFixture.editor)!!.reconcileNow()
      assertFalse(inlay.isValid)
      assertThat(headingFolds()).isEmpty()
    }
  }

  @Test
  fun testManyHeadingSpacersUseBatchInlayUpdates(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val headings = (1..101).joinToString("\n") { "# heading $it" }
      val content = "$headings\n\nbody"
      val specs = configureWithLateSpecs(content, content.length)
      val editor = myFixture.editor
      editor.caretModel.moveToOffset(0)
      for (line in 1..100) {
        editor.caretModel.addCaret(editor.offsetToVisualPosition(editor.document.getLineStartOffset(line)))
      }
      var batchModeStarted = false
      editor.inlayModel.addListener(object : InlayModel.Listener {
        override fun onBatchModeStart(editor: Editor) {
          batchModeStarted = true
        }
      }, myFixture.testRootDisposable)

      MarkdownLivePreviewReconciler.getExisting(editor)!!.publishSpecsAndWait(specs)

      assertTrue(batchModeStarted, "Heading spacers must use batch inlay updates")
      assertThat(headingFolds()).isEmpty()
      assertEquals(101, editor.inlayModel.getBlockElementsInRange(0, content.length).size)
      assertEquals(content, editor.document.text)
    }
  }

  @Test
  fun testHeadingFoldRefreshesAfterEditorFontChange(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "before\n# title\n\nafter"
      configure("$content<caret>")
      val editor = myFixture.editor as EditorImpl
      val oldHeight = headingFolds().single().heightInPixels

      editor.fontSize = editor.colorsScheme.editorFontSize + 4
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

      assertTrue(headingFolds().single().heightInPixels > oldHeight)
      assertHeadingHeightIsStable(content)
    }
  }

  @Test
  fun testHeadingFoldRefreshesAfterEditorFontNameChange(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("before\n# title\n\nafter<caret>")
      val scheme = myFixture.editor.colorsScheme
      val fontName = EditorColorsManager.getInstance().globalScheme.editorFontName
      assertFalse(scheme.editorFontName == fontName, "The test needs another font name")
      val renderer = headingFolds().single().renderer

      scheme.editorFontName = fontName
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

      assertNotSame(renderer, headingFolds().single().renderer)
    }
  }

  @Test
  fun testHeadingPaintsWithLightAndDarkEditorSchemes(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "before\n# **bold** *italic* `code` ~~gone~~ [link](https://example.org)\n\nafter"
      configure("$content<caret>")
      val manager = EditorColorsManager.getInstance() as EditorColorsManagerImpl
      val originalScheme = manager.globalScheme
      Disposer.register(myFixture.testRootDisposable) { manager.setGlobalScheme(originalScheme, processChangeSynchronously = true) }
      for (name in listOf("Darcula", "Default")) {
        val scheme = manager.getScheme(name)!!
        manager.setGlobalScheme(scheme, processChangeSynchronously = true)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        val bitmap = paintHeading(headingFolds().single(), scheme.defaultBackground)
        val pixels = bitmap.getRGB(0, 0, bitmap.width, bitmap.height, null, 0, bitmap.width)
        assertTrue(pixels.count { it != scheme.defaultBackground.rgb } > 100, "The heading must paint text in $name")
        assertHeadingHeightIsStable(content)
      }
    }
  }

  @Test
  fun testHeadingPaintsTheHighlightBackground(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("# ==marked==\n\ntail<caret>")
      val editor = myFixture.editor
      val highlight = editor.colorsScheme.getAttributes(MarkdownHighlighterColors.HIGHLIGHT).backgroundColor
      val bitmap = paintHeading(headingFolds().single(), editor.colorsScheme.defaultBackground)
      val pixels = bitmap.getRGB(0, 0, bitmap.width, bitmap.height, null, 0, bitmap.width)
      assertTrue(pixels.count { it == highlight.rgb } > 100, "The heading must paint the highlight background")
    }
  }

  @Test
  fun testHighlightBackgroundSurvivesRevealingAHeading(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "# ==marked==\n\ntail"
      configure("$content<caret>")
      val expected = myFixture.editor.colorsScheme.getAttributes(MarkdownHighlighterColors.HIGHLIGHT).backgroundColor
      val offset = content.indexOf("marked")
      moveCaretTo(content.indexOf("tail"))
      assertEquals(1, headingFolds().size)
      moveCaretTo(offset + 2)
      assertThat(headingFolds()).isEmpty()
      assertEquals(expected, backgroundAt(offset))
    }
  }

  @Test
  fun testStickyLineOfRevealedHeadingPaintsTheHeading(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "# title\n\ntail"
      configure("$content<caret>")
      val renderer = headingFolds().single().renderer
      moveCaretTo(0)
      assertThat(headingFolds()).isEmpty()

      val editor = myFixture.editor as EditorImpl
      EditorTestUtil.setEditorVisibleSize(editor, 80, 12)
      val highlighter = editor.markupModel.allHighlighters.single { it.customRenderer != null }
      assertSame(renderer, highlighter.customRenderer)
      assertTrue(
        paintStickyLine(editor, highlighter, sticky = false).all { it == Color.RED.rgb },
        "Only a sticky line paints the heading"
      )

      val pixels = paintStickyLine(editor, highlighter, sticky = true)
      assertTrue(
        pixels.count { it != Color.RED.rgb && it != editor.backgroundColor.rgb } > 50,
        "The sticky line must paint the heading text"
      )
      moveCaretTo(content.length)
      assertFalse(highlighter.isValid)
    }
  }

  @Test
  fun testStickyLineOfWrappedHeadingPaintsOneLine(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("# " + "word ".repeat(30).trim() + "\n\ntail<caret>")
      val editor = myFixture.editor as EditorImpl
      EditorTestUtil.configureSoftWraps(editor, 300, 1000, 10)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      val fold = headingFolds().single()
      assertTrue(fold.heightInPixels > 2 * editor.lineHeight, "The heading must wrap")
      val background = editor.colorsScheme.defaultBackground
      val wrappedRows = paintHeading(fold, background).paintedRows(background)

      editor.isStickyLinePainting = true
      try {
        val rows = paintHeading(fold, background).paintedRows(background)
        val columns = paintedHeadingColumns()
        assertTrue(rows.last < editor.lineHeight, "The sticky line must paint within one line: $rows")
        assertTrue(columns.last > fold.widthInPixels * 3 / 4, "The sticky line must paint up to the fold end: $columns")
      }
      finally {
        editor.isStickyLinePainting = false
      }
      assertEquals(wrappedRows, paintHeading(fold, background).paintedRows(background), "The editor must paint the wrapped heading again")
    }
  }

  @Test
  fun testHeadingAtDocumentEnd(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("<caret>before\n## last")
      assertEquals(1, headingFolds().size)
      moveCaretTo(myFixture.editor.document.textLength)
      assertThat(headingFolds()).isEmpty()
      moveCaretTo(0)
      assertEquals(1, headingFolds().size)
    }
  }

  @Test
  fun testSetextHeadingFoldsItsContentLineAndTheUnderlineIsARule(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "before\n\nTitle\n===\n\nafter"
      configure("$content<caret>")
      val contentStart = content.indexOf("Title")
      val underline = content.indexOf("===")

      val fold = headingFolds().single()
      assertEquals(contentStart, fold.startOffset)
      assertEquals(underline - 1, fold.endOffset)
      assertEquals(listOf(underline), thematicBreakHighlighters().map { it.startOffset })
      assertEquals(content, myFixture.editor.document.text)

      moveCaretTo(contentStart)
      assertThat(headingFolds()).isEmpty()
      assertEquals(1, thematicBreakHighlighters().size)

      moveCaretTo(underline)
      assertEquals(1, headingFolds().size)
      assertThat(thematicBreakHighlighters()).isEmpty()

      moveCaretTo(content.length)
      assertEquals(1, headingFolds().size)
      assertEquals(1, thematicBreakHighlighters().size)
    }
  }

  @Test
  fun testSetextHeadingIsTheWholeDocument(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("Title\n---<caret>")
      assertEquals(1, headingFolds().size)

      moveCaretTo(0)
      assertThat(headingFolds()).isEmpty()
      assertEquals(1, thematicBreakHighlighters().size)
    }
  }

  @Test
  fun testSetextHeadingsReplaceTheSectionFoldsOfTheCodeFoldingBuilder(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "Title\n===\n\nbody\nmore\n\nNext\n===\n\ntail"
      configure("<caret>$content")
      val editor = myFixture.editor
      EditorTestUtil.buildInitialFoldingsInBackground(editor, null)
      val section = editor.foldingModel.getFoldRegion(0, content.indexOf("\n\nNext"))!!
      assertFalse(section is CustomFoldRegion)

      moveCaretTo(content.length)
      assertEquals(listOf(0, content.indexOf("Next")), headingFolds().map { it.startOffset })
      assertFalse(section.isValid)
      assertEquals(content, editor.document.text)
    }
  }

  @Test
  fun testClickOnARenderedSetextHeadingPlacesTheCaretAtTheClickedSource(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "before\n\nHello **big** world\n===\n\nafter"
      configure("$content<caret>")
      EditorTestUtil.setEditorVisibleSize(myFixture.editor, 80, 12)
      val start = content.indexOf("Hello")
      val end = content.indexOf("\n===")
      val text = paintedHeadingColumns()

      assertEquals(start, clickHeading { 1 })
      val last = clickHeading { text.last }
      assertTrue(last in end - 1..end, "$last")
    }
  }

  @Test
  fun testHtmlHeadingElementsKeepInlineSourceMapping(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "before\n## Hello **big** world\n\nafter"
      configure("$content<caret>")
      EditorTestUtil.setEditorVisibleSize(myFixture.editor, 80, 12)
      val start = content.indexOf("Hello")

      assertEquals(start, clickHeading { 1 })
    }
  }

  @Test
  fun testClickOnAHighlightedHeadingPlacesTheCaretInTheMarkedText(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "before\n# ==marked==\n\nafter"
      configure("$content<caret>")
      EditorTestUtil.setEditorVisibleSize(myFixture.editor, 80, 12)
      val start = content.indexOf("marked")
      val text = paintedHeadingColumns()

      val middle = clickHeading { (text.first + text.last) / 2 }
      assertTrue(middle in start..start + "marked".length, "$middle")
    }
  }

  @Test
  fun testClickOnARenderedHeadingPlacesTheCaretAtTheClickedSource(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "before\n# Hello **big** world\n\nafter"
      configure("$content<caret>")
      EditorTestUtil.setEditorVisibleSize(myFixture.editor, 80, 12)
      val start = content.indexOf("Hello")
      val end = content.indexOf("\n\n")
      val text = paintedHeadingColumns()

      assertEquals(start, clickHeading { 1 })
      val middle = clickHeading { (text.first + text.last) / 2 }
      assertTrue(middle in start + 1 until end, "$middle")
      val last = clickHeading { text.last }
      assertTrue(last in end - 1..end, "$last")
    }
  }

  @Test
  fun testClickOnRenderedCodePlacesTheCaretInsideTheCode(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "before\n# `code`\n\nafter"
      configure("$content<caret>")
      EditorTestUtil.setEditorVisibleSize(myFixture.editor, 80, 12)
      val start = content.indexOf("code")
      val code = paintedHeadingColumns()

      assertEquals(start, clickHeading { 1 })
      val middle = clickHeading { (code.first + code.last) / 2 }
      assertTrue(middle in start + 1 until start + "code".length, "$middle")
      // A click right of the code text puts the caret after the code text, before the closing backtick.
      assertEquals(start + "code".length, clickHeading { it.widthInPixels - 1 })
    }
  }

  @Test
  fun testBodySizeHeadingTextSitsInItsLineAsRegularText(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "###### Title\nTitle\n\ntail"
      configure("$content<caret>")
      val editor = myFixture.editor as EditorImpl
      val fold = headingFolds().single()
      val background = editor.colorsScheme.defaultBackground
      val headingRows = paintHeading(fold, background).paintedRows(background)
      val lineY = editor.offsetToXY(content.indexOf("\nTitle") + 1).y
      val editorImage = BufferedImage(fold.widthInPixels, lineY + editor.lineHeight, BufferedImage.TYPE_INT_RGB)
      val graphics = editorImage.createGraphics()
      try {
        editor.contentComponent.setSize(editorImage.width, editorImage.height)
        editor.contentComponent.paint(graphics)
      }
      finally {
        graphics.dispose()
      }
      val lineRows = editorImage.getSubimage(0, lineY, editorImage.width, editor.lineHeight).paintedRows(background)

      assertEquals(editor.lineHeight, fold.heightInPixels, "An h6 has the body font size, so it takes one line")
      assertTrue(abs(headingRows.first - lineRows.first) <= 1 && abs(headingRows.last - lineRows.last) <= 1,
                 "The heading paints rows $headingRows, the line below paints rows $lineRows")
    }
  }

  @Test
  fun testQuotedHeadingTextStartsWhereQuotedTextStarts(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("# Title\n\ntail<caret>")
      val bearing = paintedHeadingColumns().first
      val content = "> # Title\n> text\n\ntail"
      configure("$content<caret>")
      val editor = myFixture.editor
      val fold = headingFolds().single()
      val textX = editor.offsetToXY(content.indexOf("text")).x - editor.offsetToXY(content.indexOf("> text")).x
      val headingX = paintedHeadingColumns().first - bearing

      assertEquals(0 to content.indexOf('\n'), fold.startOffset to fold.endOffset)
      assertTrue(abs(headingX - textX) <= 1, "The heading text starts at $headingX, the quoted text at $textX")
    }
  }

  @Test
  fun testNestedQuoteRulesKeepTheirXOnAHeadingLine(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "> > # Title\n> > text\n\ntail"
      configure("$content<caret>")
      val textLine = content.indexOf("> > text")
      val rules = paintedRules()

      assertEquals(1, headingFolds().size)
      for (marker in listOf(0, 2)) {
        val headingX = rules.getValue(marker).x
        val textX = rules.getValue(textLine + marker).x
        assertTrue(abs(headingX - textX) <= 1, "The rule of the marker at $marker is at $headingX, the rule below it at $textX")
      }
    }
  }

  @Test
  fun testClickOnAQuotedHeadingPlacesTheCaretAtTheClickedSource(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "before\n> > # Title\n\nafter"
      configure("$content<caret>")
      EditorTestUtil.setEditorVisibleSize(myFixture.editor, 80, 12)
      val text = paintedHeadingColumns()

      assertEquals(content.indexOf("Title"), clickHeading { text.first + 1 })
      val last = clickHeading { text.last }
      assertTrue(last in content.indexOf("\n\n") - 1..content.indexOf("\n\n"), "$last")
    }
  }

  @Test
  fun testQuoteRuleCoversTheWholeHeadingHeight(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("> # Title\n\ntail<caret>")
      val fold = headingFolds().single()

      assertEquals(fold.heightInPixels, paintedRules().values.single().height + 1)
    }
  }

  @Test
  fun testImageInAHeadingStaysBelowTheHeadingWhenTheHeadingShowsItsSource(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(200, 200)
      val content = "before\n\n# Parent ![image1](image.png)\n\ntail"
      configureProjectFile(content)
      val imageY = waitForImageInlay().bounds!!.y
      val fold = headingFolds().single()
      assertEquals(fold.location!!.y + fold.heightInPixels, imageY)

      moveCaretTo(content.indexOf("Parent"))

      assertThat(headingFolds()).isEmpty()
      assertEquals(imageY, imageInlays().single().bounds!!.y)
    }
  }

  @Test
  fun testWrappedHeadingRelayoutsItsRegionWhenTheEditorNarrows(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "before\n# " + "word ".repeat(30).trim() + "\n\nafter"
      configure("$content<caret>")
      val editor = myFixture.editor
      EditorTestUtil.configureSoftWraps(editor, 2000, 1000, 10)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      val region = headingFolds().single()
      val wideHeight = region.heightInPixels

      EditorTestUtil.configureSoftWraps(editor, 300, 1000, 10)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

      assertSame(region, headingFolds().single())
      assertTrue(region.heightInPixels > wideHeight, "${region.heightInPixels} > $wideHeight")
      assertHeadingHeightIsStable(content)
    }
  }

  @Test
  fun testInlineLinkShowsOnlyItsTitle(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("Read [the docs](https://example.org) today<caret>")
      assertEquals("Read the docs today", visibleText())
    }
  }

  @Test
  fun testAutolinksShowOnlyTheirTarget(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("See <https://example.org> or mail <team@example.org> here<caret>\n\ntail")
      assertEquals("See https://example.org or mail team@example.org here\n\ntail", visibleText())
    }
  }

  @Test
  fun testUnorderedListMarkersUseDepthPlaceholders(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = """
        |- one
        |  * two
        |    + three
        |      - four
        |
        |tail
      """.trimMargin()
      configure("$content<caret>")
      assertEquals("• one\n  ◦ two\n    ▪ three\n      • four\n\ntail", visibleText())
      assertEquals(listOf("-" to "•", "*" to "◦", "+" to "▪", "-" to "•"), concealedWithPlaceholders())
    }
  }

  @Test
  fun testCaretOnTheElementRevealsItsMarkers(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "Some **bold** text"
      configure(content)
      moveCaretTo(content.length)
      assertEquals(listOf("**", "**"), concealed())

      // Immediately after the closing `**`, which is the element's end offset.
      moveCaretTo(content.indexOf(" text"))
      assertThat(concealed()).isEmpty()
      assertEquals(content, visibleText())
    }
  }

  @Test
  fun testHighlightMarkersAreHiddenAndRevealedByTheCaret(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "Some ==marked== text"
      configure(content)
      moveCaretTo(content.length)
      assertEquals(listOf("==", "=="), concealed())
      assertEquals("Some marked text", visibleText())

      moveCaretTo(content.indexOf("marked") + 2)
      assertThat(concealed()).isEmpty()
      assertEquals(content, visibleText())
    }
  }

  /**
   * The load-bearing test: touching counts at both ends, so no concealing region may ever sit under or next
   * to a caret. Everything else in the design leans on this.
   */
  @Test
  fun testEveryCaretOffsetAroundAnElement(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "**bold** x"
      configure(content)
      val elementEnd = content.indexOf(" x")
      for (offset in 0..content.length) {
        moveCaretTo(offset)
        if (offset <= elementEnd) {
          assertThat(concealed()).describedAs("Caret at $offset touches the element and must reveal it").isEmpty()
        }
        else {
          assertEquals(listOf("**", "**"), concealed(), "Caret at $offset is outside the element, which must stay hidden")
        }
      }
    }
  }

  @Test
  fun testCaretOnEitherListMarkerBoundaryRevealsMarker(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- item\n\ntail"
      configure("$content<caret>")
      assertEquals(listOf("-" to "•"), concealedWithPlaceholders())

      moveCaretTo(0)
      assertThat(concealed()).isEmpty()
      moveCaretTo(content.length)
      assertEquals(listOf("-"), concealed())
      moveCaretTo(1)
      assertThat(concealed()).isEmpty()
      moveCaretTo(2)
      assertEquals(listOf("-"), concealed(), "The caret at the start of the item text keeps the marker")
    }
  }

  @Test
  fun testOnlyOffsetsOnTheListMarkerRevealIt(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertNothingLogged {
        for ([prefix, marker] in listOf("  - " to "-", "  - [ ] " to "- [ ]", "  1. [x] " to "[x]")) {
          val line = "${prefix}item text  "
          val content = "$line\n\ntail"
          configure("$content<caret>")
          val markerStart = line.indexOf(marker)
          for (offset in 0..line.length) {
            moveCaretTo(offset)
            if (offset in markerStart..markerStart + marker.length) {
              assertThat(concealed()).describedAs("The caret at $offset must reveal $prefix").isEmpty()
              assertThat(checkboxInlays()).isEmpty()
            }
            else {
              assertEquals(listOf(marker), concealed(), "The caret at $offset must keep $prefix")
              assertEquals(marker != "-", checkboxInlays().isNotEmpty())
            }
            moveCaretTo(content.length)
            assertEquals(1, concealed().size)
          }
        }
      }
    }
  }

  @Test
  fun testContinuationAndNestedListLinesDoNotRevealTheirParent(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      for (marker in listOf("-", "- [ ]")) {
        val content = "$marker parent\n  continuation\n  $marker child\n\ntail"
        configure("$content<caret>")
        moveCaretTo(content.indexOf("continuation"))
        assertEquals(listOf(marker, marker), concealed())
        moveCaretTo(content.indexOf("  $marker child") + 2)
        assertEquals(listOf(marker), concealed())
        assertEquals(0, concealedLivePreviewRegions(myFixture.editor).single().startOffset)
        moveCaretTo(content.indexOf("child") + 2)
        assertEquals(listOf(marker, marker), concealed())
        moveCaretTo(content.indexOf("parent") + 2)
        assertEquals(listOf(marker, marker), concealed())
      }
    }
  }

  @Test
  fun testSoftWrappedListTextKeepsItsMarkerConcealed(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      for (marker in listOf("-", "- [ ]")) {
        val content = "$marker a long item that wraps over several visual lines\n\ntail"
        configure("$content<caret>")
        EditorTestUtil.configureSoftWraps(myFixture.editor, 15)
        moveCaretTo(content.indexOf("visual"))
        assertTrue(myFixture.editor.caretModel.visualPosition.line > 0)
        assertEquals(listOf(marker), concealed())
        assertEquals(marker != "-", checkboxInlays().isNotEmpty())
      }
    }
  }

  @Test
  fun testListLinesGetIndentInlaysByDepthAndParagraphsDoNot(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- one\n  * two\n    + three\n- four\n\ntail"
      configure("$content<caret>")
      val document = myFixture.editor.document
      val inlays = listIndentInlays()
      assertEquals((0..3).map { document.getLineStartOffset(it) }, inlays.map { it.offset })
      assertTrue(inlays[0].widthInPixels > 0)
      assertTrue(inlays[1].widthInPixels > inlays[0].widthInPixels)
      assertEquals(inlays[1].widthInPixels - inlays[0].widthInPixels, inlays[2].widthInPixels - inlays[1].widthInPixels)
      assertEquals(inlays[0].widthInPixels, inlays[3].widthInPixels)
      assertEquals(listOf("-", "*", "+", "-"), concealed())
    }
  }

  @Test
  fun testCaretAtANestedLineStartKeepsItsIndentInlay(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- one\n  * two\n\ntail"
      configure("$content<caret>")
      val lineStart = content.indexOf("  * two")
      val inlay = listIndentInlays().single { it.offset == lineStart }

      moveCaretTo(lineStart)
      assertEquals(listOf("-", "*"), concealed())
      assertSame(inlay, listIndentInlays().single { it.offset == lineStart })

      moveCaretTo(lineStart + 2)
      assertEquals(listOf("-"), concealed())
      assertSame(inlay, listIndentInlays().single { it.offset == lineStart })
      assertTrue(inlay.isValid)
    }
  }

  @Test
  fun testSoftWrappedListItemContinuationStartsUnderTheItemText(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- bullet item that wraps over several visual lines\n\n" +
                    "2. ordered item that wraps over several visual lines\n\n" +
                    "- [ ] task item that wraps over several visual lines\n\n" +
                    "- parent\n  - nested item that wraps over several visual lines\n\n" +
                    "> - quoted item that wraps over several visual lines\n\n" +
                    "plain item that wraps over several visual lines\n\ntail"
      configure("$content<caret>")
      val editor = myFixture.editor
      val softWrapModel = editor.softWrapModel as SoftWrapModelImpl
      softWrapModel.applianceManager.setWidthProvider { 200 }
      EditorTestUtil.setEditorVisibleSizeInPixels(editor, 200, 1000)
      softWrapModel.applianceManager.recalculateIfNecessary("test")
      MarkdownLivePreviewReconciler.getExisting(editor)!!.reconcileNow()
      assertEquals(0, softWrapModel.getMinDrawingWidthInPixels(SoftWrapDrawingType.AFTER_SOFT_WRAP))
      for (item in listOf("bullet", "ordered", "task", "nested", "quoted", "plain")) {
        val textStart = content.indexOf("$item item")
        val softWrap = softWrapModel.getSoftWrapsForRange(textStart + 1, content.indexOf('\n', textStart)).first()
        assertEquals(editor.offsetToXY(textStart).x, editor.offsetToXY(softWrap.start, true, false).x, item)
      }
    }
  }

  @Test
  fun testSoftWrappedBlockQuoteContinuationStartsUnderTheQuoteText(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "> quote text that wraps over several visual lines\n\n" +
                    "> > nested quote text that wraps over several visual lines\n\ntail"
      configure("$content<caret>")
      val editor = myFixture.editor
      val softWrapModel = editor.softWrapModel as SoftWrapModelImpl
      softWrapModel.applianceManager.setWidthProvider { 200 }
      EditorTestUtil.setEditorVisibleSizeInPixels(editor, 200, 1000)
      softWrapModel.applianceManager.recalculateIfNecessary("test")
      MarkdownLivePreviewReconciler.getExisting(editor)!!.reconcileNow()
      for (quote in listOf("quote", "nested quote")) {
        val textStart = content.indexOf("$quote text")
        val softWrap = softWrapModel.getSoftWrapsForRange(textStart + 1, content.indexOf('\n', textStart)).first()
        assertEquals(editor.offsetToXY(textStart).x, editor.offsetToXY(softWrap.start, true, false).x, quote)
      }
    }
  }

  @Test
  fun testSelectionAndMultipleCaretsRevealListMarkers(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- first\n- [ ] second\n- third\n\ntail"
      configure("$content<caret>")
      val second = content.indexOf("second")
      select(second, second + 2)
      assertEquals(listOf("-", "- [ ]", "-"), concealed(), "A selection in the item text keeps every marker")
      select(content.indexOf("- [ ]"), second + 2)
      assertEquals(listOf("-", "-"), concealed())
      val editor = myFixture.editor
      editor.caretModel.addCaret(editor.offsetToVisualPosition(0))
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      assertEquals(listOf("-"), concealed())
    }
  }

  @Test
  fun testCheckboxesUseCompactWidthRegardlessOfSourcePrefixAndFontSize(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- [ ] first\n-\t[x] second\n1. [X] third\n\ntail"
      configure("$content<caret>")
      val editor = myFixture.editor as EditorImpl
      assertEquals(listOf(false, true, true), checkboxInlays().map { it.renderer.checked })
      for (fontSize in listOf(editor.colorsScheme.editorFontSize, editor.colorsScheme.editorFontSize + 4)) {
        editor.fontSize = fontSize
        moveCaretTo(content.length)
        val widths = checkboxInlays().map { it.widthInPixels }
        assertEquals(1, widths.distinct().size, "Every checkbox must have the same width")
        assertTrue(widths.first() <= editor.lineHeight, "The checkbox must fit within one line height")
        for ([word, marker] in listOf("first" to "- [ ]", "second" to "-\t[x]", "third" to "[X]")) {
          moveCaretTo(content.length)
          val offset = content.indexOf(word)
          val position = editor.offsetToXY(offset)
          moveCaretTo(content.indexOf(marker))
          assertTrue(position.x < editor.offsetToXY(offset).x, "The compact checkbox must use less space than the source prefix")
        }
      }
    }
  }

  @Test
  fun testCheckboxClicksPreserveCaretsSelectionAndConcealment(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- [ ] task\n\ntail text"
      configure("$content<caret>")
      val editor = myFixture.editor as EditorImpl
      EditorTestUtil.setEditorVisibleSize(editor, 80, 12)
      select(content.indexOf("tail"), content.length)
      val carets = editor.caretModel.allCarets.map { Triple(it.offset, it.selectionStart, it.selectionEnd) }
      val scroll = editor.scrollingModel.verticalScrollOffset
      val checkbox = checkboxInlays().single()
      val fold = concealedLivePreviewRegions(editor).single()
      var disposals = 0
      editor.foldingModel.addListener(object : FoldingListener {
        override fun beforeFoldRegionDisposed(region: FoldRegion) {
          if (region === fold) disposals++
        }
      }, myFixture.testRootDisposable)

      clickCheckbox(checkbox)
      myFixture.checkResult(content.replace("[ ]", "[x]"))
      assertTrue(checkbox.renderer.checked, "The click must change the checkbox before highlighting")
      waitForDocument(content.replace("[ ]", "[x]"))
      assertTrue(checkboxInlays().single().renderer.checked)
      assertEquals(carets, editor.caretModel.allCarets.map { Triple(it.offset, it.selectionStart, it.selectionEnd) })
      assertEquals(scroll, editor.scrollingModel.verticalScrollOffset)
      assertSame(checkbox, checkboxInlays().single())
      assertEquals(0, disposals)

      clickCheckbox(checkbox)
      myFixture.checkResult(content)
      assertFalse(checkbox.renderer.checked, "The second click must also change the checkbox immediately")
      waitForDocument(content)
      assertFalse(checkboxInlays().single().renderer.checked)
      assertEquals(carets, editor.caretModel.allCarets.map { Triple(it.offset, it.selectionStart, it.selectionEnd) })
      assertEquals(0, disposals)
    }
  }

  @Test
  fun testCheckboxToggleIsOneUndoableEdit(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- [X] task\n\ntail"
      configure("$content<caret>")
      clickCheckbox(checkboxInlays().single())
      waitForDocument(content.replace("[X]", "[ ]"))
      myFixture.performEditorAction(IdeActions.ACTION_UNDO)
      assertTrue(checkboxInlays().single().renderer.checked, "Undo must update the checkbox before highlighting")
      waitForDocument(content)
      myFixture.performEditorAction(IdeActions.ACTION_REDO)
      assertFalse(checkboxInlays().single().renderer.checked, "Redo must update the checkbox before highlighting")
      waitForDocument(content.replace("[X]", "[ ]"))
    }
  }

  @Test
  fun testRapidCheckboxClicksDoNotWaitForHighlighting(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- [ ] first\n- [ ] second\n\ntail"
      configure("$content<caret>")
      val [first, second] = checkboxInlays()
      clickCheckbox(first)
      assertTrue(first.renderer.checked)
      clickCheckbox(first)
      assertFalse(first.renderer.checked)
      clickCheckbox(second)
      assertTrue(second.renderer.checked)
      myFixture.checkResult(content.replace("[ ] second", "[x] second"))
      myFixture.performEditorAction(IdeActions.ACTION_UNDO)
      assertFalse(second.renderer.checked)
      myFixture.checkResult(content)
    }
  }

  @Test
  fun testCheckboxSurvivesAnEditBeforeItsRange(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "start\n\n- [ ] task\n\ntail"
      configure("$content<caret>")
      val inlay = checkboxInlays().single()
      val region = concealedLivePreviewRegions(myFixture.editor).single()
      val offset = inlay.offset

      writeCommandAction(myFixture.project).run<Throwable> {
        myFixture.editor.document.insertString(0, "prefix\n")
      }
      myFixture.doHighlighting()
      waitForCurrentSpecs()

      assertSame(region, concealedLivePreviewRegions(myFixture.editor).single())
      assertSame(inlay, checkboxInlays().single())
      assertEquals(offset + "prefix\n".length, inlay.offset)
      clickCheckbox(inlay)
      waitForDocument("prefix\n${content.replace("[ ]", "[x]")}")
      assertTrue(inlay.renderer.checked)
    }
  }

  @Test
  fun testCheckboxReleaseOutsideCancelsTheClick(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- [ ] task\n\ntail"
      configure("$content<caret>")
      val editor = myFixture.editor as EditorImpl
      EditorTestUtil.setEditorVisibleSize(editor, 80, 12)
      val bounds = checkboxInlays().single().bounds!!
      val caret = editor.caretModel.offset
      EditorMouseFixture(editor).pressAtXY(bounds.x + 2, bounds.y + bounds.height / 2).dragTo(2, 0).release()
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      myFixture.checkResult(content)
      assertEquals(caret, editor.caretModel.offset)
    }
  }

  @Test
  fun testReadOnlyCheckboxClickDoesNotEditOrReveal(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- [ ] task\n\ntail"
      configure("$content<caret>")
      val editor = myFixture.editor
      editor.document.setReadOnly(true)
      try {
        clickCheckbox(checkboxInlays().single())
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        myFixture.checkResult(content)
        assertEquals(content.length, editor.caretModel.offset)
        assertEquals(listOf("- [ ]"), concealed())
      }
      finally {
        editor.document.setReadOnly(false)
      }
    }
  }

  @Test
  fun testCheckboxClickUsesItsMovedRangeBeforeHighlighting(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- [ ] first\n- [ ] second\n\ntail"
      configure("$content<caret>")
      val checkbox = checkboxInlays().first()
      writeCommandAction(myFixture.project).run<Throwable> {
        myFixture.editor.document.insertString(0, "- [ ] new\n")
      }
      clickCheckbox(checkbox)
      assertTrue(checkbox.renderer.checked)
      myFixture.checkResult("- [ ] new\n${content.replace("[ ] first", "[x] first")}")
    }
  }

  @Test
  fun testCheckboxSourceRemainsEditableAndPreviewCanBeDisabled(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- [ ] task\n\ntail"
      configure("$content<caret>")
      moveCaretTo(content.indexOf(']'))
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      myFixture.checkResult(content.replace("[ ]", "[]"))
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      assertThat(checkboxInlays()).isEmpty()

      configure("$content<caret>")
      myFixture.editor.setLivePreviewEnabledness(false, configuration)
      myFixture.doHighlighting()
      waitForConcealed(emptyList())
      assertThat(checkboxInlays()).isEmpty()
    }
  }

  @Test
  fun testIndentingMarkerReplacesItsStalePlaceholder(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- parent\n- child\n\ntail"
      configure("$content<caret>")
      assertEquals(listOf("-" to "•", "-" to "•"), concealedWithPlaceholders())
      val staleChildBullet = concealedLivePreviewRegions(myFixture.editor)[1].bulletInlay(myFixture.editor)
      assertNotNull(staleChildBullet)

      val childText = content.indexOf("child")
      select(childText + 1, childText + 2)
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_INDENT_SELECTION)
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      myFixture.editor.selectionModel.removeSelection()
      moveCaretTo(myFixture.editor.document.textLength)

      assertEquals(listOf("-" to "•", "-" to "◦"), concealedWithPlaceholders())
      assertFalse(staleChildBullet!!.isValid, "The stale bullet must be disposed")
    }
  }

  @Test
  fun testNestedElementRevealsItsAncestor(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "**bold *and italic* here**"
      configure(content)
      moveCaretTo(content.indexOf("and"))
      assertThat(concealed()).describedAs("A caret in the inner element must reveal the outer one too").isEmpty()
    }
  }

  @Test
  fun testSelectionOverAnElementRevealsIt(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "a **bold** b **more** c"
      configure(content)
      select(0, content.indexOf(" b"))
      assertEquals(listOf("**", "**"), concealed(), "Only the selected element is revealed")
    }
  }

  @Test
  fun testSelectionEndpointsAreNotDropped(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "a **bold** b"
      configure(content)
      val start = content.indexOf("**")
      val end = start + "**bold**".length
      select(start, end)
      assertTrue(myFixture.editor.selectionModel.hasSelection(), "The selection must survive reconciliation")
      assertEquals(start, myFixture.editor.selectionModel.selectionStart)
      assertEquals(end, myFixture.editor.selectionModel.selectionEnd)
    }
  }

  @Test
  fun testMultipleCaretsRevealOnlyWhatTheyTouch(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "**one** and *two* and ~~three~~ tail"
      configure(content)
      val editor = myFixture.editor
      editor.caretModel.caretsAndSelections = listOf(
        com.intellij.openapi.editor.CaretState(editor.offsetToLogicalPosition(content.indexOf("two")), null, null),
        com.intellij.openapi.editor.CaretState(editor.offsetToLogicalPosition(content.length), null, null),
      )
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      assertEquals(listOf("**", "**", "~~", "~~"), concealed(), "Only the element under a caret is revealed")
    }
  }

  @Test
  fun testConcealingDoesNotChangeTheDocument(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "Some **bold** and [docs](https://example.org)<caret>"
      configure(content)
      assertFalse(concealed().isEmpty())
      myFixture.checkResult(content.removeSuffix("<caret>"))
    }
  }

  @Test
  fun testTableCellInlineMarkersAreConcealedAndRevealOnCaret(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = """
        || Name | Value |
        || --- | --- |
        || box | *box*, **box**, ~~box~~, `box`, [box](url) |
      """.trimMargin()
      configure("$content<caret>")
      assertEquals(
        """
          || Name | Value |
          || --- | --- |
          || box | box, box, box, box, box |
        """.trimMargin(),
        visibleText(),
      )

      val boldStart = content.indexOf("**box**")
      moveCaretTo(boldStart + 2)
      assertEquals(listOf("*", "*", "~~", "~~", "`", "`", "[", "](url)"), concealed())
      myFixture.checkResult(content)

      writeCommandAction(myFixture.project).run<Throwable> {
        myFixture.editor.document.insertString(boldStart + 3, "new")
      }
      val editedContent = content.replace("**box**", "**bnewox**")
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      moveCaretTo(editedContent.length)
      assertEquals(
        """
          || Name | Value |
          || --- | --- |
          || box | box, bnewox, box, box, box |
        """.trimMargin(),
        visibleText(),
      )
      myFixture.checkResult(editedContent)
    }
  }

  @Test
  fun testThematicBreaksUseEmptyFoldsAndOwnedRules(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "---\n***\n___\ntail"
      configure("$content<caret>")

      assertEquals(3, thematicBreakHighlighters().size)
      assertEquals(listOf("---", "***", "___"), concealed())
      myFixture.checkResult(content)
    }
  }

  @Test
  @TestFor(issues = ["IJPL-261672"])
  fun testSplittingThematicBreakIntoTwoLinesKeepsBothRules(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val rule = "-----------------"
      val content = "$rule\n\ntail"
      val splitOffset = rule.length / 2 + 1
      configure("$content<caret>")

      moveCaretTo(splitOffset)
      myFixture.type("\n")
      myFixture.checkResult("${rule.take(splitOffset)}\n<caret>${rule.drop(splitOffset)}\n\ntail")
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      moveCaretTo(myFixture.editor.document.textLength)

      assertEquals(listOf(rule.take(splitOffset), rule.drop(splitOffset)), concealed())
      assertEquals(2, thematicBreakHighlighters().size)
    }
  }

  @Test
  fun testThematicBreakRevealsAndRestores(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "---\ntail"
      configure("$content<caret>")
      val rule = thematicBreakHighlighters().single()

      moveCaretTo(rule.startOffset)
      assertThat(concealed()).isEmpty()
      assertThat(thematicBreakHighlighters()).isEmpty()
      myFixture.checkResult(content)
      assertThat(imageInlays()).isEmpty()

      moveCaretTo(content.length)
      assertEquals(listOf("---"), concealed())
      assertEquals(1, thematicBreakHighlighters().size)
    }
  }

  @Test
  fun testDecorationKindChangesAtTheSameFoldRange(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("-----\n\ntail<caret>")
      val region = concealedLivePreviewRegions(myFixture.editor).single()
      val rule = thematicBreakHighlighters().single()

      writeCommandAction(myFixture.project).run<Throwable> {
        myFixture.editor.document.replaceString(0, 5, "- [ ]")
      }
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      val checkbox = checkboxInlays().single()
      assertSame(region, concealedLivePreviewRegions(myFixture.editor).single())
      assertFalse(rule.isValid)
      assertThat(thematicBreakHighlighters()).isEmpty()

      writeCommandAction(myFixture.project).run<Throwable> {
        myFixture.editor.document.replaceString(0, 5, "-----")
      }
      myFixture.doHighlighting()
      waitForCurrentSpecs()

      assertSame(region, concealedLivePreviewRegions(myFixture.editor).single())
      assertFalse(checkbox.isValid)
      assertThat(checkboxInlays()).isEmpty()
      assertEquals(1, thematicBreakHighlighters().size)
    }
  }

  @Test
  fun testHeadingAndTextFoldsReplaceEachOtherAtTheSameRange(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("-----\n\ntail<caret>")
      val textFold = concealedLivePreviewRegions(myFixture.editor).single()
      val rule = thematicBreakHighlighters().single()

      writeCommandAction(myFixture.project).run<RuntimeException> {
        myFixture.editor.document.replaceString(0, 5, "# abc")
      }
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      val headingFold = headingFolds().single()
      assertFalse(textFold.isValid)
      assertFalse(rule.isValid)
      assertEquals(listOf("# abc"), concealed())

      writeCommandAction(myFixture.project).run<RuntimeException> {
        myFixture.editor.document.replaceString(0, 5, "-----")
      }
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      assertFalse(headingFold.isValid)
      assertThat(headingFolds()).isEmpty()
      assertEquals(listOf("-----"), concealed())
      assertEquals(1, thematicBreakHighlighters().size)
    }
  }

  @Test
  fun testSetextUnderlineRuleDoesNotConcealInlineCodeOnThePreviousLine(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "`---`\n---\ntail"
      configure("$content<caret>")

      assertEquals(listOf("`---`", "`---`", "---"), computeLivePreviewSpecs(myFixture.file, myFixture.editor, configuration).elements
        .filterNot { it is MarkdownLivePreviewSpec.CodeFont }
        .map { content.substring(it.range.startOffset, it.range.endOffset) })
      assertEquals(content.indexOf('\n'), headingFolds().single().endOffset)
      assertEquals(listOf(content.lastIndexOf("---")), thematicBreakHighlighters().map { it.startOffset })
      assertEquals("---", concealed().last())
    }
  }

  @Test
  fun testCodeFontHighlightersFollowLivePreview(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "Call `foo()` here\n\n```\nval a = 1\n```\n\ntail"
      configure("$content<caret>")
      assertEquals(listOf("foo()", "```", "val a = 1", "```"), codeFontTexts())
      val highlighters = codeFontHighlighters()

      moveCaretTo(content.indexOf("foo"))
      assertEquals(listOf("```", "```"), concealed())
      assertEquals(highlighters, codeFontHighlighters())

      moveCaretTo(content.indexOf("= 1") + 3)
      myFixture.type("0")
      assertEquals(listOf("foo()", "```", "val a = 10", "```"), codeFontTexts())
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      assertEquals(listOf("foo()", "```", "val a = 10", "```"), codeFontTexts())

      myFixture.editor.setLivePreviewEnabledness(false, configuration)
      MarkdownLivePreviewReconciler.getExisting(myFixture.editor)!!.reconcileNow()
      assertThat(codeFontHighlighters()).isEmpty()
    }
  }

  @Test
  fun testCaretInACodeFenceRevealsBothMarkerLines(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "```kotlin\nval a = 1\n```\n\ntail"
      configure("$content<caret>")
      val concealedText = "kotlin\nval a = 1\n\n\ntail"
      assertEquals(concealedText, visibleText())

      for (offset in listOf(content.indexOf("kotlin") + 2, content.indexOf("= 1"), content.lastIndexOf("```"))) {
        moveCaretTo(content.length)
        moveCaretTo(offset)
        assertEquals(content, visibleText(), "The caret at $offset reveals the fence")
      }

      moveCaretTo(content.length)
      assertEquals(concealedText, visibleText())
    }
  }

  @Test
  fun testCodeFenceMarkersAndLanguageAreGrayAndPlainOnlyInLivePreview(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "```kotlin\nval a = 1\n```\n\ntail"
      configure("$content<caret>")
      val editor = myFixture.editor as EditorEx
      val global = EditorColorsManager.getInstance().globalScheme
      fun languageAttributes() = editor.highlighter.createIterator(content.indexOf("kotlin")).textAttributes
      fun markerAttributes() = editor.highlighter.createIterator(0).textAttributes

      for (attributes in listOf(languageAttributes(), markerAttributes())) {
        assertEquals(global.getAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT).foregroundColor, attributes.foregroundColor)
        assertEquals(Font.PLAIN, attributes.fontType)
      }
      val markup = DocumentMarkupModel.forDocument(editor.document, myFixture.project, true) as MarkupModelEx
      val gray = global.getAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT).foregroundColor
      val styledByMarkup = ArrayList<String>()
      markup.processRangeHighlightersOverlappingWith(content.indexOf("kotlin") + 1, content.indexOf("kotlin") + 2) {
        it.getTextAttributes(editor.colorsScheme)
          ?.takeIf { attributes -> attributes.foregroundColor.let { color -> color != null && color != gray } || attributes.fontType != Font.PLAIN }
          ?.let { attributes -> styledByMarkup += "${it.errorStripeTooltip} $attributes" }
        true
      }
      assertThat(styledByMarkup).describedAs("A highlighter of the daemon must not restyle the language").isEmpty()

      editor.setLivePreviewEnabledness(false, configuration)
      val globalLanguage = global.getAttributes(MarkdownHighlighterColors.CODE_FENCE_LANGUAGE)
      val globalMarker = global.getAttributes(MarkdownHighlighterColors.CODE_FENCE_MARKER)
      PlatformTestUtil.waitWithEventsDispatching("The fence keeps the live preview style", {
        languageAttributes() == globalLanguage && markerAttributes() == globalMarker
      }, 10)
    }
  }

  @Test
  fun testRevealedCodeFenceHasNoPadding(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "```bash\necho one\n```\n\ntail"
      configure("$content<caret>")
      fun paddings() = myFixture.editor.inlayModel.getInlineElementsInRange(0, content.length, MarkdownLivePreviewCodePaddingInlayRenderer::class.java)
      assertEquals(2, paddings().size)

      moveCaretTo(content.indexOf("one"))
      assertThat(paddings()).isEmpty()

      moveCaretTo(content.length)
      assertEquals(2, paddings().size)
    }
  }

  @Test
  fun testCodeFencePadsTheLanguageAndEachCodeLineOnTheLeft(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      for (opening in listOf("```bash", "``` bash")) {
        val content = "$opening\necho one\n\necho two\n```\n\ntail"
        configure("$content<caret>")
        val editor = myFixture.editor
        val paddings = editor.inlayModel.getInlineElementsInRange(0, content.length, MarkdownLivePreviewCodePaddingInlayRenderer::class.java)

        assertEquals(listOf(content.indexOf("bash"), content.indexOf("echo one"), content.indexOf("echo two")), paddings.map { it.offset })
        assertEquals(listOf(false, true, true), paddings.map { it.isRelatedToPrecedingText }, "Only the language padding belongs to the text after it")
        assertEquals(editor.offsetToXY(content.indexOf("echo one"), true, false).x, editor.offsetToXY(content.indexOf("bash"), true, false).x, "The language of $opening starts at the code")
        assertTrue(editor.offsetToXY(content.indexOf("echo one"), true, false).x > editor.offsetToXY(0).x, "The code starts after the padding")
      }
    }
  }

  @Test
  fun testFrontMatterDelimitersAndThematicBreakUseRuleDecorations(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = """
        |---
        |name: valid-skill
        |description: Does useful work.
        |---
        |
        |---
        |tail
      """.trimMargin()
      configure("$content<caret>")

      assertEquals(3, thematicBreakHighlighters().size)
      assertEquals(listOf("---", "---", "---"), concealed())
      myFixture.checkResult(content)

      val bodyRule = thematicBreakHighlighters().last()
      moveCaretTo(bodyRule.startOffset)
      assertEquals(listOf("---", "---"), concealed())
      assertEquals(2, thematicBreakHighlighters().size)

      moveCaretTo(content.length)
      assertEquals(listOf("---", "---", "---"), concealed())
      assertEquals(3, thematicBreakHighlighters().size)
    }
  }

  @Test
  fun testStandaloneLocalImageRendersAndRevealsItsSource(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(120, 60)
      val imageSource = "![alt](image.png)"
      val content = "$imageSource\n\ntail"
      configureProjectFile(content)

      val inlay = waitForImageInlay()
      assertEquals(imageSource.length, inlay.offset, "The image sits below its line")
      assertEquals(listOf(imageSource to "alt"), concealedWithPlaceholders())
      assertEquals("alt\n\ntail", visibleText(), "The concealed line shows the alt text")
      assertTrue(concealedLivePreviewRegions(myFixture.editor).all { FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND.isIn(it) })
      myFixture.checkResult(content)

      moveCaretTo(0)
      assertThat(concealed()).describedAs("The caret on the line reveals the source").isEmpty()
      assertSame(inlay, imageInlays().single(), "The image stays while the source is revealed")
      myFixture.checkResult(content)

      moveCaretTo(content.length)
      assertEquals(listOf(imageSource), concealed())
      assertSame(inlay, imageInlays().single())
    }
  }

  @Test
  fun testInlineImageConcealsOnlyItsElement(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(120, 60)
      val imageSource = "![alt](image.png)"
      val content = "Text $imageSource here\n\ntail"
      configureProjectFile(content)

      val inlay = waitForImageInlay()
      assertEquals(content.indexOf('\n'), inlay.offset)
      assertEquals(listOf(imageSource), concealed())
      assertEquals("Text alt here\n\ntail", visibleText())

      moveCaretTo(2)
      assertEquals(listOf(imageSource), concealed(), "The caret on the text next to the image keeps it concealed")

      moveCaretTo(content.indexOf("alt"))
      assertThat(concealed()).describedAs("The caret on the element reveals it").isEmpty()
      assertSame(inlay, imageInlays().single())
    }
  }

  @Test
  fun testImagesInListItemBlockQuoteAndHeaderRender(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(120, 60)
      val content = "- ![a](image.png)\n\n> ![b](image.png)\n\n# ![c](image.png)\n\ntail"
      configureProjectFile(content)

      waitForImageInlays(3)
      assertEquals(listOf("![a](image.png)", "![b](image.png)", "![c](image.png)"), concealed().filter { it.startsWith("![") })
      assertEquals(content.indexOf("# "), headingFolds().single().startOffset)
      assertTrue(visibleText().startsWith("• a\n\n b\n\n"))
      assertEquals(
        listOf(0, 2, 4),
        imageInlays().map { myFixture.editor.document.getLineNumber(it.offset) }.sorted(),
      )
    }
  }

  @Test
  fun testBlockquoteCaretRevealsOnlyTheTouchedMarker(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "> first\n> second\n\ntail"
      configure("$content<caret>")
      val concealedText = " first\n second\n\ntail"
      assertEquals(concealedText, visibleText())

      moveCaretTo(content.indexOf("first"))
      assertEquals(concealedText, visibleText(), "The caret at the start of the quote text keeps the marker")

      moveCaretTo(0)
      assertEquals("> first\n second\n\ntail", visibleText())

      moveCaretTo(content.indexOf("second") - 1)
      assertEquals(" first\n> second\n\ntail", visibleText())

      moveCaretTo(content.length)
      assertEquals(concealedText, visibleText())
    }
  }

  @Test
  fun testNestedBlockquoteCaretRevealsTheMarkerRunOfItsLine(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "> outer\n> > inner\n> last\n\ntail"
      configure("$content<caret>")
      val concealedText = " outer\n  inner\n last\n\ntail"
      assertEquals(concealedText, visibleText())

      val innerLine = content.indexOf("> > inner")
      for (offset in innerLine..innerLine + 3) {
        moveCaretTo(offset)
        assertEquals(" outer\n> > inner\n last\n\ntail", visibleText(), "The caret at $offset must reveal the run of its line")
      }

      moveCaretTo(content.indexOf("inner"))
      assertEquals(concealedText, visibleText())

      moveCaretTo(0)
      assertEquals("> outer\n  inner\n last\n\ntail", visibleText())

      moveCaretTo(content.length)
      assertEquals(concealedText, visibleText())
    }
  }

  @Test
  fun testOnlyOffsetsOnTheBlockquoteMarkerRunRevealIt(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertNothingLogged {
        for (line in listOf("> quote text  ", "  > indented", ">", ">>nested", "> > spaced")) {
          val content = "$line\n\ntail"
          configure("$content<caret>")
          val markers = List(line.count { it == '>' }) { ">" }
          val run = line.indexOf('>')..line.lastIndexOf('>') + 1
          for (offset in 0..line.length) {
            moveCaretTo(offset)
            if (offset in run) {
              assertThat(concealed()).describedAs("The caret at $offset must reveal '$line'").isEmpty()
              assertThat(blockQuoteRules()).isEmpty()
            }
            else {
              assertEquals(markers, concealed(), "The caret at $offset must keep '$line'")
              assertEquals(markers.size, blockQuoteRules().size)
            }
            moveCaretTo(content.length)
            assertEquals(markers, concealed())
          }
        }
      }
    }
  }

  @Test
  fun testRevealingAQuoteMarkerKeepsTheTextInPlace(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      for (line in listOf("> text", ">text", "> > text", ">>text")) {
        configure("$line\n\ntail<caret>")
        val editor = myFixture.editor
        editor.colorsScheme.editorFontName = Font.SANS_SERIF
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertEquals(List(line.count { it == '>' }) { ">" }, concealed())
        val concealedX = editor.offsetToXY(line.indexOf("text")).x

        moveCaretTo(0)
        assertThat(concealed()).isEmpty()
        assertEquals(concealedX, editor.offsetToXY(line.indexOf("text")).x, "Revealing '$line' must keep the text in place")
      }
    }
  }

  @Test
  fun testEachConcealedBlockquoteMarkerPaintsItsOwnRule(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "- > first\n  > second\n  > third\n\ntail"
      configure("$content<caret>")
      val document = myFixture.editor.document
      val markers = listOf("> first", "> second", "> third").map(content::indexOf)
      val rules = listOf(
        markers[0] to document.getLineStartOffset(1),
        markers[1] to document.getLineStartOffset(2),
        markers[2] to document.getLineEndOffset(2),
      )
      assertEquals(rules, blockQuoteRules().map { it.startOffset to it.endOffset })

      moveCaretTo(content.indexOf("> second"))
      assertEquals(listOf("-", ">", ">"), concealed())
      assertEquals(listOf(rules[0], rules[2]), blockQuoteRules().map { it.startOffset to it.endOffset })

      moveCaretTo(content.length)
      assertEquals(rules, blockQuoteRules().map { it.startOffset to it.endOffset })
    }
  }

  @Test
  fun testAlertCaretRevealsItsTitle(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "> [!CAUTION]\n> text\n\ntail"
      configure("$content<caret>")
      val concealedText = " CAUTION\n text\n\ntail"
      assertEquals(concealedText, visibleText())

      moveCaretTo(content.indexOf("CAUTION"))
      assertEquals(" [!CAUTION]\n text\n\ntail", visibleText())

      moveCaretTo(0)
      assertEquals("> CAUTION\n text\n\ntail", visibleText())

      moveCaretTo(content.length)
      assertEquals(concealedText, visibleText())
    }
  }

  @Test
  fun testAlertRuleTakesTheAlertTitleColor(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("> [!CAUTION]\n> text\n\ntail<caret>")
      val editor = myFixture.editor
      editor.colorsScheme.setAttributes(MarkdownHighlighterColors.ALERT_TITLE_CAUTION, TextAttributes(Color.BLUE, null, null, null, Font.PLAIN))
      val rule = blockQuoteRules().minBy { it.startOffset }
      val bitmap = BufferedImage(1000, editor.lineHeight, BufferedImage.TYPE_INT_ARGB)
      val graphics = bitmap.createGraphics()
      try {
        rule.customRenderer!!.paint(editor, rule, graphics)
      }
      finally {
        graphics.dispose()
      }

      assertEquals(Color.BLUE.rgb, bitmap.getRGB(editor.offsetToXY(rule.startOffset).x, editor.lineHeight / 2))
    }
  }

  @Test
  fun testBulletTakesTheListMarkerColor(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("- item\n\ntail<caret>")
      val editor = myFixture.editor
      editor.colorsScheme.setAttributes(MarkdownHighlighterColors.LIST_MARKER, TextAttributes(Color.BLUE, null, null, null, Font.PLAIN))
      val bounds = concealedLivePreviewRegions(editor).single().bulletInlay(editor)!!.bounds!!

      val image = MarkdownTestingUtil.paintEditor(editor)
      val pixels = (bounds.x until bounds.x + bounds.width).flatMap { x -> (bounds.y until bounds.y + bounds.height).map { y -> image.getRGB(x, y) } }
      assertTrue(Color.BLUE.rgb in pixels, "The bullet must use the list marker color")
    }
  }

  @Test
  fun testPlaceholdersShowTheBackgroundOfTheirText(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("- > - item\n\ntail<caret>")
      val editor = myFixture.editor
      // The background of the quote ends at the right edge of the visible area.
      EditorTestUtil.setEditorVisibleSize(editor, 80, 12)
      editor.colorsScheme.setAttributes(MarkdownHighlighterColors.BLOCK_QUOTE, TextAttributes(null, Color.RED, null, null, Font.PLAIN))
      val [listBullet, quotedBullet] = concealedLivePreviewRegions(editor)

      var image = MarkdownTestingUtil.paintEditor(editor)
      assertFalse(Color.RED.rgb == placeholderBackground(image, listBullet), "A bullet before the quote must keep the editor background")
      assertEquals(Color.RED.rgb, placeholderBackground(image, quotedBullet))

      // A diff change paints its lines above the quote background.
      val change = TextAttributes(null, Color.GREEN, null, null, Font.PLAIN)
      editor.markupModel.addRangeHighlighter(0, 1, HighlighterLayer.SELECTION - 1, change, HighlighterTargetArea.LINES_IN_RANGE)
      image = MarkdownTestingUtil.paintEditor(editor)
      for (region in listOf(listBullet, quotedBullet)) {
        assertEquals(Color.GREEN.rgb, placeholderBackground(image, region), "The placeholder at ${region.startOffset} must show the change")
      }
    }
  }

  @Test
  fun testRevealedMarkersMoveOnlyTheRulesOfTheirLine(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = ">>text\n>>>>text\n> > > test\n\ntail"
      configure("$content<caret>")
      val editor = myFixture.editor
      val document = editor.document
      val concealedRules = paintedRules()
      assertEquals(9, concealedRules.size)
      for ([marker, bounds] in concealedRules) {
        assertEquals(editor.offsetToXY(marker).x, bounds.x, "The rule must start at the x position of its marker")
      }
      val outerRules = (0..2).map { concealedRules.getValue(document.getLineStartOffset(it)) }
      val _ = outerRules.zipWithNext { above, below -> assertEquals(above.y + above.height + 1, below.y, "The outer rules must join") }

      for (line in 0..2) {
        val lineStart = document.getLineStartOffset(line)
        select(lineStart, lineStart + 2)
        val otherRules = concealedRules.filterKeys { document.getLineNumber(it) != line }
        assertEquals(otherRules, paintedRules(), "A reveal on line $line must keep the rules of the other lines")
        editor.selectionModel.removeSelection()
        moveCaretTo(content.length)
        assertEquals(concealedRules, paintedRules())
      }
    }
  }

  @Test
  fun testImageWithoutAltTextShowsTheGenericPlaceholder(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(120, 60)
      configureProjectFile("![](image.png)\n\ntail")

      waitForImageInlay()
      assertEquals("${MarkdownBundle.message("markdown.live.preview.image.placeholder")}\n\ntail", visibleText())
    }
  }

  @Test
  fun testTwoImagesOnOneLineConcealEachElementAndProduceTwoInlays(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(120, 60)
      addBinaryFile("docs/image2.png", pngBytes(80, 40))
      val first = "![one](image.png)"
      val second = "![two](image2.png)"
      val line = "$first $second"
      configureProjectFile("$line\n\ntail")

      waitForImageInlays(2)
      assertEquals(listOf(first, second), concealed())
      assertEquals("one two\n\ntail", visibleText())
      assertEquals(setOf(line.length), imageInlays().mapTo(HashSet()) { it.offset })
      assertEquals(setOf("image.png", "image2.png"), imageInlays().mapTo(HashSet()) { it.renderer.destination })

      moveCaretTo(3)
      assertEquals(listOf(second), concealed(), "The caret in the first image reveals that image alone")
      assertEquals(2, imageInlays().size)
    }
  }

  @Test
  fun testImagesBetweenHeadersBothRender(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(120, 60)
      val content = "# Markdown WYSIWYG Demo\n\n![logo](image.png)\n\n![logo](image.png)\n\n# Markdown WYSIWYG Demo"
      configureProjectFile(content)

      assertEquals(2, computeLivePreviewSpecs(myFixture.file, myFixture.editor, configuration).elements
        .filterIsInstance<MarkdownLivePreviewSpec.Image>().size)
      waitForImageInlays(2)
    }
  }

  @Test
  fun testAdjacentImagesBothRender(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(120, 60)
      configureProjectFile("![logo](image.png)\n![logo](image.png)\n\ntail")

      waitForImageInlays(2)
    }
  }

  @Test
  fun testMovingCaretBetweenImagesKeepsBothInlays(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(120, 60)
      val first = "![first](image.png)"
      val second = "![second](image.png)"
      val content = "$first\n\n$second\n\ntail"
      configureProjectFile(content)
      waitForImageInlays(2)
      val inlays = imageInlays()

      moveCaretTo(first.length)
      assertEquals(listOf(second), concealed())
      assertEquals(inlays, imageInlays())

      moveCaretTo(content.indexOf(second) + second.length)
      assertEquals(listOf(first), concealed())
      assertEquals(inlays, imageInlays())
    }
  }

  @Test
  fun testSelectionOverImageYieldsMarkdownSource(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(80, 40)
      val imageSource = "![alt](image.png)"
      val content = "$imageSource\n\ntail"
      configureProjectFile(content)
      val inlay = waitForImageInlay()

      select(0, imageSource.length)

      assertThat(concealed()).isEmpty()
      assertEquals(imageSource, myFixture.editor.selectionModel.selectedText)
      assertSame(inlay, imageInlays().single())
    }
  }

  @Test
  fun testSvgImageUsesItsIntrinsicSize(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addBinaryFile(
        "docs/image.svg",
        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"90\" height=\"45\"><rect width=\"90\" height=\"45\"/></svg>".toByteArray(),
      )
      configureProjectFile("![alt](image.svg)\n\ntail")

      (val width, val height) = waitForImageInlay().renderer.source!!
      assertEquals(90, width)
      assertEquals(45, height)
    }
  }

  @Test
  fun testDataUriImageRendersWithTheSizeFromTheImageEngine(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"90\" height=\"45\"><rect width=\"90\" height=\"45\"/></svg>"
      val imageSource = "![alt](data:image/svg+xml;base64,${Base64.getEncoder().encodeToString(svg.toByteArray())})"
      configureProjectFile("$imageSource\n\ntail")
      EditorTestUtil.setEditorVisibleSize(myFixture.editor, 80, 12)

      val inlay = waitForImageInlay()
      assertNull(inlay.renderer.source, "The backend loads no browser-owned image")
      assertEquals(listOf(imageSource to "alt"), concealedWithPlaceholders())
      PlatformTestUtil.waitWithEventsDispatching(
        "The inlay did not take the size of the loaded image",
        { inlay.heightInPixels == 45 + 2 * JBUI.scale(2) },
        10,
      )
      assertSame(inlay, imageInlays().single())
    }
  }

  @Test
  fun testCachedAttachmentImageRendersFromProjectRoot(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addBinaryFile(".attachments/image.png", pngBytes(70, 35))
      configureProjectFile("![alt](/.attachments/image.png)\n\ntail")

      waitForImageInlay()
    }
  }

  @Test
  fun testEncodedLocalImagePathUsesSharedResolver(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addBinaryFile("docs/image with space.png", pngBytes(70, 35))
      configureProjectFile("![alt](image%20with%20space.png)\n\ntail")

      waitForImageInlay()
    }
  }

  @Test
  fun testSiblingPrefixPathOutsideProjectShowsAPlaceholder(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      // The sibling `<root>-other` of the content root shares its name prefix and lies outside it.
      // The fixture cleans the content root only, so the test deletes the sibling itself.
      val sibling = myFixture.tempDirFixture.getFile("")!!.name + "-other"
      val image = addBinaryFile("../$sibling/image.png", pngBytes(60, 30))
      Disposer.register(disposable) { VfsTestUtil.deleteFile(image.parent) }
      configureProjectFile("![alt](../../$sibling/image.png)\n\ntail")

      assertEquals(1, computeLivePreviewSpecs(myFixture.file, myFixture.editor, configuration).elements
        .filterIsInstance<MarkdownLivePreviewSpec.Image>().size)
      waitForPlaceholders(MarkdownBundle.message("markdown.live.preview.image.outside.project", "../../$sibling/image.png"))
    }
  }

  @Test
  fun testFileUriOutsideProjectRestoresItsSource(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val image = myFixture.addFileToProject("project-other/outside.png", "").virtualFile
      ApplicationManager.getApplication().runWriteAction { image.setBinaryContent(pngBytes(60, 30)) }
      val source = myFixture.addFileToProject("project/docs/test.md", "![alt](<${image.url}>)\n\ntail").virtualFile
      PsiTestUtil.addContentRoot(myFixture.module, source.parent!!.parent!!)
      myFixture.configureFromExistingVirtualFile(source)
      myFixture.editor.caretModel.moveToOffset(source.contentsToByteArray().size)
      myFixture.doHighlighting()
      waitForNoImageInlay()
    }
  }

  @Test
  fun testBrokenAndMissingImagesShowPlaceholders(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addBinaryFile("docs/broken.png", "not an image".toByteArray())
      configureProjectFile("![broken](broken.png)\n\n![missing](missing.png)\n\ntail")

      waitForPlaceholders(
        MarkdownBundle.message("markdown.live.preview.image.unreadable", "broken.png"),
        MarkdownBundle.message("markdown.live.preview.image.not.found", "missing.png"),
      )
      assertEquals(
        listOf("![broken](broken.png)" to "broken", "![missing](missing.png)" to "missing"),
        concealedWithPlaceholders(),
      )
    }
  }

  @Test
  fun testMissingImagePlaceholderBecomesTheImageWhenTheFileAppears(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configureProjectFile("![alt](image.png)\n\ntail")
      waitForPlaceholders(MarkdownBundle.message("markdown.live.preview.image.not.found", "image.png"))

      addPng(60, 30)

      waitForLoadedImage()
    }
  }

  @Test
  fun testUntrustedProjectShowsPlaceholderUntilTheProjectIsTrusted(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(60, 30)
      TrustedProjectsTestUtil.withTrustedProjectsCheckEnabled {
        TrustedProjects.setProjectTrusted(myFixture.project, false)
        try {
          configureProjectFile("![alt](image.png)\n\ntail")
          waitForPlaceholders(MarkdownBundle.message("markdown.live.preview.image.untrusted"))
          assertEquals(listOf("![alt](image.png)" to "alt"), concealedWithPlaceholders())

          TrustedProjects.setProjectTrusted(myFixture.project, true)

          waitForLoadedImage()
        }
        finally {
          TrustedProjects.setProjectTrusted(myFixture.project, true)
        }
      }
    }
  }

  @Test
  fun testBrokenDataUriShowsTheLoadFailedPlaceholder(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val destination = "data:image/png;base64,${Base64.getEncoder().encodeToString("not an image".toByteArray())}"
      configureProjectFile("![alt]($destination)\n\ntail")
      EditorTestUtil.setEditorVisibleSize(myFixture.editor, 80, 12)

      waitForPlaceholders(MarkdownBundle.message("markdown.live.preview.image.load.failed", destination))
    }
  }

  @Test
  fun testMermaidFenceShowsItsDiagramBelowTheFence(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val service = FixedCodeFenceRenderingService()
      CodeFenceRenderingService.EP_NAME.point.registerExtension(service, disposable)
      val fence = "```mermaid\ngraph LR\n  A --> B\n```"
      configure("$fence\n\ntail<caret>")

      PlatformTestUtil.waitWithEventsDispatching("The diagram did not arrive", { (mermaidInlays().singleOrNull()?.heightInPixels ?: 0) > 0 }, 10)
      val inlay = mermaidInlays().single()
      assertEquals(fence.length, inlay.offset)
      assertEquals("mermaid\ngraph LR\n  A --> B\n\n\ntail", visibleText())
      assertTrue(SwingUtilities.isDescendingFrom(service.views.single().component, myFixture.editor.contentComponent), "The editor hosts the view")
      assertEquals(20 * myFixture.editor.lineHeight + 2 * JBUI.scale(2), inlay.heightInPixels, "A tall diagram is capped at 20 lines")

      myFixture.type("!")
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      assertSame(inlay, mermaidInlays().single())
      assertEquals(1, service.renders.get())

      ApplicationManager.getApplication().messageBus.syncPublisher(LafManagerListener.TOPIC).lookAndFeelChanged(LafManager.getInstance())
      PlatformTestUtil.waitWithEventsDispatching("A new theme did not render the diagram again", { service.renders.get() == 2 }, 10)
    }
  }

  @Test
  fun testProhibitedElementsShowTheirSourceAndCaretRevealsNothing(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val service = FixedCodeFenceRenderingService()
      CodeFenceRenderingService.EP_NAME.point.registerExtension(service, disposable)
      addPng(120, 60)
      configuration = MarkdownLivePreviewConfiguration(
        prohibited = setOf(
          MarkdownLivePreviewConfiguration.IMAGES,
          MarkdownLivePreviewConfiguration.MERMAID,
          MarkdownLivePreviewConfiguration.HEADINGS,
          MarkdownLivePreviewConfiguration.BLOCK_QUOTES,
        ),
      )
      val content = "![alt](image.png)\n\n# title\n\n> [!NOTE]\n> text\n\n```mermaid\ngraph LR\n```\n\ntail"
      configureProjectFile(content)

      val fenceMarkers = listOf("```", "```")
      assertEquals(fenceMarkers, concealed())
      assertThat(imageInlays()).isEmpty()
      assertThat(headingFolds()).isEmpty()
      assertThat(blockQuoteRules()).isEmpty()
      assertThat(mermaidInlays()).isEmpty()
      for (element in listOf("alt", "title", "NOTE", "text")) {
        moveCaretTo(content.indexOf(element))
        assertEquals(fenceMarkers, concealed(), "The caret on $element must not reveal anything")
        assertThat(mermaidInlays()).isEmpty()
      }
      assertEquals(0, service.renders.get())
    }
  }

  @Test
  fun testNewConfigurationRendersPreviouslyProhibitedElements(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configuration = MarkdownLivePreviewConfiguration.DEFAULT_LIVE_PREVIEW_CONFIGURATION.copy(
        prohibited = setOf(MarkdownLivePreviewConfiguration.HEADINGS)
      )
      configure("# title\n\ntail<caret>")
      assertThat(headingFolds()).isEmpty()

      myFixture.editor.setLivePreviewEnabledness(true)

      PlatformTestUtil.waitWithEventsDispatching("The heading did not render", { headingFolds().size == 1 }, 10)
    }
  }

  @Test
  fun testImageRefreshesAfterVfsChange(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val image = addPng(100, 40)
      configureProjectFile("![alt](image.png)\n\ntail")
      val inlay = waitForImageInlay()
      val initialHeight = inlay.heightInPixels
      val initialStamp = image.modificationStamp

      ApplicationManager.getApplication().runWriteAction { image.setBinaryContent(pngBytes(100, 90)) }
      assertTrue(initialStamp != image.modificationStamp, "The image modification stamp must change")

      PlatformTestUtil.waitWithEventsDispatching(
        "Image inlay was not refreshed",
        { imageInlays().single().heightInPixels > initialHeight },
        10,
      )
      val refreshed = imageInlays().single()
      assertNotSame(inlay, refreshed, "A refresh replaces the inlay, because its renderer is immutable")
      assertFalse(inlay.isValid)
      assertEquals(image.modificationStamp, refreshed.renderer.source!!.stamp)
      assertEquals(90, refreshed.renderer.source!!.height)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      assertEquals(listOf("![alt](image.png)"), concealed(), "The source stays concealed across the refresh")
    }
  }

  @Test
  fun testTypingAfterImageKeepsTheImageInlay(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(120, 60)
      configureProjectFile("![alt](image.png)\n\ntail")
      val inlay = waitForImageInlay()
      val renderer = inlay.renderer

      myFixture.type("x")
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

      assertSame(inlay, imageInlays().single(), "A keystroke outside the image must not recreate its inlay")
      assertSame(renderer, inlay.renderer, "A keystroke outside the image must not replace its renderer")
    }
  }

  @Test
  fun testAddingAnImageKeepsTheExistingInlay(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(120, 60)
      addBinaryFile("docs/image2.png", pngBytes(80, 40))
      configureProjectFile("![alt](image.png)\n\ntail")
      val inlay = waitForImageInlay()
      val renderer = inlay.renderer

      myFixture.type("\n\n![alt2](image2.png)\n")
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      waitForImageInlays(2)

      assertSame(inlay, imageInlays().minByOrNull { it.offset })
      assertSame(renderer, inlay.renderer, "Adding an image must not replace the renderer of an existing image")
    }
  }

  @Test
  fun testImageOverByteLimitIsRejectedAfterVfsRefresh(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val image = addPng(2, 2)
      configureProjectFile("![alt](image.png)\n\ntail")
      waitForImageInlay()
      Registry.get("markdown.live.preview.image.max.megabytes").setValue(0, disposable)

      ApplicationManager.getApplication().runWriteAction { image.setBinaryContent(pngBytes(3, 3)) }

      waitForPlaceholders(MarkdownBundle.message("markdown.live.preview.image.too.large", "image.png"))
    }
  }

  @Test
  fun testImageOverPixelLimitIsRejectedAfterVfsRefresh(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val image = addPng(2, 2)
      configureProjectFile("![alt](image.png)\n\ntail")
      waitForImageInlay()
      Registry.get("markdown.live.preview.image.max.megapixels").setValue(0, disposable)

      ApplicationManager.getApplication().runWriteAction { image.setBinaryContent(pngBytes(3, 3)) }

      waitForPlaceholders(MarkdownBundle.message("markdown.live.preview.image.too.large", "image.png"))
    }
  }

  @Test
  fun testResizingEditorDoesNotRecreateImageInlay(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(100, 40)
      configureProjectFile("![alt](image.png)\n\ntail")
      val inlay = waitForImageInlay()
      val renderer = inlay.renderer

      EditorTestUtil.setEditorVisibleSize(myFixture.editor, 40, 20)
      EditorTestUtil.setEditorVisibleSize(myFixture.editor, 80, 20)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

      assertSame(inlay, imageInlays().single())
      assertSame(renderer, inlay.renderer)
    }
  }

  @Test
  fun testClickingImagePreservesViewportPosition(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(240, 120)
      val content = (1..4).joinToString("\n\n") { "![logo](image.png)" } + "\n\n- tail"
      configureProjectFile(content)
      waitForImageInlays(4)
      val editor = myFixture.editor
      EditorTestUtil.setEditorVisibleSize(editor, 80, 12)
      val target = imageInlays().sortedBy { it.offset }[1]
      val targetLine = editor.offsetToVisualPosition(target.offset).line
      editor.scrollingModel.scrollVertically((editor.visualLineToY(targetLine) - editor.lineHeight).coerceAtLeast(0))
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      val verticalOffset = editor.scrollingModel.verticalScrollOffset

      EditorMouseFixture(editor as EditorImpl).clickAt(targetLine, 1)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

      assertEquals(verticalOffset, editor.scrollingModel.verticalScrollOffset)
      assertEquals(4, imageInlays().size)
    }
  }

  @Test
  fun testMovingCaretAcrossSmallImageDoesNotJumpViewport(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(40, 20)
      val image = "![logo](image.png)"
      val content = (1..8).joinToString("\n") { "before$it" } + "\n\n$image\n\n" +
                    (1..8).joinToString("\n") { "after$it" }
      configureProjectFile(content)
      val editor = myFixture.editor
      waitForImageInlay()
      EditorTestUtil.setEditorVisibleSize(editor, 400, editor.lineHeight * 8)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

      val imageLine = editor.document.getLineNumber(content.indexOf(image))
      editor.scrollingModel.scrollVertically(editor.visualLineToY(imageLine - 3))
      moveCaretTo(editor.document.getLineStartOffset(imageLine - 1))
      val verticalOffset = editor.scrollingModel.verticalScrollOffset

      moveCaretDown()
      moveCaretDown()

      assertEquals(verticalOffset, editor.scrollingModel.verticalScrollOffset)
    }
  }

  @Test
  fun testMovingPastLargeImageUsesMinimumScroll(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(100, 1_000)
      val image = "![logo](image.png)"
      val content = (1..6).joinToString("\n") { "before$it" } + "\n\n$image\n\n" +
                    (1..12).joinToString("\n") { "after$it" }
      configureProjectFile(content)
      val editor = myFixture.editor
      val inlay = waitForImageInlay()
      assertEquals(20 * editor.lineHeight + 2 * JBUI.scale(2), inlay.heightInPixels, "A tall image is capped at 20 lines")
      // The platform scrolls by the minimum only when the caret line and one line of context on each side fit into
      // the viewport. The context line above the caret carries the image, so the viewport holds 24 lines.
      EditorTestUtil.setEditorVisibleSizeInPixels(editor, 300, editor.lineHeight * 24)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

      val imageLine = editor.document.getLineNumber(content.indexOf(image))
      moveCaretTo(editor.document.getLineStartOffset(imageLine))
      assertThat(concealed()).isEmpty()
      assertEquals(1, imageInlays().size)
      editor.scrollingModel.scrollVertically((editor.visualLineToY(imageLine) - editor.lineHeight * 2).coerceAtLeast(0))
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      val visibleArea = editor.scrollingModel.visibleArea

      moveCaretDown()

      val expected = expectedRelativeScroll(editor, visibleArea.y, visibleArea.height)
      assertEquals(expected, editor.scrollingModel.verticalScrollOffset)
      assertEquals(listOf(image), concealed())
      assertEquals(1, imageInlays().size)
    }
  }

  @Test
  fun testSelectingEverythingRevealsEverything(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "Some **bold** and `code`"
      configure(content)
      select(0, content.length)
      assertThat(concealed()).describedAs("Text cannot be selected while it is still hidden").isEmpty()
      assertEquals(content, myFixture.editor.selectionModel.selectedText)
    }
  }

  @Test
  fun testBackspacePastAnElementDeletesOneCharacter(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "**bold** x"
      configure(content)
      moveCaretTo(content.length - 1)
      assertEquals(listOf("**", "**"), concealed())
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      myFixture.checkResult("**bold**x")
    }
  }

  @Test
  fun testBackspaceInsideAnElementDeletesOneCharacter(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "**bold** x"
      configure(content)
      moveCaretTo("**bold".length)
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      myFixture.checkResult("**bol** x")
    }
  }

  @Test
  fun testBackspaceAfterAutolinkDeletesOneCharacter(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertBackspaceAfterElement("<https://example.org>")
    }
  }

  @Test
  fun testBackspaceAfterEmphasisDeletesOneCharacter(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertBackspaceAfterElement("*italic*")
    }
  }

  @Test
  fun testBackspaceAfterStrongEmphasisDeletesOneCharacter(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertBackspaceAfterElement("**bold**")
    }
  }

  @Test
  fun testBackspaceAfterCodeSpanDeletesOneCharacter(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertBackspaceAfterElement("`code`")
    }
  }

  @Test
  fun testBackspaceAfterInlineLinkDeletesOneCharacter(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertBackspaceAfterElement("[link](https://example.org)")
    }
  }

  @Test
  fun testBackspaceAfterStrikethroughDeletesOneCharacter(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertBackspaceAfterElement("~~deleted~~")
    }
  }

  @Test
  fun testBackspaceAfterHighlightDeletesOneCharacter(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertBackspaceAfterElement("==marked==")
    }
  }

  @Test
  fun testBackspaceAfterThematicBreakDeletesOneCharacter(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      assertBackspaceAfterElement("---")
    }
  }

  @Test
  fun testBackspaceAfterHeadingDeletesOneCharacterAtATime(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("# Header\n<caret>")
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      myFixture.checkResult("# Header<caret>")
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
      myFixture.checkResult("# Heade<caret>")
    }
  }

  @Test
  fun testBackspaceAfterImageDoesNotDeleteTheImage(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      addPng(120, 60)
      val image = "![logo](image.png)"
      val content = "`KotlinClass`\n\n$image\n"
      configureProjectFile("$content<caret>")
      val inlay = waitForImageInlay()

      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)

      myFixture.checkResult(content.dropLast(1))
      assertEquals(content.length - 1, myFixture.editor.caretModel.offset)
      myFixture.doHighlighting()
      waitForCurrentSpecs()
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      assertEquals(listOf("`", "`"), concealed(), "The caret at the end of the image line reveals the source")
      assertSame(inlay, imageInlays().single())
    }
  }

  @Test
  fun testDeleteAfterAnElementDeletesOneCharacter(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "**bold** x"
      configure(content)
      moveCaretTo(content.indexOf(" x"))
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
      myFixture.checkResult("**bold**x")
    }
  }

  @Test
  fun testLivePreviewToggleRepublishesSpecs(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("Some **bold** text<caret>")
      assertEquals(listOf("**", "**"), concealed())

      myFixture.editor.setLivePreviewEnabledness(false, configuration)
      myFixture.doHighlighting()
      waitForConcealed(emptyList())

      myFixture.editor.setLivePreviewEnabledness(true, configuration)
      myFixture.doHighlighting()
      waitForConcealed(listOf("**", "**"))
    }
  }

  @Test
  fun testClearingSpecsAndDisablingPreviewRemoveAllDecorations(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configureAllDecorations()
      val editor = myFixture.editor
      val reconciler = MarkdownLivePreviewReconciler.getExisting(editor)!!
      val specs = computeLivePreviewSpecs(myFixture.file, editor, configuration)
      val folds = concealedLivePreviewRegions(editor)
      val checkbox = checkboxInlays().single()
      val indents = listIndentInlays()
      val rule = thematicBreakHighlighters().single()
      val image = imageInlays().single()

      reconciler.publishSpecsAndWait(null)

      assertThat(concealed()).isEmpty()
      assertThat(checkboxInlays()).isEmpty()
      assertThat(listIndentInlays()).isEmpty()
      assertThat(thematicBreakHighlighters()).isEmpty()
      assertThat(imageInlays()).isEmpty()
      assertTrue(folds.all { !it.isValid })
      assertFalse(checkbox.isValid)
      assertTrue(indents.none { it.isValid })
      assertFalse(rule.isValid)
      assertFalse(image.isValid)

      reconciler.publishSpecsAndWait(specs)
      assertEquals(5, concealed().size)
      assertEquals(1, checkboxInlays().size)
      assertEquals(2, listIndentInlays().size)
      assertEquals(1, thematicBreakHighlighters().size)
      assertEquals(1, imageInlays().size)

      editor.setLivePreviewEnabledness(false, configuration)
      reconciler.reconcileNow()

      assertThat(concealed()).isEmpty()
      assertThat(checkboxInlays()).isEmpty()
      assertThat(listIndentInlays()).isEmpty()
      assertThat(thematicBreakHighlighters()).isEmpty()
      assertThat(imageInlays()).isEmpty()
    }
  }

  @Test
  fun testManyBulletsChangeInOneInlayBatch(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val items = 150
      configure((1..items).joinToString("\n") { "- item $it" } + "\n\ntail<caret>")
      val editor = myFixture.editor
      val reconciler = MarkdownLivePreviewReconciler.getExisting(editor)!!
      val specs = computeLivePreviewSpecs(myFixture.file, editor, configuration)
      assertEquals(items, bulletInlays().size)
      var batches = 0
      editor.inlayModel.addListener(object : InlayModel.Listener {
        override fun onBatchModeStart(editor: Editor) {
          batches++
        }
      }, myFixture.testRootDisposable)

      assertEquals(items, listIndentInlays().size)
      reconciler.publishSpecsAndWait(specs)
      assertEquals(0, batches, "A publish that keeps every bullet must not start an inlay batch")

      reconciler.publishSpecsAndWait(null)
      assertThat(bulletInlays()).isEmpty()
      assertThat(listIndentInlays()).isEmpty()
      assertEquals(2, batches, "The removal of the bullets and of the list indents must take one inlay batch each")

      reconciler.publishSpecsAndWait(specs)
      assertEquals(items, bulletInlays().size)
      assertEquals(items, listIndentInlays().size)
      assertEquals(4, batches, "The creation of the bullets and of the list indents must take one inlay batch each")
    }
  }

  @Test
  fun testDisposalRemovesAllDecorationsAndIgnoresLaterUpdates(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configureAllDecorations()
      val editor = myFixture.editor
      val reconciler = MarkdownLivePreviewReconciler.getExisting(editor)!!
      val specs = computeLivePreviewSpecs(myFixture.file, editor, configuration)

      Disposer.dispose(reconciler)
      reconciler.publishSpecsAndWait(specs)
      reconciler.reconcileNow()
      moveCaretTo(0)

      assertFalse(reconciler.hasCurrentSpecs())
      assertThat(concealed()).isEmpty()
      assertThat(checkboxInlays()).isEmpty()
      assertThat(listIndentInlays()).isEmpty()
      assertThat(thematicBreakHighlighters()).isEmpty()
      assertThat(imageInlays()).isEmpty()
    }
  }

  @Test
  fun testDiffEditorHidesNothing(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("Some **bold** text<caret>")
      val document = myFixture.editor.document
      val diffEditor = EditorFactory.getInstance().createEditor(document, myFixture.project, myFixture.file.virtualFile, false, EditorKind.DIFF)
      try {
        val reconciler = MarkdownLivePreviewReconciler.getOrCreate(diffEditor)!!
        reconciler.publishSpecsAndWait(computeLivePreviewSpecs(myFixture.file, myFixture.editor, configuration))
        assertThat(concealed(diffEditor)).describedAs("A diff editor must show the raw source").isEmpty()
      }
      finally {
        EditorFactory.getInstance().releaseEditor(diffEditor)
      }
    }
  }

  @Test
  fun testUnmarkedMainEditorHidesNothing(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("Some **bold** text<caret>")
      val document = myFixture.editor.document
      val mainEditor = EditorFactory.getInstance().createEditor(document, myFixture.project, myFixture.file.virtualFile, false, EditorKind.MAIN_EDITOR)
      try {
        assertFalse(mainEditor.isLivePreviewEnabled())
        val reconciler = MarkdownLivePreviewReconciler.getOrCreate(mainEditor)!!
        reconciler.publishSpecsAndWait(computeLivePreviewSpecs(myFixture.file, myFixture.editor, configuration))
        assertThat(concealed(mainEditor)).describedAs("A main editor without live preview must show the raw source").isEmpty()
      }
      finally {
        EditorFactory.getInstance().releaseEditor(mainEditor)
      }
    }
  }

  /** Exercises the binary search the reconciler uses to find the elements around a caret. */
  @Test
  fun testOnlyTheTouchedElementRevealsInAManyElementDocument(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = (1..40).joinToString(" ") { "**w$it**" } + " tail"
      configure(content)
      val target = content.indexOf("**w20**")
      moveCaretTo(target + 3)
      assertEquals(40 * 2 - 2, concealed().size, "Every element but the one under the caret stays hidden")
      moveCaretTo(content.length)
      assertEquals(40 * 2, concealed().size, "With the caret past the end nothing is revealed")
    }
  }

  @Test
  fun testRevealingElementsWithDifferentFoldCounts(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val content = "    code\n\n**bold**\n\n- [ ] task\n\n-----\n\ntail"
      configureProjectFile(content)
      assertEquals(listOf("**", "**", "- [ ]", "-----"), concealed())

      moveCaretTo(content.indexOf("bold") + 1)
      assertEquals(listOf("- [ ]", "-----"), concealed())
      assertEquals(1, checkboxInlays().size)

      moveCaretTo(content.indexOf("- [ ]"))
      assertEquals(listOf("**", "**", "-----"), concealed())
      assertThat(checkboxInlays()).isEmpty()

      moveCaretTo(content.indexOf("-----") + 1)
      assertEquals(listOf("**", "**", "- [ ]"), concealed())
      assertThat(thematicBreakHighlighters()).isEmpty()

      moveCaretTo(content.indexOf("code") + 1)
      assertEquals(listOf("**", "**", "- [ ]", "-----"), concealed())
      select(content.indexOf("bold"), content.indexOf("task") + 1)
      assertEquals(listOf("-----"), concealed())
    }
  }

  @Test
  fun testSpecsFromAnOlderDocumentAreDeclined(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      configure("Some **bold** text")
      val editor = myFixture.editor
      val reconciler = MarkdownLivePreviewReconciler.getExisting(editor)!!
      reconciler.publishSpecsAndWait(null)
      val elements = computeLivePreviewSpecs(myFixture.file, editor, configuration).elements
      val oldVersion = MarkdownLivePreviewDocumentVersion.capture(editor.document, myFixture.project)
      (editor.document as DocumentEx).setModificationStamp(editor.document.modificationStamp + 1)

      reconciler.publishSpecsAndWait(
        MarkdownLivePreviewSpecSet(oldVersion, elements)
      )
      assertThat(concealed()).describedAs("Specs computed from an older document must be declined").isEmpty()

      reconciler.publishSpecsAndWait(
        MarkdownLivePreviewSpecSet(MarkdownLivePreviewDocumentVersion.capture(editor.document, myFixture.project), elements)
      )
      assertEquals(listOf("**", "**"), concealed())
    }
  }

  @Test
  fun testSpecsPublishedDuringBulkUpdateAreAppliedAfterItFinishes(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      myFixture.configureByText("test.md", "Some **bold** text<caret>")
      PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
      val editor = myFixture.editor
      val reconciler = MarkdownLivePreviewReconciler.getOrCreate(editor)!!
      val specSet = computeLivePreviewSpecs(myFixture.file, editor, configuration)

      DocumentUtil.executeInBulk(editor.document, true) {
        reconciler.publishSpecsAndWait(specSet)
        assertThat(concealed()).describedAs("A bulk update must delay reconciliation").isEmpty()
      }

      PlatformTestUtil.waitWithEventsDispatching(
        "The pending live-preview specs were not applied",
        { concealed() == listOf("**", "**") },
        10,
      )
    }
  }

  /**
   * The x range, relative to the single heading fold, where the heading paints its text.
   * Live preview soft-wraps the heading, so the fold is as wide as the editor, and the text fills only a part of it.
   */
  private fun paintedHeadingColumns(): IntRange {
    val background = myFixture.editor.colorsScheme.defaultBackground
    val bitmap = paintHeading(headingFolds().single(), background)
    val columns = (0 until bitmap.width).filter { x -> (0 until bitmap.height).any { y -> bitmap.getRGB(x, y) != background.rgb } }
    assertThat(columns).isNotEmpty()
    return columns.first()..columns.last()
  }

  private fun BufferedImage.paintedRows(background: Color): IntRange {
    val rows = (0 until height).filter { y -> (0 until width).any { x -> getRGB(x, y) != background.rgb } }
    assertThat(rows).isNotEmpty()
    return rows.first()..rows.last()
  }

  private fun paintHeading(fold: CustomFoldRegion, background: Color): BufferedImage {
    val bitmap = BufferedImage(fold.widthInPixels, fold.heightInPixels, BufferedImage.TYPE_INT_RGB)
    val graphics = bitmap.createGraphics()
    try {
      graphics.color = background
      graphics.fillRect(0, 0, bitmap.width, bitmap.height)
      fold.renderer.paint(fold, graphics, Rectangle2D.Double(0.0, 0.0, bitmap.width.toDouble(), bitmap.height.toDouble()), TextAttributes())
    }
    finally {
      graphics.dispose()
    }
    return bitmap
  }

  private fun paintStickyLine(editor: EditorImpl, highlighter: RangeHighlighter, sticky: Boolean): IntArray {
    val bitmap = BufferedImage(editor.scrollingModel.visibleArea.width, editor.lineHeight, BufferedImage.TYPE_INT_RGB)
    val graphics = bitmap.createGraphics()
    editor.isStickyLinePainting = sticky
    try {
      graphics.color = Color.RED
      graphics.fillRect(0, 0, bitmap.width, bitmap.height)
      graphics.translate(0, -editor.visualLineToY(0))
      highlighter.customRenderer!!.paint(editor, highlighter, graphics)
    }
    finally {
      editor.isStickyLinePainting = false
      graphics.dispose()
    }
    return bitmap.getRGB(0, 0, bitmap.width, bitmap.height, null, 0, bitmap.width)
  }

  /** Clicks the folded heading at the x that [x] gives, and returns the caret offset after the click. */
  private fun clickHeading(x: (CustomFoldRegion) -> Int): Int {
    moveCaretTo(myFixture.editor.document.textLength)
    val fold = headingFolds().single()
    val location = fold.location!!
    EditorMouseFixture(myFixture.editor as EditorImpl).clickAtXY(location.x + x(fold), location.y + fold.heightInPixels / 2)
    assertThat(headingFolds()).isEmpty()
    return myFixture.editor.caretModel.offset
  }

  private fun assertHeadingHeightIsStable(content: String) {
    val editor = myFixture.editor
    val after = content.indexOf("after")
    val y = editor.offsetToXY(after).y
    repeat(3) {
      moveCaretTo(content.indexOf('#'))
      assertThat(headingFolds()).isEmpty()
      assertEquals(y, editor.offsetToXY(after).y)
      moveCaretTo(content.length)
      assertEquals(1, headingFolds().size)
      assertEquals(y, editor.offsetToXY(after).y)
    }
  }

  /** The background that the editor paints under the character at [offset], merged from all of its highlighters. */
  private fun backgroundAt(offset: Int): Color? =
    IterationState(myFixture.editor as EditorEx, offset, offset + 1, null, false, false, false, false).mergedAttributes.backgroundColor

  private fun headingFolds(): List<CustomFoldRegion> =
    myFixture.editor.foldingModel.allFoldRegions.filterIsInstance<CustomFoldRegion>().sortedBy { it.startOffset }

  /**
   * Shows [content] as source in a small viewport with the caret at [caretOffset], as an editor does before its specs arrive.
   * Returns the specs to publish.
   */
  private fun configureWithLateSpecs(content: String, caretOffset: Int): MarkdownLivePreviewSpecSet {
    configure(content)
    val editor = myFixture.editor
    val specs = computeLivePreviewSpecs(myFixture.file, editor, configuration)
    MarkdownLivePreviewReconciler.getExisting(editor)!!.publishSpecsAndWait(null)
    EditorTestUtil.setEditorVisibleSize(editor, 80, 10)
    moveCaretTo(caretOffset)
    assertThat(headingFolds()).isEmpty()
    return specs
  }

  private fun assertBackspaceAfterElement(element: String) {
    val content = "$element\n"
    configure("$content<caret>")

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)

    myFixture.checkResult(element)
    assertEquals(element.length, myFixture.editor.caretModel.offset)
  }

  private fun configure(content: String) {
    myFixture.configureByText("test.md", content)
    myFixture.doHighlighting()
    waitForCurrentSpecs()
  }

  private fun configureAllDecorations() {
    addPng(80, 40)
    configureProjectFile("- [ ] task\n  - nested\n\n-----\n\n![alt](image.png)\n\n# heading\n\ntail")
    waitForImageInlay()
    assertEquals(5, concealed().size)
    assertEquals(1, checkboxInlays().size)
    assertEquals(2, listIndentInlays().size)
    assertEquals(1, thematicBreakHighlighters().size)
  }

  private fun checkboxInlays(): List<Inlay<out MarkdownLivePreviewCheckboxInlayRenderer>> =
    myFixture.editor.inlayModel.getInlineElementsInRange(
      0, myFixture.editor.document.textLength, MarkdownLivePreviewCheckboxInlayRenderer::class.java,
    )

  private fun bulletInlays(): List<Inlay<out MarkdownLivePreviewBulletInlayRenderer>> =
    myFixture.editor.inlayModel.getInlineElementsInRange(
      0, myFixture.editor.document.textLength, MarkdownLivePreviewBulletInlayRenderer::class.java,
    )

  private fun listIndentInlays(): List<Inlay<out MarkdownLivePreviewListIndentInlayRenderer>> =
    myFixture.editor.inlayModel.getInlineElementsInRange(
      0, myFixture.editor.document.textLength, MarkdownLivePreviewListIndentInlayRenderer::class.java,
    )

  private fun clickCheckbox(inlay: Inlay<*>) {
    val editor = myFixture.editor as EditorImpl
    EditorTestUtil.setEditorVisibleSize(editor, 80, 12)
    val bounds = inlay.bounds!!
    EditorMouseFixture(editor).clickAtXY(bounds.x + 2, bounds.y + bounds.height / 2)
  }

  private fun waitForDocument(expected: String) {
    PlatformTestUtil.waitWithEventsDispatching("The checkbox edit was not applied", { myFixture.editor.document.text == expected }, 10)
    myFixture.doHighlighting()
    waitForCurrentSpecs()
  }

  private fun configureProjectFile(content: String) {
    myFixture.addFileToProject("docs/test.md", content)
    myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("docs/test.md"))
    myFixture.editor.caretModel.moveToOffset(content.length)
    myFixture.doHighlighting()
    waitForCurrentSpecs()
  }

  private fun waitForCurrentSpecs() {
    PlatformTestUtil.waitWithEventsDispatching(
      "Live-preview specs were not reconciled",
      { MarkdownLivePreviewReconciler.getExisting(myFixture.editor)?.hasCurrentSpecs() == true },
      10,
    )
  }

  private fun waitForConcealed(expected: List<String>) {
    PlatformTestUtil.waitWithEventsDispatching(
      "Expected $expected to be concealed",
      { concealed() == expected },
      10,
    )
  }

  private fun addPng(width: Int, height: Int): VirtualFile = addBinaryFile("docs/image.png", pngBytes(width, height))

  private fun addBinaryFile(path: String, bytes: ByteArray): VirtualFile {
    return myFixture.addFileToProject(path, "").virtualFile.also {
      ApplicationManager.getApplication().runWriteAction { it.setBinaryContent(bytes) }
    }
  }

  private fun pngBytes(width: Int, height: Int): ByteArray {
    val output = ByteArrayOutputStream()
    check(ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output))
    return output.toByteArray()
  }

  private fun waitForImageInlay(): Inlay<out MarkdownLivePreviewImageInlayRenderer> {
    waitForImageInlays(1)
    return imageInlays().single()
  }

  /** Waits until [count] image inlays exist. */
  private fun waitForImageInlays(count: Int) {
    PlatformTestUtil.waitWithEventsDispatching(
      "Image inlays were not added",
      { imageInlays().size == count },
      10,
    )
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  /** Waits until the image inlays, in document order, show the placeholders with [texts]. */
  private fun waitForPlaceholders(vararg texts: String) {
    PlatformTestUtil.waitWithEventsDispatching(
      "Image placeholders were not shown",
      { imageInlays().sortedBy { it.offset }.map { it.renderer.inlayText } == texts.toList() },
      10,
    )
  }

  private fun waitForLoadedImage() {
    PlatformTestUtil.waitWithEventsDispatching(
      "The image did not replace the placeholder",
      { imageInlays().singleOrNull()?.renderer?.source != null },
      10,
    )
    assertNull(imageInlays().single().renderer.inlayText)
  }

  private fun waitForNoImageInlay() {
    PlatformTestUtil.waitWithEventsDispatching(
      "Image inlay was not removed",
      { imageInlays().isEmpty() },
      10,
    )
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun moveCaretTo(offset: Int) {
    myFixture.editor.caretModel.moveToOffset(offset)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun moveCaretDown() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun expectedRelativeScroll(editor: Editor, viewportY: Int, viewportHeight: Int): Int {
    val caretY = editor.visualLineToY(editor.caretModel.visualPosition.line)
    val lineHeight = editor.lineHeight
    val scrollOffset = editor.settings.verticalScrollOffset * lineHeight
    val topBound = caretY - scrollOffset
    val bottomBound = caretY + scrollOffset + lineHeight
    return when {
      viewportY > topBound -> topBound
      viewportY + viewportHeight < bottomBound -> bottomBound - viewportHeight
      else -> viewportY
    }.coerceAtLeast(0)
  }

  private fun select(start: Int, end: Int) {
    myFixture.editor.selectionModel.setSelection(start, end)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  /** The concealed markup, in document order. A blockquote marker counts as concealed while its glyph is hidden. */
  private fun concealed(editor: Editor = myFixture.editor): List<String> {
    val text = editor.document.charsSequence
    return concealedRanges(editor).map { [range, _] -> text.subSequence(range.startOffset, range.endOffset).toString() }
  }

  private fun concealedWithPlaceholders(editor: Editor = myFixture.editor): List<Pair<String, String>> {
    val text = editor.document.charsSequence
    return concealedLivePreviewRegions(editor).map {
      text.subSequence(it.startOffset, it.endOffset).toString() to it.shownText(editor)
    }
  }

  /** The text that [this] region shows: its placeholder, or the glyph of the bullet inlay at its end. */
  private fun FoldRegion.shownText(editor: Editor): String = placeholderText.ifEmpty { bulletInlay(editor)?.renderer?.glyph.orEmpty() }

  private fun FoldRegion.bulletInlay(editor: Editor): Inlay<out MarkdownLivePreviewBulletInlayRenderer>? =
    editor.inlayModel.getInlineElementsInRange(endOffset, endOffset, MarkdownLivePreviewBulletInlayRenderer::class.java).firstOrNull()

  /** Returns the logical text with markup removed. Blank placeholders and hidden quote markers only reserve visual space. */
  private fun visibleText(): String {
    val editor = myFixture.editor
    val text = editor.document.charsSequence
    val result = StringBuilder()
    var offset = 0
    for ([range, placeholder] in concealedRanges(editor)) {
      if (range.startOffset > offset) result.append(text, offset, range.startOffset)
      if (placeholder.any { !it.isWhitespace() }) result.append(placeholder)
      offset = maxOf(offset, range.endOffset)
    }
    result.append(text, offset, text.length)
    return result.toString()
  }

  /** Returns the pixel at the top right of a placeholder, clear of its glyph and of a blockquote rule. */
  private fun placeholderBackground(image: BufferedImage, region: FoldRegion): Int {
    region.bulletInlay(myFixture.editor)?.bounds?.let { return image.getRGB(it.x + it.width - 1, it.y + 1) }
    val end = myFixture.editor.offsetToXY(region.endOffset)
    return image.getRGB(end.x - 1, end.y + 1)
  }

  private fun concealedLivePreviewRegions(editor: Editor): List<FoldRegion> =
    editor.foldingModel.allFoldRegions
      .filter { it.isValid && it.shouldNeverExpand() }
      .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))

  /** The fold ranges with their placeholders and the hidden quote markers with no placeholder, in document order. */
  private fun concealedRanges(editor: Editor): List<Pair<TextRange, String>> =
    (concealedLivePreviewRegions(editor).map { it.textRange to it.shownText(editor) } + hiddenQuoteMarkers(editor).map { it.textRange to "" })
      .sortedWith(compareBy({ it.first.startOffset }, { it.first.endOffset }))

  private fun hiddenQuoteMarkers(editor: Editor = myFixture.editor): List<RangeHighlighter> =
    editor.markupModel.allHighlighters
      .filter { it.isValid && it.getTextAttributes(null)?.foregroundColor?.alpha == 0 }
      .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))

  private fun blockQuoteRules(): List<RangeHighlighter> =
    myFixture.editor.markupModel.allHighlighters
      .filter { it.isValid && it.customRenderer is MarkdownBlockQuotePainter }
      .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))

  /** Paints each blockquote rule alone. Returns the painted bounds, keyed by the offset of the rule marker. */
  private fun paintedRules(): Map<Int, Rectangle> {
    val editor = myFixture.editor
    val height = editor.offsetToXY(editor.document.textLength).y + 2 * editor.lineHeight
    return blockQuoteRules().associate { rule ->
      val bitmap = BufferedImage(1000, height, BufferedImage.TYPE_INT_ARGB)
      val graphics = bitmap.createGraphics()
      try {
        rule.customRenderer!!.paint(editor, rule, graphics)
      }
      finally {
        graphics.dispose()
      }
      var bounds: Rectangle? = null
      for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
          if (bitmap.getRGB(x, y) ushr 24 == 0) continue
          bounds = bounds?.apply { add(x, y) } ?: Rectangle(x, y, 0, 0)
        }
      }
      rule.startOffset to checkNotNull(bounds) { "The rule at ${rule.startOffset} must paint" }
    }
  }

  private fun thematicBreakHighlighters(): List<RangeHighlighter> =
    myFixture.editor.markupModel.allHighlighters
      .filter { it.isValid && it.customRenderer != null && it.textAttributesKey == MarkdownHighlighterColors.HRULE }
      .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))

  private fun codeFontHighlighters(): List<RangeHighlighter> =
    myFixture.editor.markupModel.allHighlighters
      .filter { it.isValid && it.getTextAttributes(null)?.fontFamilies == FontFamilies.of("JetBrains Mono") }
      .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))

  private fun codeFontTexts(): List<String> {
    val text = myFixture.editor.document.charsSequence
    return codeFontHighlighters().map { text.subSequence(it.startOffset, it.endOffset).toString() }
  }

  private fun imageInlays(): List<Inlay<out MarkdownLivePreviewImageInlayRenderer>> {
    val editor = myFixture.editor
    return editor.inlayModel
      .getBlockElementsInRange(0, editor.document.textLength, MarkdownLivePreviewImageInlayRenderer::class.java)
      .filter { it.isValid }
  }

  private fun mermaidInlays(): List<Inlay<*>> =
    myFixture.editor.inlayModel.getBlockElementsInRange(0, myFixture.editor.document.textLength).filter { it.isValid }
}

/** Renders every source as the same empty [TallCodeFenceView]. */
private class FixedCodeFenceRenderingService : CodeFenceRenderingService {
  val renders = AtomicInteger()
  val views: MutableList<TallCodeFenceView> = Collections.synchronizedList(ArrayList())

  override suspend fun render(language: String, source: String, scope: CoroutineScope): CodeFenceView {
    renders.incrementAndGet()
    return TallCodeFenceView().also(views::add)
  }
}

/** An empty view that needs more height than the editor gives a code fence. */
private class TallCodeFenceView : CodeFenceView {
  override val component: JComponent = JPanel().apply { preferredSize = Dimension(0, 10_000) }
}
