// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.markdown.frontend.editor.livepreview.MARKDOWN_LIVE_PREVIEW_REGION
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewReconciler
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.impl.FoldingKeys
import com.intellij.markdown.frontend.editor.livepreview.MARKDOWN_LIVE_PREVIEW_HORIZONTAL_RULE
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.settings.MarkdownSettings

class MarkdownLivePreviewFoldingTest: BasePlatformTestCase() {

  private val settings get() = MarkdownSettings.getInstance(project)

  override fun setUp() {
    super.setUp()
    val livePreview = settings.enableLivePreview
    Disposer.register(testRootDisposable) { settings.enableLivePreview = livePreview }
    settings.enableLivePreview = true
  }

  fun testMarkupIsHiddenWhileTheCaretIsElsewhere() {
    configure("Some **bold**, *italic* and `code` here<caret>")
    assertEquals("Some bold, italic and code here", visibleText())
    assertTrue(concealedLivePreviewRegions(myFixture.editor).none { FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND.isIn(it) })
  }

  fun testInlineLinkShowsOnlyItsTitle() {
    configure("Read [the docs](https://example.org) today<caret>")
    assertEquals("Read the docs today", visibleText())
  }

  fun testAutolinksShowOnlyTheirTarget() {
    configure("See <https://example.org> or mail <team@example.org> here<caret>\n\ntail")
    assertEquals("See https://example.org or mail team@example.org here\n\ntail", visibleText())
  }

  fun testUnorderedListMarkersUseDepthPlaceholders() {
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
    assertTrue(concealedLivePreviewRegions(myFixture.editor).all { FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND.isIn(it) })
  }

  fun testCaretOnTheElementRevealsItsMarkers() {
    val content = "Some **bold** text"
    configure(content)
    moveCaretTo(content.length)
    assertEquals(listOf("**", "**"), concealed())

    // Immediately after the closing `**`, which is the element's end offset.
    moveCaretTo(content.indexOf(" text"))
    assertEmpty(concealed())
    assertEquals(content, visibleText())
  }

  /**
   * The load-bearing test: touching counts at both ends, so no concealing region may ever sit under or next
   * to a caret. Everything else in the design leans on this.
   */
  fun testEveryCaretOffsetAroundAnElement() {
    val content = "**bold** x"
    configure(content)
    val elementEnd = content.indexOf(" x")
    for (offset in 0..content.length) {
      moveCaretTo(offset)
      if (offset <= elementEnd) {
        assertEmpty("Caret at $offset touches the element and must reveal it", concealed())
      }
      else {
        assertEquals("Caret at $offset is outside the element, which must stay hidden", listOf("**", "**"), concealed())
      }
    }
  }

  fun testCaretOnEitherListMarkerBoundaryRevealsMarker() {
    val content = "- item\n\ntail"
    configure("$content<caret>")
    assertEquals(listOf("-" to "•"), concealedWithPlaceholders())

    moveCaretTo(0)
    assertEmpty(concealed())
    moveCaretTo(content.length)
    assertEquals(listOf("-"), concealed())
    moveCaretTo(2)
    assertEmpty(concealed())
  }

  fun testIndentingMarkerReplacesItsStalePlaceholder() {
    val content = "- parent\n- child\n\ntail"
    configure("$content<caret>")
    assertEquals(listOf("-" to "•", "-" to "•"), concealedWithPlaceholders())
    val staleChildRegion = concealedLivePreviewRegions(myFixture.editor)[1]

    val childText = content.indexOf("child")
    select(childText + 1, childText + 2)
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_INDENT_SELECTION)
    myFixture.doHighlighting()
    myFixture.editor.selectionModel.removeSelection()
    moveCaretTo(myFixture.editor.document.textLength)

