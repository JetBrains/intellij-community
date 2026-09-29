// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.markdown.backend.editor.fence.CodeFenceBackground
import com.intellij.markdown.backend.editor.fence.collectCodeFenceBackground
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import java.awt.Color
import java.awt.image.BufferedImage

class MarkdownCodeFenceBackgroundTest : BasePlatformTestCase() {
  fun `test a fence suppresses the background of its injection`() {
    myFixture.configureByText("test.md", "```python\na\n```")

    val fence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)
    assertNotNull(fence)
    assertFalse(InjectedLanguageUtil.isHighlightInjectionBackground(fence))
  }

  fun `test the background of a top-level fence covers the lines of the code`() {
    val (background, document) = collect("```python\na\n\nbbbb\n```")

    assertEquals(0, column(background, document))
    assertEquals(document.getLineStartOffset(1), background.startOffset)
    assertEquals(document.getLineEndOffset(3), background.endOffset)
  }

  fun `test the background of a fence in a list starts in the column of the code`() {
    val (background, document) = collect("- ```python\n  a\n\n  bbbb\n  ```")

    assertEquals(2, column(background, document))
    assertEquals(document.getLineStartOffset(1), background.startOffset)
    assertEquals(document.getLineEndOffset(3), background.endOffset)
  }

  fun `test the background of an indented fence starts in the column of the code`() {
    val (background, document) = collect("    ```python\n    a\n\n    bbbb\n    ```")

    assertEquals(4, column(background, document))
  }

  fun `test the background of a fence in a block quote starts in the column of the code`() {
    val (background, document) = collect(">     ```python\n>     a\n>\n>     bbbb\n>     ```")

    assertEquals(6, column(background, document))
  }

  fun `test the background ignores an extra indent on a blank line`() {
    val (background, document) = collect("    ```python\n    a\n        \n    b\n    ```")

    assertEquals(4, column(background, document))
  }

  fun `test a fence that holds only blank lines keeps a background`() {
    val (background, document) = collect("    ```python\n    \n    ```")

    assertEquals(4, column(background, document))
    assertEquals(1, document.getLineNumber(background.startOffset))
    assertEquals(1, document.getLineNumber(background.endOffset))
  }

  fun `test a fence without a closing marker keeps a background`() {
    val (background, document) = collect("```python\na\n")

    assertEquals(0, column(background, document))
    assertEquals(document.getLineStartOffset(1), background.startOffset)
  }

  private fun collect(text: String): Pair<CodeFenceBackground, Document> {
    myFixture.configureByText("test.md", text)

    val fence = PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java)
    assertNotNull(fence)
    val document = myFixture.editor.document
    val background = collectCodeFenceBackground(fence!!, document)
    assertNotNull(background)
    return background!! to document
  }

  /** The column of the left side of the background. */
  private fun column(background: CodeFenceBackground, document: Document): Int {
    val line = document.getLineNumber(background.codeOffset)
    return background.codeOffset - document.getLineStartOffset(line)
  }

  fun `test selection uses the selection background inside a code fence`() {
    val editor = configureEditor()
    val codeStart = TEXT.indexOf(CODE_LINE)
    val sampleOffset = codeStart + CODE_LINE_PREFIX.length + 5

    val withoutSelection = paint(editor)
    select(editor, codeStart, codeStart + CODE_LINE.length)
    val withSelection = paint(editor)

    assertEquals(codeFenceBackgroundColor(editor), pixelAt(editor, withoutSelection, sampleOffset))
    assertEquals(selectionBackgroundColor(editor), pixelAt(editor, withSelection, sampleOffset))
  }

  fun `test selection uses the selection background on an empty code fence line`() {
    val editor = configureEditor()
    val emptyLineSampleOffset = TEXT.indexOf("\n\n") + 1
    val selectionStart = TEXT.indexOf(CODE_LINE)
    val secondCodeLineStart = TEXT.indexOf(SECOND_CODE_LINE)
    val selectionEnd = secondCodeLineStart + SECOND_CODE_LINE.length

    val withoutSelection = paint(editor)
    select(editor, selectionStart, selectionEnd)
    val withSelection = paint(editor)

    assertEquals(codeFenceBackgroundColor(editor), pixelAt(editor, withoutSelection, emptyLineSampleOffset))
    assertEquals(selectionBackgroundColor(editor), pixelAt(editor, withSelection, emptyLineSampleOffset))
  }

  private fun configureEditor(): Editor {
    myFixture.configureByText("test.md", TEXT)
    val editor = myFixture.editor
    editor.settings.isCaretRowShown = false
    EditorTestUtil.setEditorVisibleSize(editor, 80, 10)
    myFixture.doHighlighting()
    return editor
  }

  private fun codeFenceBackgroundColor(editor: Editor): Color {
    val scheme = editor.colorsScheme
    val codeFenceColor = scheme.getAttributes(MarkdownHighlighterColors.CODE_FENCE)?.backgroundColor
    if (codeFenceColor != null) return codeFenceColor
    val injectedLanguageKey = EditorColors.createInjectedLanguageFragmentKey(MarkdownLanguage.INSTANCE)
    return checkNotNull(scheme.getAttributes(injectedLanguageKey)?.backgroundColor)
  }

  private fun selectionBackgroundColor(editor: Editor): Color =
    checkNotNull(editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR))

  private fun select(editor: Editor, startOffset: Int, endOffset: Int) {
    editor.selectionModel.setSelection(startOffset, endOffset)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

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

  private fun pixelAt(editor: Editor, image: BufferedImage, offset: Int): Color {
    val point = editor.offsetToXY(offset)
    return Color(image.getRGB(point.x, point.y + editor.lineHeight / 2))
  }

  private companion object {
    const val CODE_LINE_PREFIX = "int a = 3;"
    const val SECOND_CODE_LINE = "System.out.println(\"test\");"
    val CODE_LINE = CODE_LINE_PREFIX + " ".repeat(20)
    val TEXT = "```java\n$CODE_LINE\n\n$SECOND_CODE_LINE\n```"
  }
}
