// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.editor.fence.collectCodeSpanBackground
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeSpan
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import kotlin.math.abs

class MarkdownCodeSpanBackgroundTest : BasePlatformTestCase() {
  fun `test the background covers the content between the backtick runs`() {
    assertEquals(TextRange(3, 7), collect("a `code` b"))
  }

  fun `test the background keeps the spaces next to the backtick runs`() {
    assertEquals(TextRange(2, 10), collect("`` `code` ``"))
  }

  fun `test the content has the code span background and the backtick has none`() {
    val editor = configureEditor("a `x    y` b")

    val image = paint(editor)

    assertEquals(Color.RED, pixelAt(editor, image, 6))
    assertEquals(editorBackground(editor), pixelAt(editor, image, 2))
  }

  fun `test the background covers an inline HTML tag`() {
    val text = "`test <a> hello`"
    assertEquals(TextRange(1, 15), collect(text))
    val codeSpanBackground = EditorColorsManager.getInstance().globalScheme
      .getAttributes(MarkdownHighlighterColors.CODE_SPAN).backgroundColor
    assertNotNull(codeSpanBackground)
    val editor = configureEditor(text, codeSpanBackground!!)

    val image = paint(editor)

    assertEquals(codeSpanBackground, pixelAt(editor, image, 9))
  }

  fun `test the selection stays visible over the code span background`() {
    val editor = configureEditor("`test <a> hello`")
    editor.selectionModel.setSelection(6, 10)

    val image = paint(editor)

    assertEquals(editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR), pixelAt(editor, image, 9))
    assertEquals(Color.RED, pixelAt(editor, image, 5))
  }

  fun `test the corner of the background is round`() {
    val editor = configureEditor("a `x    y` b")

    val image = paint(editor)
    val corner = editor.offsetToXY(3)

    assertEquals(Color.RED, pixelAt(editor, image, 5))
    assertOutsideTheFill(editor, Color(image.getRGB(corner.x, corner.y)))
  }

  fun `test a span over two lines paints one rectangle per line`() {
    val editor = configureEditor("`a\n   b` c")

    val image = paint(editor)
    val firstLineEnd = editor.offsetToXY(2)

    assertEquals(Color.RED, pixelAt(editor, image, 4))
    assertEquals(editorBackground(editor), pixelAt(editor, image, 8))
    assertEquals(editorBackground(editor), Color(image.getRGB(firstLineEnd.x + 2, firstLineEnd.y + editor.lineHeight / 2)))
  }

  private fun collect(text: String): TextRange? {
    myFixture.configureByText("test.md", text)
    val span = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeSpan::class.java)
    assertNotNull(span)
    return collectCodeSpanBackground(span!!)
  }

  private fun configureEditor(text: String, codeSpanBackground: Color = Color.RED): Editor {
    myFixture.configureByText("test.md", text)
    val editor = myFixture.editor
    editor.settings.isCaretRowShown = false
    editor.colorsScheme.setAttributes(MarkdownHighlighterColors.CODE_SPAN, TextAttributes(null, codeSpanBackground, null, null, Font.PLAIN))
    EditorTestUtil.setEditorVisibleSize(editor, 80, 10)
    myFixture.doHighlighting()
    return editor
  }

  private fun editorBackground(editor: Editor): Color = (editor as EditorEx).backgroundColor

  private fun paint(editor: Editor): BufferedImage {
    val component = editor.contentComponent
    val image = BufferedImage(component.width, component.height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    try {
      component.paint(graphics)
    }
    finally {
      graphics.dispose()
    }
    return image
  }

  /** The pixel at the left edge of [offset], at the middle of its line. The tests sample a space, so no glyph is there. */
  private fun pixelAt(editor: Editor, image: BufferedImage, offset: Int): Color {
    val point = editor.offsetToXY(offset)
    return Color(image.getRGB(point.x, point.y + editor.lineHeight / 2))
  }

  /** Anti-aliasing mixes a few percent of the red fill into a pixel outside the arc. A square corner is pure red. */
  private fun assertOutsideTheFill(editor: Editor, actual: Color) {
    val background = editorBackground(editor)
    assertTrue("Expected a color near $background, got $actual", distance(background, actual) < distance(Color.RED, actual))
  }

  private fun distance(a: Color, b: Color): Int = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
}