    assertEquals(listOf("-" to "•", "-" to "◦"), concealedWithPlaceholders())
    assertNotSame(staleChildRegion, concealedLivePreviewRegions(myFixture.editor)[1])
  }

  fun testListMarkerConcealmentDoesNotMoveItemText() {
    val content = "- bullet\n1. ordered\n\ntail"
    configure("$content<caret>")
    val offsets = listOf(content.indexOf("bullet"), content.indexOf("ordered"))
    val concealedPositions = offsets.map { myFixture.editor.offsetToXY(it) }

    moveCaretTo(0)

    assertEmpty(concealed())
    assertEquals(concealedPositions, offsets.map { myFixture.editor.offsetToXY(it) })
  }

  fun testNestedElementRevealsItsAncestor() {
    val content = "**bold *and italic* here**"
    configure(content)
    moveCaretTo(content.indexOf("and"))
    assertEmpty("A caret in the inner element must reveal the outer one too", concealed())
  }

  fun testSelectionOverAnElementRevealsIt() {
    val content = "a **bold** b **more** c"
    configure(content)
    select(0, content.indexOf(" b"))
    assertEquals("Only the selected element is revealed", listOf("**", "**"), concealed())
  }

  fun testSelectionEndpointsAreNotDropped() {
    val content = "a **bold** b"
    configure(content)
    val start = content.indexOf("**")
    val end = start + "**bold**".length
    select(start, end)
    assertTrue("The selection must survive reconciliation", myFixture.editor.selectionModel.hasSelection())
    assertEquals(start, myFixture.editor.selectionModel.selectionStart)
    assertEquals(end, myFixture.editor.selectionModel.selectionEnd)
  }

  fun testMultipleCaretsRevealOnlyWhatTheyTouch() {
    val content = "**one** and *two* and ~~three~~ tail"
    configure(content)
    val editor = myFixture.editor
    editor.caretModel.caretsAndSelections = listOf(
      com.intellij.openapi.editor.CaretState(editor.offsetToLogicalPosition(content.indexOf("two")), null, null),
      com.intellij.openapi.editor.CaretState(editor.offsetToLogicalPosition(content.length), null, null),
    )
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertEquals("Only the element under a caret is revealed", listOf("**", "**", "~~", "~~"), concealed())
  }

  fun testConcealingDoesNotChangeTheDocument() {
    val content = "Some **bold** and [docs](https://example.org)<caret>"
    configure(content)
    assertFalse(concealed().isEmpty())
    assertEquals("Folding must not touch the document", content.removeSuffix("<caret>"), myFixture.editor.document.text)
  }

  fun testThematicBreaksUseEmptyFoldsAndOwnedRules() {
    val content = "---\n***\n___\ntail"
    configure("$content<caret>")

    assertEquals(3, thematicBreakHighlighters().size)
    assertEquals(listOf("---", "***", "___"), concealed())
    assertEquals(content, myFixture.editor.document.text)
  }

  fun testThematicBreakRevealsAndRestores() {
    val content = "---\ntail"
    configure("$content<caret>")
    val rule = thematicBreakHighlighters().single()

    moveCaretTo(rule.startOffset)
    assertEmpty(concealed())
    assertEmpty(thematicBreakHighlighters())
    assertEquals(content, myFixture.editor.document.text)

    moveCaretTo(content.length)
    assertEquals(listOf("---"), concealed())
    assertEquals(1, thematicBreakHighlighters().size)
  }

  fun testThematicBreakDoesNotConcealInlineCodeOnThePreviousLine() {
    val content = "`---`\n---\ntail"
    configure("$content<caret>")

    assertEquals(listOf("`---`", "---"), computeLivePreviewSpecs(myFixture.file).map {
      content.substring(it.range.startOffset, it.range.endOffset)
    })
    assertEquals(1, thematicBreakHighlighters().size)
    assertEquals(listOf("", "", ""), concealedLivePreviewRegions(myFixture.editor).map { it.placeholderText })
    assertEquals("---\n\ntail", visibleText())
    assertEquals(1, thematicBreakHighlighters().size)
    assertEquals(listOf("`", "`", "---"), concealed())
  }

  fun testSelectingEverythingRevealsEverything() {
    val content = "Some **bold** and `code`"
    configure(content)
    select(0, content.length)
    assertEmpty("Text cannot be selected while it is still hidden", concealed())
    assertEquals(content, myFixture.editor.selectionModel.selectedText)
  }

  fun testBackspacePastAnElementDeletesOneCharacter() {
    val content = "**bold** x"
    configure(content)
    moveCaretTo(content.length - 1)
    assertEquals(listOf("**", "**"), concealed())
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    assertEquals("**bold**x", myFixture.editor.document.text)
  }

  fun testBackspaceInsideAnElementDeletesOneCharacter() {
    val content = "**bold** x"
    configure(content)
    moveCaretTo("**bold".length)
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    assertEquals("**bol** x", myFixture.editor.document.text)
  }

  fun testDeleteAfterAnElementDeletesOneCharacter() {
    val content = "**bold** x"
    configure(content)
    moveCaretTo(content.indexOf(" x"))
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
    assertEquals("**bold**x", myFixture.editor.document.text)
  }

  fun testLivePreviewSettingReconcilesImmediately() {
    configure("Some **bold** text<caret>")
    assertFalse(concealed().isEmpty())
    settings.update { it.enableLivePreview = false }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertEmpty(concealed())

    settings.update { it.enableLivePreview = true }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertEquals(listOf("**", "**"), concealed())
  }

  fun testDiffEditorHidesNothing() {
    configure("Some **bold** text<caret>")
    val document = myFixture.editor.document
    val diffEditor = EditorFactory.getInstance().createEditor(document, project, myFixture.file.virtualFile, false, EditorKind.DIFF)
    try {
      val reconciler = MarkdownLivePreviewReconciler.getOrCreate(diffEditor)!!
      reconciler.publishSpecs(MarkdownLivePreviewSpecSet(document.modificationStamp, computeLivePreviewSpecs(myFixture.file)))
      assertEmpty("A diff editor must show the raw source", concealed(diffEditor))
    }
    finally {
      EditorFactory.getInstance().releaseEditor(diffEditor)
    }
  }

  /** Exercises the binary search the reconciler uses to find the elements around a caret. */
  fun testOnlyTheTouchedElementRevealsInAManyElementDocument() {
    val content = (1..40).joinToString(" ") { "**w$it**" } + " tail"
    configure(content)
    val target = content.indexOf("**w20**")
    moveCaretTo(target + 3)
    assertEquals("Every element but the one under the caret stays hidden", 40 * 2 - 2, concealed().size)
    moveCaretTo(content.length)
    assertEquals("With the caret past the end nothing is revealed", 40 * 2, concealed().size)
  }

  /**
   * Specs carry the document stamp they were computed from, and the reconciler declines them once the
   * document has moved on. The pass records that same stamp rather than the current one, so declined specs
   * do not look applied and the next pass recomputes them.
   */
  fun testSpecsFromAnOlderDocumentAreDeclined() {
    myFixture.configureByText("test.md", "Some **bold** text")
    val editor = myFixture.editor
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val reconciler = MarkdownLivePreviewReconciler.getOrCreate(editor)!!
    val elements = computeLivePreviewSpecs(myFixture.file)

    reconciler.publishSpecs(MarkdownLivePreviewSpecSet(editor.document.modificationStamp - 1, elements))
    assertEmpty("Specs computed from an older document must be declined", concealed())

    reconciler.publishSpecs(MarkdownLivePreviewSpecSet(editor.document.modificationStamp, elements))
    assertEquals(listOf("**", "**"), concealed())
  }

  /** Opens the file and lets the highlighting pass compute and publish the specs, as it does in an editor. */
  private fun configure(content: String) {
    myFixture.configureByText("test.md", content)
    myFixture.doHighlighting()
  }

  private fun moveCaretTo(offset: Int) {
    myFixture.editor.caretModel.moveToOffset(offset)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun select(start: Int, end: Int) {
    myFixture.editor.selectionModel.setSelection(start, end)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  /** The concealed markup, in document order. */
  private fun concealed(editor: Editor = myFixture.editor): List<String> {
    val text = editor.document.charsSequence
    return concealedLivePreviewRegions(editor).map { text.subSequence(it.startOffset, it.endOffset).toString() }
  }

  private fun concealedWithPlaceholders(editor: Editor = myFixture.editor): List<Pair<String, String>> {
    val text = editor.document.charsSequence
    return concealedLivePreviewRegions(editor).map {
      text.subSequence(it.startOffset, it.endOffset).toString() to it.placeholderText
    }
  }

  /** What the reader sees: every concealed range replaced by its fold placeholder. */
  private fun visibleText(): String {
    val editor = myFixture.editor
    val text = editor.document.charsSequence
    val result = StringBuilder()
    var offset = 0
    for (region in concealedLivePreviewRegions(editor)) {
      if (region.startOffset > offset) result.append(text, offset, region.startOffset)
      result.append(region.placeholderText)
      offset = maxOf(offset, region.endOffset)
    }
    result.append(text, offset, text.length)
    return result.toString()
  }

  private fun concealedLivePreviewRegions(editor: Editor): List<FoldRegion> =
    editor.foldingModel.allFoldRegions
      .filter { it.isValid && it.getUserData(MARKDOWN_LIVE_PREVIEW_REGION) == true }
      .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))

  private fun thematicBreakHighlighters(): List<RangeHighlighter> =
    myFixture.editor.markupModel.allHighlighters
      .filter { it.isValid && it.getUserData(MARKDOWN_LIVE_PREVIEW_HORIZONTAL_RULE) == true }
      .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))
}
