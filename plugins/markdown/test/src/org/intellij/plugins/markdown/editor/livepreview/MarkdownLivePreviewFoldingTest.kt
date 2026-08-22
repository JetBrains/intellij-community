// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.markdown.frontend.editor.livepreview.MARKDOWN_LIVE_PREVIEW_REGION
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewReconciler
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.FoldRegion
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
  }

  fun testInlineLinkShowsOnlyItsTitle() {
    configure("Read [the docs](https://example.org) today<caret>")
    assertEquals("Read the docs today", visibleText())
  }

  fun testAutolinksShowOnlyTheirTarget() {
    configure("See <https://example.org> or mail <team@example.org> here<caret>\n\ntail")
    assertEquals("See https://example.org or mail team@example.org here\n\ntail", visibleText())
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

  fun testSelectingEverythingRevealsEverything() {
    val content = "Some **bold** and `code`"
    configure(content)
    select(0, content.length)
    assertEmpty("Text cannot be selected while it is still hidden", concealed())
    assertEquals(content, myFixture.editor.selectionModel.selectedText)
  }

  /**
   * `BackspaceAction` looks for a collapsed region one character behind the caret and deletes the whole of
   * it, and that lookup counts a region ending exactly there as a hit - so a caret one character past
   * `**bold**` would take both asterisks out in a single keystroke. It never gets the chance: the action
   * first moves the caret one column left, onto the element's end offset, and the reveal that runs
   * synchronously in the caret listener removes the region before the action goes looking for it. This is
   * what makes a dedicated backspace handler unnecessary, so the test is here to keep it that way.
   */
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

  fun testDisablingLivePreviewRemovesEveryRegion() {
    configure("Some **bold** text<caret>")
    assertFalse(concealed().isEmpty())
    settings.enableLivePreview = false
    moveCaretTo(0)
    assertEmpty(concealed())
  }

  fun testDiffEditorHidesNothing() {
    configure("Some **bold** text<caret>")
    val document = myFixture.editor.document
    val diffEditor = EditorFactory.getInstance().createEditor(document, project, myFixture.file.virtualFile, false, EditorKind.DIFF)
    try {
      val reconciler = MarkdownLivePreviewReconciler.getOrCreate(diffEditor)!!
      reconciler.publishSpecs(MarkdownConcealSpecSet(document.modificationStamp, computeConcealElements(myFixture.file)))
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
    val elements = computeConcealElements(myFixture.file)

    reconciler.publishSpecs(MarkdownConcealSpecSet(editor.document.modificationStamp - 1, elements))
    assertEmpty("Specs computed from an older document must be declined", concealed())

    reconciler.publishSpecs(MarkdownConcealSpecSet(editor.document.modificationStamp, elements))
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

  /** What the reader sees: the document with every concealed region left out. */
  private fun visibleText(): String {
    val editor = myFixture.editor
    val text = editor.document.charsSequence
    val result = StringBuilder()
    var offset = 0
    for (region in concealedLivePreviewRegions(editor)) {
      if (region.startOffset > offset) result.append(text, offset, region.startOffset)
      offset = maxOf(offset, region.endOffset)
    }
    result.append(text, offset, text.length)
    return result.toString()
  }

  private fun concealedLivePreviewRegions(editor: Editor): List<FoldRegion> =
    editor.foldingModel.allFoldRegions
      .filter { it.isValid && it.getUserData(MARKDOWN_LIVE_PREVIEW_REGION) == true }
      .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))
}
