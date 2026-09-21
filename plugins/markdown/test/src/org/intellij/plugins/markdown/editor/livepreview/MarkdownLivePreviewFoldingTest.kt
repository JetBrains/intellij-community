// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.markdown.backend.editor.livepreview.computeLivePreviewSpecs
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewImageInlayRenderer
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewCheckboxInlayRenderer
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewReconciler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FoldingKeys
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.assertNothingLogged
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.EditorMouseFixture
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.JBUI
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class MarkdownLivePreviewFoldingTest : BasePlatformTestCase() {

  private val settings get() = MarkdownApplicationSettings.getInstance()

  override fun setUp() {
    super.setUp()
    val livePreview = settings.enableLivePreview
    Disposer.register(testRootDisposable) { settings.enableLivePreview = livePreview }
    settings.enableLivePreview = true
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorCreated(event: EditorFactoryEvent) {
        if (event.editor.editorKind == EditorKind.UNTYPED) event.editor.enableLivePreviewSupport()
      }
    }, testRootDisposable)
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

  fun testEveryOffsetOnTheFirstListLineRevealsItsMarker() = assertNothingLogged {
    for (prefix in listOf("  - ", "  - [ ] ", "  1. [x] ")) {
      val line = "${prefix}item text  "
      val content = "$line\n\ntail"
      configure("$content<caret>")
      for (offset in 0..line.length) {
        moveCaretTo(offset)
        assertEmpty("The caret at $offset must reveal $prefix", concealed())
        assertEmpty(checkboxInlays())
        moveCaretTo(content.length)
        assertEquals(1, concealed().size)
      }
    }
  }

  fun testContinuationAndNestedListLinesDoNotRevealTheirParent() {
    for (marker in listOf("-", "- [ ]")) {
      val content = "$marker parent\n  continuation\n  $marker child\n\ntail"
      configure("$content<caret>")
      moveCaretTo(content.indexOf("continuation"))
      assertEquals(listOf(marker, marker), concealed())
      moveCaretTo(content.indexOf("child") + 2)
      assertEquals(listOf(marker), concealed())
      moveCaretTo(content.indexOf("parent") + 2)
      assertEquals(listOf(marker), concealed())
    }
  }

  fun testSoftWrappedListTextRevealsItsMarker() {
    for (marker in listOf("-", "- [ ]")) {
      val content = "$marker a long item that wraps over several visual lines\n\ntail"
      configure("$content<caret>")
      EditorTestUtil.configureSoftWraps(myFixture.editor, 15)
      moveCaretTo(content.indexOf("visual"))
      assertTrue(myFixture.editor.caretModel.visualPosition.line > 0)
      assertEmpty(concealed())
      assertEmpty(checkboxInlays())
    }
  }

  fun testSelectionAndMultipleCaretsRevealListMarkers() {
    val content = "- first\n- [ ] second\n- third\n\ntail"
    configure("$content<caret>")
    select(content.indexOf("second"), content.indexOf("second") + 2)
    assertEquals(listOf("-", "-"), concealed())
    val editor = myFixture.editor
    editor.caretModel.addCaret(editor.offsetToVisualPosition(content.indexOf("first")))
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertEquals(listOf("-"), concealed())
  }

  fun testCheckboxesUseCompactWidthRegardlessOfSourcePrefixAndFontSize() {
    val content = "- [ ] first\n-\t[x] second\n1. [X] third\n\ntail"
    configure("$content<caret>")
    val editor = myFixture.editor as EditorImpl
    assertEquals(listOf(false, true, true), checkboxInlays().map { it.renderer.checked })
    for (fontSize in listOf(editor.colorsScheme.editorFontSize, editor.colorsScheme.editorFontSize + 4)) {
      editor.fontSize = fontSize
      moveCaretTo(content.length)
      val widths = checkboxInlays().map { it.widthInPixels }
      assertEquals("Every checkbox must have the same width", 1, widths.distinct().size)
      assertTrue("The checkbox must fit within one line height", widths.first() <= editor.lineHeight)
      for (word in listOf("first", "second", "third")) {
        moveCaretTo(content.length)
        val offset = content.indexOf(word)
        val position = editor.offsetToXY(offset)
        moveCaretTo(offset)
        assertTrue("The compact checkbox must use less space than the source prefix", position.x < editor.offsetToXY(offset).x)
      }
    }
  }

  fun testCheckboxClicksPreserveCaretsSelectionAndConcealment() {
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
    }, testRootDisposable)

    clickCheckbox(checkbox)
    myFixture.checkResult(content.replace("[ ]", "[x]"))
    assertTrue("The click must change the checkbox before highlighting", checkbox.renderer.checked)
    waitForDocument(content.replace("[ ]", "[x]"))
    assertTrue(checkboxInlays().single().renderer.checked)
    assertEquals(carets, editor.caretModel.allCarets.map { Triple(it.offset, it.selectionStart, it.selectionEnd) })
    assertEquals(scroll, editor.scrollingModel.verticalScrollOffset)
    assertSame(checkbox, checkboxInlays().single())
    assertEquals(0, disposals)

    clickCheckbox(checkbox)
    myFixture.checkResult(content)
    assertFalse("The second click must also change the checkbox immediately", checkbox.renderer.checked)
    waitForDocument(content)
    assertFalse(checkboxInlays().single().renderer.checked)
    assertEquals(carets, editor.caretModel.allCarets.map { Triple(it.offset, it.selectionStart, it.selectionEnd) })
    assertEquals(0, disposals)
  }

  fun testCheckboxToggleIsOneUndoableEdit() {
    val content = "- [X] task\n\ntail"
    configure("$content<caret>")
    clickCheckbox(checkboxInlays().single())
    waitForDocument(content.replace("[X]", "[ ]"))
    myFixture.performEditorAction(IdeActions.ACTION_UNDO)
    assertTrue("Undo must update the checkbox before highlighting", checkboxInlays().single().renderer.checked)
    waitForDocument(content)
    myFixture.performEditorAction(IdeActions.ACTION_REDO)
    assertFalse("Redo must update the checkbox before highlighting", checkboxInlays().single().renderer.checked)
    waitForDocument(content.replace("[X]", "[ ]"))
  }

  fun testRapidCheckboxClicksDoNotWaitForHighlighting() {
    val content = "- [ ] first\n- [ ] second\n\ntail"
    configure("$content<caret>")
    val (first, second) = checkboxInlays()
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

  fun testCheckboxSurvivesAnEditBeforeItsRange() {
    val content = "start\n\n- [ ] task\n\ntail"
    configure("$content<caret>")
    val inlay = checkboxInlays().single()
    val region = concealedLivePreviewRegions(myFixture.editor).single()
    val offset = inlay.offset

    writeCommandAction(project).run<Throwable> {
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

  fun testCheckboxReleaseOutsideCancelsTheClick() {
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

  fun testReadOnlyCheckboxClickDoesNotEditOrReveal() {
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

  fun testCheckboxClickUsesItsMovedRangeBeforeHighlighting() {
    val content = "- [ ] first\n- [ ] second\n\ntail"
    configure("$content<caret>")
    val checkbox = checkboxInlays().first()
    writeCommandAction(project).run<Throwable> {
      myFixture.editor.document.insertString(0, "- [ ] new\n")
    }
    clickCheckbox(checkbox)
    assertTrue(checkbox.renderer.checked)
    myFixture.checkResult("- [ ] new\n${content.replace("[ ] first", "[x] first")}")
  }

  fun testCheckboxSourceRemainsEditableAndPreviewCanBeDisabled() {
    val content = "- [ ] task\n\ntail"
    configure("$content<caret>")
    moveCaretTo(content.indexOf(']'))
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    myFixture.checkResult(content.replace("[ ]", "[]"))
    myFixture.doHighlighting()
    waitForCurrentSpecs()
    assertEmpty(checkboxInlays())

    configure("$content<caret>")
    settings.enableLivePreview = false
    myFixture.doHighlighting()
    waitForConcealed(emptyList())
    assertEmpty(checkboxInlays())
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
    waitForCurrentSpecs()
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
    myFixture.checkResult(content.removeSuffix("<caret>"))
  }

  fun testTableCellInlineMarkersAreConcealedAndRevealOnCaret() {
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

    writeCommandAction(project).run<Throwable> {
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

  fun testThematicBreaksUseEmptyFoldsAndOwnedRules() {
    val content = "---\n***\n___\ntail"
    configure("$content<caret>")

    assertEquals(3, thematicBreakHighlighters().size)
    assertEquals(listOf("---", "***", "___"), concealed())
    myFixture.checkResult(content)
  }

  fun testThematicBreakRevealsAndRestores() {
    val content = "---\ntail"
    configure("$content<caret>")
    val rule = thematicBreakHighlighters().single()

    moveCaretTo(rule.startOffset)
    assertEmpty(concealed())
    assertEmpty(thematicBreakHighlighters())
    myFixture.checkResult(content)
    assertEmpty(imageInlays())

    moveCaretTo(content.length)
    assertEquals(listOf("---"), concealed())
    assertEquals(1, thematicBreakHighlighters().size)
  }

  fun testDecorationKindChangesAtTheSameFoldRange() {
    configure("-----\n\ntail<caret>")
    val region = concealedLivePreviewRegions(myFixture.editor).single()
    val rule = thematicBreakHighlighters().single()

    writeCommandAction(project).run<Throwable> {
      myFixture.editor.document.replaceString(0, 5, "- [ ]")
    }
    myFixture.doHighlighting()
    waitForCurrentSpecs()
    val checkbox = checkboxInlays().single()
    assertSame(region, concealedLivePreviewRegions(myFixture.editor).single())
    assertFalse(rule.isValid)
    assertEmpty(thematicBreakHighlighters())

    writeCommandAction(project).run<Throwable> {
      myFixture.editor.document.replaceString(0, 5, "-----")
    }
    myFixture.doHighlighting()
    waitForCurrentSpecs()

    assertSame(region, concealedLivePreviewRegions(myFixture.editor).single())
    assertFalse(checkbox.isValid)
    assertEmpty(checkboxInlays())
    assertEquals(1, thematicBreakHighlighters().size)
  }

  fun testThematicBreakDoesNotConcealInlineCodeOnThePreviousLine() {
    val content = "`---`\n---\ntail"
    configure("$content<caret>")

    assertEquals(listOf("`---`", "---"), computeLivePreviewSpecs(myFixture.file, myFixture.editor).elements.map {
      content.substring(it.range.startOffset, it.range.endOffset)
    })
    assertEquals(1, thematicBreakHighlighters().size)
    assertEquals(listOf("", "", ""), concealedLivePreviewRegions(myFixture.editor).map { it.placeholderText })
    assertEquals("---\n\ntail", visibleText())
    assertEquals(1, thematicBreakHighlighters().size)
    assertEquals(listOf("`", "`", "---"), concealed())
  }

  fun testFrontMatterDelimitersAndThematicBreakUseRuleDecorations() {
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

  fun testStandaloneLocalImageRendersAndRevealsItsSource() {
    addPng(120, 60)
    val imageSource = "![alt](image.png)"
    val content = "$imageSource\n\ntail"
    configureProjectFile(content)

    val inlay = waitForImageInlay()
    assertEquals("The image sits below its line", imageSource.length, inlay.offset)
    assertEquals(listOf(imageSource to "alt"), concealedWithPlaceholders())
    assertEquals("The concealed line shows the alt text", "alt\n\ntail", visibleText())
    assertTrue(concealedLivePreviewRegions(myFixture.editor).all { FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND.isIn(it) })
    myFixture.checkResult(content)

    moveCaretTo(0)
    assertEmpty("The caret on the line reveals the source", concealed())
    assertSame("The image stays while the source is revealed", inlay, imageInlays().single())
    myFixture.checkResult(content)

    moveCaretTo(content.length)
    assertEquals(listOf(imageSource), concealed())
    assertSame(inlay, imageInlays().single())
  }

  fun testInlineImageConcealsOnlyItsElement() {
    addPng(120, 60)
    val imageSource = "![alt](image.png)"
    val content = "Text $imageSource here\n\ntail"
    configureProjectFile(content)

    val inlay = waitForImageInlay()
    assertEquals(content.indexOf('\n'), inlay.offset)
    assertEquals(listOf(imageSource), concealed())
    assertEquals("Text alt here\n\ntail", visibleText())

    moveCaretTo(2)
    assertEquals("The caret on the text next to the image keeps it concealed", listOf(imageSource), concealed())

    moveCaretTo(content.indexOf("alt"))
    assertEmpty("The caret on the element reveals it", concealed())
    assertSame(inlay, imageInlays().single())
  }

  fun testImagesInListItemBlockQuoteAndHeaderRender() {
    addPng(120, 60)
    val content = "- ![a](image.png)\n\n> ![b](image.png)\n\n# ![c](image.png)\n\ntail"
    configureProjectFile(content)

    waitForImageInlays(3)
    assertEquals(listOf("![a](image.png)", "![b](image.png)", "![c](image.png)"), concealed().filter { it.startsWith("![") })
    assertEquals("• a\n\n> b\n\n# c\n\ntail", visibleText())
    assertEquals(
      listOf(0, 2, 4),
      imageInlays().map { myFixture.editor.document.getLineNumber(it.offset) }.sorted(),
    )
  }

  fun testImageWithoutAltTextShowsTheGenericPlaceholder() {
    addPng(120, 60)
    configureProjectFile("![](image.png)\n\ntail")

    waitForImageInlay()
    assertEquals("${MarkdownBundle.message("markdown.live.preview.image.placeholder")}\n\ntail", visibleText())
  }

  fun testTwoImagesOnOneLineConcealEachElementAndProduceTwoInlays() {
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
    assertEquals("The caret in the first image reveals that image alone", listOf(second), concealed())
    assertEquals(2, imageInlays().size)
  }

  fun testImagesBetweenHeadersBothRender() {
    addPng(120, 60)
    val content = "# Markdown WYSIWYG Demo\n\n![logo](image.png)\n\n![logo](image.png)\n\n# Markdown WYSIWYG Demo"
    configureProjectFile(content)

    assertEquals(2, computeLivePreviewSpecs(myFixture.file, myFixture.editor).elements.filterIsInstance<MarkdownLivePreviewSpec.Image>().size)
    waitForImageInlays(2)
  }

  fun testAdjacentImagesBothRender() {
    addPng(120, 60)
    configureProjectFile("![logo](image.png)\n![logo](image.png)\n\ntail")

    waitForImageInlays(2)
  }

  fun testMovingCaretBetweenImagesKeepsBothInlays() {
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

  fun testSelectionOverImageYieldsMarkdownSource() {
    addPng(80, 40)
    val imageSource = "![alt](image.png)"
    val content = "$imageSource\n\ntail"
    configureProjectFile(content)
    val inlay = waitForImageInlay()

    select(0, imageSource.length)

    assertEmpty(concealed())
    assertEquals(imageSource, myFixture.editor.selectionModel.selectedText)
    assertSame(inlay, imageInlays().single())
  }

  fun testSvgImageUsesItsIntrinsicSize() {
    addBinaryFile(
      "docs/image.svg",
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"90\" height=\"45\"><rect width=\"90\" height=\"45\"/></svg>".toByteArray(),
    )
    configureProjectFile("![alt](image.svg)\n\ntail")

    val source = waitForImageInlay().renderer.source
    assertEquals(90, source.width)
    assertEquals(45, source.height)
  }

  fun testCachedAttachmentImageRendersFromProjectRoot() {
    addBinaryFile(".attachments/image.png", pngBytes(70, 35))
    configureProjectFile("![alt](/.attachments/image.png)\n\ntail")

    waitForImageInlay()
  }

  fun testEncodedLocalImagePathUsesSharedResolver() {
    addBinaryFile("docs/image with space.png", pngBytes(70, 35))
    configureProjectFile("![alt](image%20with%20space.png)\n\ntail")

    waitForImageInlay()
  }

  fun testSiblingPrefixPathOutsideProjectRestoresItsSource() {
    // The light project root is `temp:///src`, so `temp:///src-other` shares its name prefix and lies outside it.
    // The fixture cleans `temp:///src` only, so the test deletes the sibling itself.
    val image = addBinaryFile("../src-other/image.png", pngBytes(60, 30))
    Disposer.register(testRootDisposable) { VfsTestUtil.deleteFile(image.parent) }
    configureProjectFile("![alt](../../src-other/image.png)\n\ntail")

    assertEquals(1, computeLivePreviewSpecs(myFixture.file, myFixture.editor).elements.filterIsInstance<MarkdownLivePreviewSpec.Image>().size)
    waitForNoImageInlay()
  }

  fun testFileUriOutsideProjectRestoresItsSource() {
    val image = myFixture.addFileToProject("project-other/outside.png", "").virtualFile
    ApplicationManager.getApplication().runWriteAction { image.setBinaryContent(pngBytes(60, 30)) }
    val source = myFixture.addFileToProject("project/docs/test.md", "![alt](<${image.url}>)\n\ntail").virtualFile
    PsiTestUtil.addContentRoot(myFixture.module, source.parent!!.parent!!)
    myFixture.configureFromExistingVirtualFile(source)
    myFixture.editor.caretModel.moveToOffset(source.contentsToByteArray().size)
    myFixture.doHighlighting()
    waitForNoImageInlay()
  }

  fun testBrokenImageAndMissingImageStayRaw() {
    addBinaryFile("docs/broken.png", "not an image".toByteArray())
    configureProjectFile("![broken](broken.png)\n\n![missing](missing.png)\n\ntail")

    waitForNoImageInlay()

    assertEmpty(concealed())
    myFixture.checkResult("![broken](broken.png)\n\n![missing](missing.png)\n\ntail")
  }

  fun testImageRefreshesAfterVfsChange() {
    val image = addPng(100, 40)
    configureProjectFile("![alt](image.png)\n\ntail")
    val inlay = waitForImageInlay()
    val initialHeight = inlay.heightInPixels
    val initialStamp = image.modificationStamp

    ApplicationManager.getApplication().runWriteAction { image.setBinaryContent(pngBytes(100, 90)) }
    assertTrue("The image modification stamp must change", initialStamp != image.modificationStamp)

    PlatformTestUtil.waitWithEventsDispatching(
      "Image inlay was not refreshed",
      { imageInlays().single().heightInPixels > initialHeight },
      10,
    )
    val refreshed = imageInlays().single()
    assertNotSame("A refresh replaces the inlay, because its renderer is immutable", inlay, refreshed)
    assertFalse(inlay.isValid)
    assertEquals(image.modificationStamp, refreshed.renderer.source.stamp)
    assertEquals(90, refreshed.renderer.source.height)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertEquals("The source stays concealed across the refresh", listOf("![alt](image.png)"), concealed())
  }

  fun testTypingAfterImageKeepsTheImageInlay() {
    addPng(120, 60)
    configureProjectFile("![alt](image.png)\n\ntail")
    val inlay = waitForImageInlay()
    val renderer = inlay.renderer

    myFixture.type("x")
    myFixture.doHighlighting()
    waitForCurrentSpecs()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertSame("A keystroke outside the image must not recreate its inlay", inlay, imageInlays().single())
    assertSame("A keystroke outside the image must not replace its renderer", renderer, inlay.renderer)
  }

  fun testAddingAnImageKeepsTheExistingInlay() {
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
    assertSame("Adding an image must not replace the renderer of an existing image", renderer, inlay.renderer)
  }

  fun testImageOverByteLimitIsRejectedAfterVfsRefresh() {
    val image = addPng(2, 2)
    configureProjectFile("![alt](image.png)\n\ntail")
    waitForImageInlay()
    Registry.get("markdown.live.preview.image.max.bytes").setValue(1, testRootDisposable)

    ApplicationManager.getApplication().runWriteAction { image.setBinaryContent(pngBytes(3, 3)) }

    waitForNoImageInlay()
    assertEmpty(concealed())
  }

  fun testImageOverPixelLimitIsRejectedAfterVfsRefresh() {
    val image = addPng(2, 2)
    configureProjectFile("![alt](image.png)\n\ntail")
    waitForImageInlay()
    Registry.get("markdown.live.preview.image.max.pixels").setValue(1, testRootDisposable)

    ApplicationManager.getApplication().runWriteAction { image.setBinaryContent(pngBytes(3, 3)) }

    waitForNoImageInlay()
    assertEmpty(concealed())
  }

  fun testResizingEditorDoesNotRecreateImageInlay() {
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

  fun testClickingImagePreservesViewportPosition() {
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

  fun testMovingCaretAcrossSmallImageDoesNotJumpViewport() {
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

  fun testMovingPastLargeImageUsesMinimumScroll() {
    addPng(100, 1_000)
    val image = "![logo](image.png)"
    val content = (1..6).joinToString("\n") { "before$it" } + "\n\n$image\n\n" +
                  (1..12).joinToString("\n") { "after$it" }
    configureProjectFile(content)
    val editor = myFixture.editor
    val inlay = waitForImageInlay()
    assertEquals("A tall image is capped at 20 lines", 20 * editor.lineHeight + 2 * JBUI.scale(2), inlay.heightInPixels)
    // The platform scrolls by the minimum only when the caret line and one line of context on each side fit into
    // the viewport. The context line above the caret carries the image, so the viewport holds 24 lines.
    EditorTestUtil.setEditorVisibleSizeInPixels(editor, 300, editor.lineHeight * 24)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val imageLine = editor.document.getLineNumber(content.indexOf(image))
    moveCaretTo(editor.document.getLineStartOffset(imageLine))
    assertEmpty(concealed())
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
    myFixture.checkResult("**bold**x")
  }

  fun testBackspaceInsideAnElementDeletesOneCharacter() {
    val content = "**bold** x"
    configure(content)
    moveCaretTo("**bold".length)
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    myFixture.checkResult("**bol** x")
  }

  fun testBackspaceAfterAutolinkDeletesOneCharacter() {
    assertBackspaceAfterElement("<https://example.org>")
  }

  fun testBackspaceAfterEmphasisDeletesOneCharacter() {
    assertBackspaceAfterElement("*italic*")
  }

  fun testBackspaceAfterStrongEmphasisDeletesOneCharacter() {
    assertBackspaceAfterElement("**bold**")
  }

  fun testBackspaceAfterCodeSpanDeletesOneCharacter() {
    assertBackspaceAfterElement("`code`")
  }

  fun testBackspaceAfterInlineLinkDeletesOneCharacter() {
    assertBackspaceAfterElement("[link](https://example.org)")
  }

  fun testBackspaceAfterStrikethroughDeletesOneCharacter() {
    assertBackspaceAfterElement("~~deleted~~")
  }

  fun testBackspaceAfterThematicBreakDeletesOneCharacter() {
    assertBackspaceAfterElement("---")
  }

  fun testBackspaceAfterImageDoesNotDeleteTheImage() {
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
    assertEquals("The caret at the end of the image line reveals the source", listOf("`", "`"), concealed())
    assertSame(inlay, imageInlays().single())
  }

  fun testDeleteAfterAnElementDeletesOneCharacter() {
    val content = "**bold** x"
    configure(content)
    moveCaretTo(content.indexOf(" x"))
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
    myFixture.checkResult("**bold**x")
  }

  fun testLivePreviewSettingRepublishesSpecs() {
    configure("Some **bold** text<caret>")
    assertEquals(listOf("**", "**"), concealed())

    settings.enableLivePreview = false
    myFixture.doHighlighting()
    waitForConcealed(emptyList())

    settings.enableLivePreview = true
    myFixture.doHighlighting()
    waitForConcealed(listOf("**", "**"))
  }

  fun testClearingSpecsAndDisablingPreviewRemoveAllDecorations() {
    configureAllDecorations()
    val editor = myFixture.editor
    val reconciler = MarkdownLivePreviewReconciler.getExisting(editor)!!
    val specs = computeLivePreviewSpecs(myFixture.file, editor)
    val folds = concealedLivePreviewRegions(editor)
    val checkbox = checkboxInlays().single()
    val rule = thematicBreakHighlighters().single()
    val image = imageInlays().single()

    reconciler.publishSpecs(null)

    assertEmpty(concealed())
    assertEmpty(checkboxInlays())
    assertEmpty(thematicBreakHighlighters())
    assertEmpty(imageInlays())
    assertTrue(folds.all { !it.isValid })
    assertFalse(checkbox.isValid)
    assertFalse(rule.isValid)
    assertFalse(image.isValid)

    reconciler.publishSpecs(specs)
    assertEquals(3, concealed().size)
    assertEquals(1, checkboxInlays().size)
    assertEquals(1, thematicBreakHighlighters().size)
    assertEquals(1, imageInlays().size)

    settings.enableLivePreview = false
    reconciler.reconcileNow()

    assertEmpty(concealed())
    assertEmpty(checkboxInlays())
    assertEmpty(thematicBreakHighlighters())
    assertEmpty(imageInlays())
  }

  fun testDisposalRemovesAllDecorationsAndIgnoresLaterUpdates() {
    configureAllDecorations()
    val editor = myFixture.editor
    val reconciler = MarkdownLivePreviewReconciler.getExisting(editor)!!
    val specs = computeLivePreviewSpecs(myFixture.file, editor)

    Disposer.dispose(reconciler)
    reconciler.publishSpecs(specs)
    reconciler.reconcileNow()
    moveCaretTo(0)

    assertFalse(reconciler.hasCurrentSpecs())
    assertEmpty(concealed())
    assertEmpty(checkboxInlays())
    assertEmpty(thematicBreakHighlighters())
    assertEmpty(imageInlays())
  }

  fun testDiffEditorHidesNothing() {
    configure("Some **bold** text<caret>")
    val document = myFixture.editor.document
    val diffEditor = EditorFactory.getInstance().createEditor(document, project, myFixture.file.virtualFile, false, EditorKind.DIFF)
    try {
      val reconciler = MarkdownLivePreviewReconciler.getOrCreate(diffEditor)!!
      reconciler.publishSpecs(computeLivePreviewSpecs(myFixture.file, myFixture.editor))
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

  fun testRevealingElementsWithDifferentFoldCounts() {
    val content = "![missing](missing.png)\n\n**bold**\n\n- [ ] task\n\n-----\n\ntail"
    configureProjectFile(content)
    assertEquals(listOf("**", "**", "- [ ]", "-----"), concealed())

    moveCaretTo(content.indexOf("bold") + 1)
    assertEquals(listOf("- [ ]", "-----"), concealed())
    assertEquals(1, checkboxInlays().size)

    moveCaretTo(content.indexOf("task") + 1)
    assertEquals(listOf("**", "**", "-----"), concealed())
    assertEmpty(checkboxInlays())

    moveCaretTo(content.indexOf("-----") + 1)
    assertEquals(listOf("**", "**", "- [ ]"), concealed())
    assertEmpty(thematicBreakHighlighters())

    moveCaretTo(content.indexOf("missing") + 1)
    assertEquals(listOf("**", "**", "- [ ]", "-----"), concealed())
    select(content.indexOf("bold"), content.indexOf("task") + 1)
    assertEquals(listOf("-----"), concealed())
  }

  fun testSpecsFromAnOlderDocumentAreDeclined() {
    myFixture.configureByText("test.md", "Some **bold** text")
    val editor = myFixture.editor
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val reconciler = MarkdownLivePreviewReconciler.getOrCreate(editor)!!
    val elements = computeLivePreviewSpecs(myFixture.file, editor).elements
    val oldVersion = MarkdownLivePreviewDocumentVersion.capture(editor.document, project)
    (editor.document as DocumentEx).setModificationStamp(editor.document.modificationStamp + 1)

    reconciler.publishSpecs(
      MarkdownLivePreviewSpecSet(oldVersion, elements)
    )
    assertEmpty("Specs computed from an older document must be declined", concealed())

    reconciler.publishSpecs(
      MarkdownLivePreviewSpecSet(MarkdownLivePreviewDocumentVersion.capture(editor.document, project), elements)
    )
    assertEquals(listOf("**", "**"), concealed())
  }

  fun testSpecsPublishedDuringBulkUpdateAreAppliedAfterItFinishes() {
    myFixture.configureByText("test.md", "Some **bold** text<caret>")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val editor = myFixture.editor
    val reconciler = MarkdownLivePreviewReconciler.getOrCreate(editor)!!
    val specSet = computeLivePreviewSpecs(myFixture.file, editor)

    DocumentUtil.executeInBulk(editor.document, true) {
      reconciler.publishSpecs(specSet)
      assertEmpty("A bulk update must delay reconciliation", concealed())
    }

    PlatformTestUtil.waitWithEventsDispatching(
      "The pending live-preview specs were not applied",
      { concealed() == listOf("**", "**") },
      10,
    )
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
    configureProjectFile("- [ ] task\n\n-----\n\n![alt](image.png)\n\ntail")
    waitForImageInlay()
    assertEquals(3, concealed().size)
    assertEquals(1, checkboxInlays().size)
    assertEquals(1, thematicBreakHighlighters().size)
  }

  private fun checkboxInlays(): List<Inlay<out MarkdownLivePreviewCheckboxInlayRenderer>> =
    myFixture.editor.inlayModel.getInlineElementsInRange(
      0, myFixture.editor.document.textLength, MarkdownLivePreviewCheckboxInlayRenderer::class.java,
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

  /** Waits until [count] image inlays exist. Every inlay has a loaded source by construction. */
  private fun waitForImageInlays(count: Int) {
    PlatformTestUtil.waitWithEventsDispatching(
      "Image inlays were not added",
      { imageInlays().size == count },
      10,
    )
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
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
      .filter { it.isValid && it.shouldNeverExpand() }
      .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))

  private fun thematicBreakHighlighters(): List<RangeHighlighter> =
    myFixture.editor.markupModel.allHighlighters
      .filter { it.isValid && it.customRenderer != null }
      .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))

  private fun imageInlays(): List<Inlay<out MarkdownLivePreviewImageInlayRenderer>> {
    val editor = myFixture.editor
    return editor.inlayModel
      .getBlockElementsInRange(0, editor.document.textLength, MarkdownLivePreviewImageInlayRenderer::class.java)
      .filter { it.isValid }
  }
}
