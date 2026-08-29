// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.livepreview

import com.intellij.codeInsight.documentation.render.DocRenderer
import com.intellij.markdown.frontend.editor.livepreview.MarkdownLivePreviewReconciler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FoldingKeys
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.EditorMouseFixture
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

@RunWith(JUnit4::class)
class MarkdownLivePreviewFoldingTest : BasePlatformTestCase() {

  private val settings get() = MarkdownSettings.getInstance(project)

  @get:Rule
  val temporaryDirectory = TemporaryDirectory()

  override fun setUp() {
    super.setUp()
    val root = temporaryDirectory.createDir()
    val rootFile = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root))
    PsiTestUtil.addContentRoot(myFixture.module, rootFile)
    val livePreview = settings.enableLivePreview
    Disposer.register(testRootDisposable) { settings.enableLivePreview = livePreview }
    settings.enableLivePreview = true
  }

  @Test
  fun testMarkupIsHiddenWhileTheCaretIsElsewhere() {
    configure("Some **bold**, *italic* and `code` here<caret>")
    assertEquals("Some bold, italic and code here", visibleText())
    assertTrue(concealedLivePreviewRegions(myFixture.editor).none { FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND.isIn(it) })
  }

  @Test
  fun testInlineLinkShowsOnlyItsTitle() {
    configure("Read [the docs](https://example.org) today<caret>")
    assertEquals("Read the docs today", visibleText())
  }

  @Test
  fun testAutolinksShowOnlyTheirTarget() {
    configure("See <https://example.org> or mail <team@example.org> here<caret>\n\ntail")
    assertEquals("See https://example.org or mail team@example.org here\n\ntail", visibleText())
  }

  @Test
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

  @Test
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
  @Test
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

  @Test
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

  @Test
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

  @Test
  fun testListMarkerConcealmentDoesNotMoveItemText() {
    val content = "- bullet\n1. ordered\n\ntail"
    configure("$content<caret>")
    val offsets = listOf(content.indexOf("bullet"), content.indexOf("ordered"))
    val concealedPositions = offsets.map { myFixture.editor.offsetToXY(it) }

    moveCaretTo(0)

    assertEmpty(concealed())
    assertEquals(concealedPositions, offsets.map { myFixture.editor.offsetToXY(it) })
  }

  @Test
  fun testNestedElementRevealsItsAncestor() {
    val content = "**bold *and italic* here**"
    configure(content)
    moveCaretTo(content.indexOf("and"))
    assertEmpty("A caret in the inner element must reveal the outer one too", concealed())
  }

  @Test
  fun testSelectionOverAnElementRevealsIt() {
    val content = "a **bold** b **more** c"
    configure(content)
    select(0, content.indexOf(" b"))
    assertEquals("Only the selected element is revealed", listOf("**", "**"), concealed())
  }

  @Test
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

  @Test
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

  @Test
  fun testConcealingDoesNotChangeTheDocument() {
    val content = "Some **bold** and [docs](https://example.org)<caret>"
    configure(content)
    assertFalse(concealed().isEmpty())
    assertEquals("Folding must not touch the document", content.removeSuffix("<caret>"), myFixture.editor.document.text)
  }

  @Test
  fun testThematicBreaksUseEmptyFoldsAndOwnedRules() {
    val content = "---\n***\n___\ntail"
    configure("$content<caret>")

    assertEquals(3, thematicBreakHighlighters().size)
    assertEquals(listOf("---", "***", "___"), concealed())
    assertEquals(content, myFixture.editor.document.text)
  }

  @Test
  fun testThematicBreakRevealsAndRestores() {
    val content = "---\ntail"
    configure("$content<caret>")
    val rule = thematicBreakHighlighters().single()

    moveCaretTo(rule.startOffset)
    assertEmpty(concealed())
    assertEmpty(thematicBreakHighlighters())
    assertEquals(content, myFixture.editor.document.text)
    assertEmpty(imageRegions())

    moveCaretTo(content.length)
    assertEquals(listOf("---"), concealed())
    assertEquals(1, thematicBreakHighlighters().size)
  }

  @Test
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

  @Test
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
    assertEquals(content, myFixture.editor.document.text)

    val bodyRule = thematicBreakHighlighters().last()
    moveCaretTo(bodyRule.startOffset)
    assertEquals(listOf("---", "---"), concealed())
    assertEquals(2, thematicBreakHighlighters().size)

    moveCaretTo(content.length)
    assertEquals(listOf("---", "---", "---"), concealed())
    assertEquals(3, thematicBreakHighlighters().size)
  }

  @Test
  fun testStandaloneLocalImageRendersAndRevealsItsSource() {
    addPng(120, 60)
    val content = "![alt](image.png)\n\ntail"
    configureProjectFile(content)

    val region = waitForImageRenderer()
    assertEquals("![alt](image.png)", content.substring(region.startOffset, region.endOffset))
    assertTrue(region.renderer is DocRenderer)
    assertEquals(content, myFixture.editor.document.text)

    moveCaretTo(0)
    assertEmpty(imageRegions())
    assertEquals(content, myFixture.editor.document.text)

    moveCaretTo(content.length)
    waitForImageRenderer()
  }

  @Test
  fun testImagesBetweenHeadersBothRender() {
    addPng(120, 60)
    val content = "# Markdown WYSIWYG Demo\n\n![logo](image.png)\n\n![logo](image.png)\n\n# Markdown WYSIWYG Demo"
    configureProjectFile(content)

    assertEquals(2, computeLivePreviewSpecs(myFixture.file).filterIsInstance<MarkdownLivePreviewSpec.Image>().size)
    waitForImageRegions(2)
  }

  @Test
  fun testAdjacentImagesBothRender() {
    addPng(120, 60)
    configureProjectFile("![logo](image.png)\n![logo](image.png)\n\ntail")

    waitForImageRegions(2)
  }

  @Test
  fun testMovingCaretBetweenImagesDoesNotUseDisposedFoldRegion() {
    addPng(120, 60)
    val first = "![first](image.png)"
    val second = "![second](image.png)"
    val content = "$first\n\n$second\n\ntail"
    configureProjectFile(content)
    waitForImageRegions(2)

    moveCaretTo(first.length)
    assertEquals(1, imageRegions().size)

    moveCaretTo(content.indexOf(second) + second.length)
    assertEquals(1, imageRegions().size)
  }

  @Test
  fun testSelectionOverImageYieldsMarkdownSource() {
    addPng(80, 40)
    val imageSource = "![alt](image.png)"
    val content = "$imageSource\n\ntail"
    configureProjectFile(content)
    waitForImageRenderer()

    select(0, imageSource.length)

    assertEmpty(imageRegions())
    assertEquals(imageSource, myFixture.editor.selectionModel.selectedText)
  }

  @Test
  fun testSvgImageUsesItsIntrinsicSize() {
    addBinaryFile(
      "docs/image.svg",
      "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"90\" height=\"45\"><rect width=\"90\" height=\"45\"/></svg>".toByteArray(),
    )
    configureProjectFile("![alt](image.svg)\n\ntail")
    assertTrue(waitForImageRenderer().renderer is DocRenderer)
  }

  @Test
  fun testCachedAttachmentImageRendersFromProjectRoot() {
    addBinaryFile(".attachments/image.png", pngBytes(70, 35))
    configureProjectFile("![alt](/.attachments/image.png)\n\ntail")

    assertTrue(waitForImageRenderer().renderer is DocRenderer)
  }

  @Test
  fun testEncodedLocalImagePathUsesSharedResolver() {
    addBinaryFile("docs/image with space.png", pngBytes(70, 35))
    configureProjectFile("![alt](image%20with%20space.png)\n\ntail")

    assertTrue(waitForImageRenderer().renderer is DocRenderer)
  }

  @Test
  fun testSiblingPrefixPathOutsideProjectRestoresItsSource() {
    val container = Files.createTempDirectory("markdown-live-preview-boundary")
    Disposer.register(testRootDisposable) { NioFiles.deleteRecursively(container) }
    val projectRoot = Files.createDirectories(container.resolve("project"))
    val sourcePath = Files.createDirectories(projectRoot.resolve("docs")).resolve("test.md")
    val siblingRoot = Files.createDirectories(container.resolve("project-other"))
    Files.write(siblingRoot.resolve("image.png"), pngBytes(60, 30))
    val content = "![alt](../../project-other/image.png)\n\ntail"
    Files.writeString(sourcePath, content)
    val localFileSystem = LocalFileSystem.getInstance()
    val root = requireNotNull(localFileSystem.refreshAndFindFileByNioFile(projectRoot))
    PsiTestUtil.addContentRoot(myFixture.module, root)
    val source = requireNotNull(localFileSystem.refreshAndFindFileByNioFile(sourcePath))
    myFixture.configureFromExistingVirtualFile(source)
    myFixture.editor.caretModel.moveToOffset(content.length)
    myFixture.doHighlighting()

    assertEquals(1, computeLivePreviewSpecs(myFixture.file).filterIsInstance<MarkdownLivePreviewSpec.Image>().size)
    PlatformTestUtil.waitWithEventsDispatching("Sibling image fold was not removed", { imageRegions().isEmpty() }, 10)
  }

  @Test
  fun testFileUriOutsideProjectRestoresItsSource() {
    val image = Files.createTempFile("markdown-live-preview", ".png")
    try {
      Files.write(image, pngBytes(60, 30))
      configureProjectFile("![alt](<${image.toUri()}>)\n\ntail")

      PlatformTestUtil.waitWithEventsDispatching("Outside image fold was not removed", { imageRegions().isEmpty() }, 10)
    }
    finally {
      Files.deleteIfExists(image)
    }
  }

  @Test
  fun testBrokenImageAndMissingImageStayRaw() {
    addBinaryFile("docs/broken.png", "not an image".toByteArray())
    configureProjectFile("![broken](broken.png)\n\n![missing](missing.png)\n\ntail")

    waitForNoImageRegion()

    assertEquals("![broken](broken.png)\n\n![missing](missing.png)\n\ntail", myFixture.editor.document.text)
  }

  @Test
  fun testImageRefreshesAfterVfsChange() {
    val image = addPng(100, 40)
    configureProjectFile("![alt](image.png)\n\ntail")
    val region = waitForImageRenderer()
    assertTrue(region.renderer is DocRenderer)

    ApplicationManager.getApplication().runWriteAction { image.setBinaryContent(pngBytes(100, 90)) }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertSame(region, imageRegions().single())
    assertTrue(imageRegions().single().renderer is DocRenderer)
  }

  @Test
  fun testImageOverByteLimitIsRejectedAfterVfsRefresh() {
    val image = addPng(2, 2)
    configureProjectFile("![alt](image.png)\n\ntail")
    waitForImageRenderer()
    Registry.get("markdown.live.preview.image.max.bytes").setValue(1, testRootDisposable)

    ApplicationManager.getApplication().runWriteAction { image.setBinaryContent(pngBytes(3, 3)) }

    waitForNoImageRegion()
  }

  @Test
  fun testImageOverPixelLimitIsRejectedAfterVfsRefresh() {
    val image = addPng(2, 2)
    configureProjectFile("![alt](image.png)\n\ntail")
    waitForImageRenderer()
    Registry.get("markdown.live.preview.image.max.pixels").setValue(1, testRootDisposable)

    ApplicationManager.getApplication().runWriteAction { image.setBinaryContent(pngBytes(3, 3)) }

    waitForNoImageRegion()
  }

  @Test
  fun testResizingEditorDoesNotRecreateImageFold() {
    addPng(100, 40)
    configureProjectFile("![alt](image.png)\n\ntail")
    val region = waitForImageRenderer()
    val renderer = region.renderer

    EditorTestUtil.setEditorVisibleSize(myFixture.editor, 40, 20)
    EditorTestUtil.setEditorVisibleSize(myFixture.editor, 80, 20)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertSame(region, imageRegions().single())
    assertSame(renderer, imageRegions().single().renderer)
  }

  @Test
  fun testClickingImagePreservesViewportPosition() {
    addPng(240, 120)
    val content = (1..4).joinToString("\n\n") { "![logo](image.png)" } + "\n\n- tail"
    configureProjectFile(content)
    waitForImageRegions(4)
    val editor = myFixture.editor
    EditorTestUtil.setEditorVisibleSize(editor, 80, 12)
    val target = imageRegions().sortedBy { it.startOffset }[1]
    val targetLine = editor.offsetToVisualPosition(target.startOffset).line
    editor.scrollingModel.scrollVertically((editor.visualLineToY(targetLine) - editor.lineHeight).coerceAtLeast(0))
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    val verticalOffset = editor.scrollingModel.verticalScrollOffset

    EditorMouseFixture(editor as EditorImpl).clickAt(targetLine, 1)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertEquals(verticalOffset, editor.scrollingModel.verticalScrollOffset)
  }

  @Test
  fun testMovingCaretAcrossSmallImageDoesNotJumpViewport() {
    addPng(40, 20)
    val image = "![logo](image.png)"
    val content = (1..8).joinToString("\n") { "before$it" } + "\n\n$image\n\n" +
                  (1..8).joinToString("\n") { "after$it" }
    configureProjectFile(content)
    val editor = myFixture.editor
    waitForImageRenderer()
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

  @Test
  fun testMovingPastLargeImageUsesMinimumScroll() {
    addPng(100, 1_000)
    val image = "![logo](image.png)"
    val content = (1..6).joinToString("\n") { "before$it" } + "\n\n$image\n\n" +
                  (1..12).joinToString("\n") { "after$it" }
    configureProjectFile(content)
    val editor = myFixture.editor
    waitForImageRenderer()
    EditorTestUtil.setEditorVisibleSizeInPixels(editor, 300, editor.lineHeight * 5)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val imageLine = editor.document.getLineNumber(content.indexOf(image))
    moveCaretTo(editor.document.getLineStartOffset(imageLine))
    assertEmpty(imageRegions())
    editor.scrollingModel.scrollVertically((editor.visualLineToY(imageLine) - editor.lineHeight * 2).coerceAtLeast(0))
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    val visibleArea = editor.scrollingModel.visibleArea

    moveCaretDown()

    val expected = expectedRelativeScroll(editor, visibleArea.y, visibleArea.height)
    assertEquals(expected, editor.scrollingModel.verticalScrollOffset)
    assertTrue(imageRegions().isNotEmpty())
  }

  @Test
  fun testSelectingEverythingRevealsEverything() {
    val content = "Some **bold** and `code`"
    configure(content)
    select(0, content.length)
    assertEmpty("Text cannot be selected while it is still hidden", concealed())
    assertEquals(content, myFixture.editor.selectionModel.selectedText)
  }

  @Test
  fun testBackspacePastAnElementDeletesOneCharacter() {
    val content = "**bold** x"
    configure(content)
    moveCaretTo(content.length - 1)
    assertEquals(listOf("**", "**"), concealed())
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    assertEquals("**bold**x", myFixture.editor.document.text)
  }

  @Test
  fun testBackspaceInsideAnElementDeletesOneCharacter() {
    val content = "**bold** x"
    configure(content)
    moveCaretTo("**bold".length)
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    assertEquals("**bol** x", myFixture.editor.document.text)
  }

  @Test
  fun testBackspaceAfterAutolinkDeletesOneCharacter() {
    assertBackspaceAfterElement("<https://example.org>")
  }

  @Test
  fun testBackspaceAfterEmphasisDeletesOneCharacter() {
    assertBackspaceAfterElement("*italic*")
  }

  @Test
  fun testBackspaceAfterStrongEmphasisDeletesOneCharacter() {
    assertBackspaceAfterElement("**bold**")
  }

  @Test
  fun testBackspaceAfterCodeSpanDeletesOneCharacter() {
    assertBackspaceAfterElement("`code`")
  }

  @Test
  fun testBackspaceAfterInlineLinkDeletesOneCharacter() {
    assertBackspaceAfterElement("[link](https://example.org)")
  }

  @Test
  fun testBackspaceAfterStrikethroughDeletesOneCharacter() {
    assertBackspaceAfterElement("~~deleted~~")
  }

  @Test
  fun testBackspaceAfterThematicBreakDeletesOneCharacter() {
    assertBackspaceAfterElement("---")
  }

  @Test
  fun testBackspaceAfterImageDoesNotDeleteTheImage() {
    addPng(120, 60)
    val image = "![logo](image.png)"
    val content = "`KotlinClass`\n\n$image\n"
    configureProjectFile("$content<caret>")
    waitForImageRenderer()

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)

    assertEquals(content.dropLast(1), myFixture.editor.document.text)
    assertEquals(content.length - 1, myFixture.editor.caretModel.offset)
    assertEmpty(imageRegions())
  }

  @Test
  fun testDeleteAfterAnElementDeletesOneCharacter() {
    val content = "**bold** x"
    configure(content)
    moveCaretTo(content.indexOf(" x"))
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE)
    assertEquals("**bold**x", myFixture.editor.document.text)
  }

  @Test
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

  @Test
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
  @Test
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
  @Test
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

  private fun assertBackspaceAfterElement(element: String) {
    val content = "$element\n"
    configure("$content<caret>")

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)

    assertEquals(element, myFixture.editor.document.text)
    assertEquals(element.length, myFixture.editor.caretModel.offset)
  }

  /** Opens the file and lets the highlighting pass compute and publish the specs, as it does in an editor. */
  private fun configure(content: String) {
    myFixture.configureByText("test.md", content)
    myFixture.doHighlighting()
  }

  private fun configureProjectFile(content: String) {
    val sourcePath = projectRoot().resolve("docs/test.md")
    Files.createDirectories(sourcePath.parent)
    Files.writeString(sourcePath, content)
    val source = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sourcePath))
    myFixture.configureFromExistingVirtualFile(source)
    myFixture.editor.caretModel.moveToOffset(content.length)
    myFixture.doHighlighting()
  }

  private fun addPng(width: Int, height: Int): VirtualFile = addBinaryFile("docs/image.png", pngBytes(width, height))

  private fun addBinaryFile(path: String, bytes: ByteArray): VirtualFile {
    val filePath = projectRoot().resolve(path)
    Files.createDirectories(filePath.parent)
    Files.write(filePath, bytes)
    return requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath))
  }

  private fun pngBytes(width: Int, height: Int): ByteArray {
    val output = ByteArrayOutputStream()
    check(ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output))
    return output.toByteArray()
  }

  private fun waitForImageRenderer(): CustomFoldRegion {
    waitForImageRegions(1)
    return imageRegions().single()
  }

  private fun projectRoot(): Path = ModuleRootManager.getInstance(myFixture.module).contentRoots.last().toNioPath()

  private fun waitForImageRegions(count: Int) {
    PlatformTestUtil.waitWithEventsDispatching(
      "Image renderer was not created",
      { imageRegions().size == count && imageRegions().all { it.renderer is DocRenderer } },
      10,
    )
  }

  private fun waitForNoImageRegion() {
    PlatformTestUtil.waitWithEventsDispatching(
      "Image fold was not removed",
      { imageRegions().isEmpty() },
      10,
    )
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
      .filter { it.isValid && (it is CustomFoldRegion || it.shouldNeverExpand()) }
      .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))

  private fun thematicBreakHighlighters(): List<RangeHighlighter> =
    myFixture.editor.markupModel.allHighlighters
      .filter { it.isValid && it.customRenderer != null }
      .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))

  private fun imageRegions(): List<CustomFoldRegion> =
    myFixture.editor.foldingModel.allFoldRegions
      .filterIsInstance<CustomFoldRegion>()
      .filter { it.isValid }
}
