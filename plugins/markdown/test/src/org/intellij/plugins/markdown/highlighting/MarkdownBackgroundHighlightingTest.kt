// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil.paintEditor
import org.intellij.plugins.markdown.editor.fence.collectBlockQuoteBackground
import org.intellij.plugins.markdown.editor.fence.collectCodeFenceBackground
import org.intellij.plugins.markdown.editor.fence.collectCodeSpanBackground
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeSpan
import java.awt.Color
import java.awt.Font
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.math.abs

class MarkdownBackgroundHighlightingTest : BasePlatformTestCase() {
  fun `test a fence suppresses the background of its injection`() {
    myFixture.configureByText("test.md", "```python\na\n```")

    val fence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)
    assertNotNull(fence)
    assertFalse(InjectedLanguageUtil.isHighlightInjectionBackground(fence))
  }

  fun `test the background of a top-level fence covers the fence from the opening marker to the closing marker`() {
    val [background, document] = collectFence("```python\na\n\nbbbb\n```")

    assertEquals(document.getLineStartOffset(0), background.startOffset)
    assertEquals(document.getLineEndOffset(4), background.endOffset)
  }

  fun `test the background of a fence in a list starts in the column of the code`() {
    val [background, document] = collectFence("- ```python\n  a\n\n  bbbb\n  ```")

    assertEquals(2, getColumn(background, document))
    assertEquals(document.getLineEndOffset(4), background.endOffset)
  }

  fun `test the background of an indented fence starts in the column of the code`() {
    val [background, document] = collectFence("    ```python\n    a\n\n    bbbb\n    ```")

    assertEquals(4, getColumn(background, document))
  }

  fun `test the background of a fence in a block quote starts in the column of the code`() {
    val [background, document] = collectFence(">     ```python\n>     a\n>\n>     bbbb\n>     ```")

    assertEquals(6, getColumn(background, document))
  }

  fun `test the background of a fence ignores an extra indent on a blank line`() {
    val [background, document] = collectFence("    ```python\n    a\n        \n    b\n    ```")

    assertEquals(4, getColumn(background, document))
  }

  fun `test a fence that holds only blank lines keeps a background`() {
    val [background, document] = collectFence("    ```python\n    \n    ```")

    assertEquals(4, getColumn(background, document))
    assertEquals(2, document.getLineNumber(background.endOffset))
  }

  fun `test a fence without a closing marker keeps a background`() {
    val [background, document] = collectFence("```python\na\n")

    assertEquals(0, getColumn(background, document))
    assertEquals(1, document.getLineNumber(background.endOffset))
  }

  fun `test selection uses the selection background inside a code fence`() {
    val editor = configureEditor(FENCE_TEXT)
    val codeStart = FENCE_TEXT.indexOf(CODE_LINE)
    val sampleOffset = codeStart + CODE_LINE_PREFIX.length + 5

    val withoutSelection = paintEditor(editor)
    select(editor, codeStart, codeStart + CODE_LINE.length)
    val withSelection = paintEditor(editor)

    assertEquals(getCodeFenceBackgroundColor(editor), getPixelAt(editor, withoutSelection, sampleOffset))
    assertEquals(getSelectionBackgroundColor(editor), getPixelAt(editor, withSelection, sampleOffset))
  }

  fun `test selection uses the selection background on an empty code fence line`() {
    val editor = configureEditor(FENCE_TEXT)
    val emptyLineSampleOffset = FENCE_TEXT.indexOf("\n\n") + 1
    val selectionStart = FENCE_TEXT.indexOf(CODE_LINE)
    val selectionEnd = FENCE_TEXT.indexOf(SECOND_CODE_LINE) + SECOND_CODE_LINE.length

    val withoutSelection = paintEditor(editor)
    select(editor, selectionStart, selectionEnd)
    val withSelection = paintEditor(editor)

    assertEquals(getCodeFenceBackgroundColor(editor), getPixelAt(editor, withoutSelection, emptyLineSampleOffset))
    assertEquals(getSelectionBackgroundColor(editor), getPixelAt(editor, withSelection, emptyLineSampleOffset))
  }

  fun `test the background of a fence without a language has round corners`() {
    val editor = configureEditor("```\naaaa\n\nbbbb\n```", MarkdownHighlighterColors.CODE_FENCE)

    val image = paintEditor(editor)
    val corner = editor.offsetToXY(editor.document.getLineStartOffset(0))
    val blankLine = editor.offsetToXY(editor.document.getLineStartOffset(2))

    assertEquals(Color.RED, getPixel(image, corner.x, blankLine.y + editor.lineHeight / 2))
    assertOutsideTheFill(editor, getPixel(image, corner.x, corner.y))
  }

  fun `test the background of a code span covers the content between the backtick runs`() {
    assertEquals(TextRange(3, 7), collectSpan("a `code` b"))
  }

  fun `test the background of a code span keeps the spaces next to the backtick runs`() {
    assertEquals(TextRange(2, 10), collectSpan("`` `code` ``"))
  }

  fun `test the content has the code span background and the backtick has none`() {
    val editor = configureEditor("a `x    y` b", MarkdownHighlighterColors.CODE_SPAN)

    val image = paintEditor(editor)

    assertEquals(Color.RED, getPixelAt(editor, image, 6))
    assertEquals(getEditorBackgroundColor(editor), getPixelAt(editor, image, 2))
  }

  fun `test the background of a code span covers an inline HTML tag`() {
    val text = "`test <a> hello`"
    assertEquals(TextRange(1, 15), collectSpan(text))
    val codeSpanBackground = EditorColorsManager.getInstance().globalScheme
      .getAttributes(MarkdownHighlighterColors.CODE_SPAN).backgroundColor
    assertNotNull(codeSpanBackground)
    val editor = configureEditor(text, MarkdownHighlighterColors.CODE_SPAN, codeSpanBackground!!)

    val image = paintEditor(editor)

    assertEquals(codeSpanBackground, getPixelAt(editor, image, 9))
  }

  fun `test the selection stays visible over the code span background`() {
    val editor = configureEditor("`test <a> hello`", MarkdownHighlighterColors.CODE_SPAN)
    editor.selectionModel.setSelection(6, 10)

    val image = paintEditor(editor)

    assertEquals(getSelectionBackgroundColor(editor), getPixelAt(editor, image, 9))
    assertEquals(Color.RED, getPixelAt(editor, image, 5))
  }

  fun `test the corners of a code span background are round`() {
    val editor = configureEditor("a `x    y` b", MarkdownHighlighterColors.CODE_SPAN)

    val image = paintEditor(editor)
    val corner = editor.offsetToXY(3)

    assertEquals(Color.RED, getPixelAt(editor, image, 5))
    assertOutsideTheFill(editor, getPixel(image, corner.x, corner.y))
  }

  fun `test a code span over two lines paints one rectangle per line`() {
    val editor = configureEditor("`a\n   b` c", MarkdownHighlighterColors.CODE_SPAN)

    val image = paintEditor(editor)
    val firstLineEnd = editor.offsetToXY(2)

    assertEquals(Color.RED, getPixelAt(editor, image, 4))
    assertEquals(getEditorBackgroundColor(editor), getPixelAt(editor, image, 8))
    assertEquals(getEditorBackgroundColor(editor), getPixel(image, firstLineEnd.x + 2, firstLineEnd.y + editor.lineHeight / 2))
  }

  fun `test only an outermost quote or alert has a background`() {
    val text = """
      |> lazy line
      |> another line
      |> > third line
      |> > > fourth line
      |
      |- > quote in list
      |  > list item
      |
      |> * 123
      |* > 123
      |
      |> > nested
      |> outer
      |
      |1. [ ] > test
      |- [ ] > test 2
      |- > a
      |> > b [IntelliJ IDEA][]
    """.trimMargin()

    assertEquals(
      listOf(
        "> lazy line\n> another line\n> > third line\n> > > fourth line",
        "> quote in list\n  > list item",
        "> * 123",
        "> 123",
        "> > nested\n> outer",
        "> test",
        "> test 2",
        "> a",
        "> > b [IntelliJ IDEA][]",
      ),
      collectQuotes(text),
    )
    assertEquals(listOf("> [!NOTE]\n> > inner"), collectQuotes("> [!NOTE]\n> > inner"))
  }

  fun `test the left corners of a quote background are square and the right corners are round`() {
    val editor = configureEditor("> quote", MarkdownHighlighterColors.BLOCK_QUOTE)
    val image = paintEditor(editor)
    val top = editor.offsetToXY(0)
    val right = getRightEdge(editor, image)

    assertEquals(Color.RED, getPixel(image, top.x, top.y))
    assertEquals(Color.RED, getPixel(image, right, top.y + editor.lineHeight / 2))
    assertOutsideTheFill(editor, getPixel(image, right, top.y))
    assertTrue("The background must end before the right edge", right < editor.scrollingModel.visibleArea.let { it.x + it.width } - 1)
  }

  fun `test each line of a quote background starts at the opening marker or at its text further left`() {
    val editor = configureEditor("- [ ] > a\n  > b\n\n> c\n  > d\n\n> e\nlazy", MarkdownHighlighterColors.BLOCK_QUOTE)
    val image = paintEditor(editor)
    val text = editor.document.text

    assertOutsideTheFill(editor, getTopPixel(editor, image, text.indexOf("[ ]")))
    assertEquals("A line below a task checkbox", Color.RED, getTopPixel(editor, image, text.indexOf("> b")))
    assertEquals("A marker with an indent", Color.RED, getTopPixel(editor, image, text.indexOf("  > d")))
    // The parser puts a lazy line into a paragraph of its own, after the quote.
    assertOutsideTheFill(editor, getTopPixel(editor, image, text.indexOf("lazy")))
  }

  fun `test a wrapped quote line keeps its text on the background`() {
    val editor = configureEditor("- > aaaa bbbb cccc dddd eeee", MarkdownHighlighterColors.BLOCK_QUOTE)
    EditorTestUtil.configureSoftWraps(editor, 12)
    val wrap = editor.visualPositionToOffset(VisualPosition(1, 0))
    val wrapX = editor.offsetToXY(wrap, true, false).x
    assertTrue("The wrapped part must start left of the marker", wrapX < editor.offsetToXY(2).x)

    val image = paintEditor(editor)

    assertEquals(Color.RED, getPixel(image, wrapX, editor.visualLineToY(1) + 1))
    assertOutsideTheFill(editor, getPixel(image, wrapX, 1))
  }

  fun `test a clip in the middle of a quote paints no corner`() {
    val editor = configureEditor("> a\n> b\n> c\n> d", MarkdownHighlighterColors.BLOCK_QUOTE)
    val right = getRightEdge(editor, paintEditor(editor))
    val y = editor.offsetToXY(editor.document.getLineStartOffset(2)).y

    val image = paintEditor(editor, Rectangle(0, y, editor.contentComponent.width, editor.lineHeight))

    assertEquals(Color.RED, getPixel(image, right, y))
  }

  private fun collectFence(text: String): Pair<TextRange, Document> {
    myFixture.configureByText("test.md", text)
    val fence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)
    assertNotNull(fence)
    val background = collectCodeFenceBackground(fence!!)
    assertNotNull(background)
    return background!! to myFixture.editor.document
  }

  /** The column of the left side of the background. */
  private fun getColumn(background: TextRange, document: Document): Int {
    val line = document.getLineNumber(background.startOffset)
    return background.startOffset - document.getLineStartOffset(line)
  }

  private fun collectSpan(text: String): TextRange? {
    myFixture.configureByText("test.md", text)
    val span = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeSpan::class.java)
    assertNotNull(span)
    return collectCodeSpanBackground(span!!)
  }

  private fun collectQuotes(text: String): List<String> {
    myFixture.configureByText("test.md", text)
    val quotes = PsiTreeUtil.collectElements(myFixture.file) { it.elementType == MarkdownElementTypes.BLOCK_QUOTE || it.elementType == MarkdownElementTypes.ALERT }
    return quotes.mapNotNull { collectBlockQuoteBackground(it)?.substring(text) }
  }

  /** Opens [text] in an editor of 80 by 10 columns without the caret row. A non-null [key] gets the [background] color. */
  private fun configureEditor(text: String, key: TextAttributesKey? = null, background: Color = Color.RED): Editor {
    myFixture.configureByText("test.md", text)
    val editor = myFixture.editor
    editor.settings.isCaretRowShown = false
    if (key != null) editor.colorsScheme.setAttributes(key, TextAttributes(null, background, null, null, Font.PLAIN))
    EditorTestUtil.setEditorVisibleSize(editor, 80, 10)
    myFixture.doHighlighting()
    return editor
  }

  private fun select(editor: Editor, startOffset: Int, endOffset: Int) {
    editor.selectionModel.setSelection(startOffset, endOffset)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun getCodeFenceBackgroundColor(editor: Editor): Color {
    val scheme = editor.colorsScheme
    val codeFenceColor = scheme.getAttributes(MarkdownHighlighterColors.CODE_FENCE)?.backgroundColor
    if (codeFenceColor != null) return codeFenceColor
    val injectedLanguageKey = EditorColors.createInjectedLanguageFragmentKey(MarkdownLanguage.INSTANCE)
    return checkNotNull(scheme.getAttributes(injectedLanguageKey)?.backgroundColor)
  }

  private fun getSelectionBackgroundColor(editor: Editor): Color =
    checkNotNull(editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR))

  private fun getEditorBackgroundColor(editor: Editor): Color = (editor as EditorEx).backgroundColor

  private fun getPixel(image: BufferedImage, x: Int, y: Int): Color = Color(image.getRGB(x, y))

  /** The pixel at the left edge of [offset], at the middle of its line. The tests sample a space, so no glyph is there. */
  private fun getPixelAt(editor: Editor, image: BufferedImage, offset: Int): Color {
    val point = editor.offsetToXY(offset)
    return getPixel(image, point.x, point.y + editor.lineHeight / 2)
  }

  /** The pixel one below the top of the line of [offset], above the glyphs. */
  private fun getTopPixel(editor: Editor, image: BufferedImage, offset: Int): Color {
    val point = editor.offsetToXY(offset)
    return getPixel(image, point.x, point.y + 1)
  }

  /** The last red x in the middle of the first line. */
  private fun getRightEdge(editor: Editor, image: BufferedImage): Int {
    val y = editor.offsetToXY(0).y + editor.lineHeight / 2
    return (image.width - 1 downTo 0).first { getPixel(image, it, y) == Color.RED }
  }

  /** Antialiasing mixes a few percent of the red fill into a pixel outside the arc. A square corner is pure red. */
  private fun assertOutsideTheFill(editor: Editor, actual: Color) {
    val background = getEditorBackgroundColor(editor)
    assertTrue(
      "Expected a color near $background, got $actual",
      distance(background, actual) < distance(Color.RED, actual)
    )
  }

  private fun distance(a: Color, b: Color): Int = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

  private companion object {
    const val CODE_LINE_PREFIX = "int a = 3;"
    const val SECOND_CODE_LINE = "System.out.println(\"test\");"
    val CODE_LINE = CODE_LINE_PREFIX + " ".repeat(20)
    val FENCE_TEXT = "```java\n$CODE_LINE\n\n$SECOND_CODE_LINE\n```"
  }
}
