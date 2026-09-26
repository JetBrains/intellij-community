// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.markdown.backend.editor.fence.CodeFenceBackground
import com.intellij.markdown.backend.editor.fence.collectCodeFenceBackground
import com.intellij.openapi.editor.Document
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence

class MarkdownCodeFenceBackgroundTest : LightPlatformCodeInsightTestCase() {
  fun `test a fence suppresses the background of its injection`() {
    configureFromFileText("test.md", "```python\na\n```")

    val fence = PsiTreeUtil.findChildOfType(file, MarkdownCodeFence::class.java)
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
    configureFromFileText("test.md", text)

    val fence = PsiTreeUtil.findChildOfType(file, MarkdownCodeFence::class.java)
    assertNotNull(fence)
    val document = editor.document
    val background = collectCodeFenceBackground(fence!!, document)
    assertNotNull(background)
    return background!! to document
  }

  /** The column of the left side of the background. */
  private fun column(background: CodeFenceBackground, document: Document): Int {
    val line = document.getLineNumber(background.codeOffset)
    return background.codeOffset - document.getLineStartOffset(line)
  }
}
