// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownLinkPastePreProcessorTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `creates link in Markdown text`() {
    assertPreprocessedText("Some <selection>text</selection>", "[text](https://example.com)")
  }

  @Test
  fun `creates link in Markdown formatting contexts`() {
    assertPreprocessedText("- *<selection>list item</selection>*", "[list item](https://example.com)")
    assertPreprocessedText("| Column |\n| --- |\n| <selection>table cell</selection> |", "[table cell](https://example.com)")
  }

  @Test
  fun `does not create link in fenced code block`() {
    assertPreprocessedText("""
      ```
      <selection>some code</selection>
      ```
    """.trimIndent(), "https://example.com")
  }

  @Test
  fun `does not create link in indented code block`() {
    assertPreprocessedText("    <selection>some code</selection>", "https://example.com")
  }

  @Test
  fun `does not create link in inline code`() {
    assertPreprocessedText("`<selection>some code</selection>`", "https://example.com")
  }

  @Test
  fun `does not create nested link`() {
    assertPreprocessedText("[<selection>link text</selection>](https://old.example.com)", "https://example.com")
  }

  @Test
  fun `does not create link from an existing link`() {
    assertPreprocessedText("<selection>[link](https://old.example.com)</selection>", "https://example.com")
    assertPreprocessedText("<selection>[link][label]</selection>\n\n[label]: https://old.example.com", "https://example.com")
    assertPreprocessedText("<selection>[label]</selection>\n\n[label]: https://old.example.com", "https://example.com")
    assertPreprocessedText("[link](<selection>https://old.example.com</selection>)", "https://example.com")
  }

  @Test
  fun `does not create link in autolink`() {
    assertPreprocessedText("<selection>https://old.example.com</selection>", "https://example.com")
    assertPreprocessedText("<<selection>https://old.example.com</selection>>", "https://example.com")
  }

  @Test
  fun `does not create link in HTML block`() {
    assertPreprocessedText("<div>\n<selection>some text</selection>\n</div>", "https://example.com")
  }

  @Test
  fun `does not create link in front matter`() {
    assertPreprocessedText("---\ntitle: <selection>some text</selection>\n---", "https://example.com")
  }

  @Test
  fun `does not create link from blank selection`() {
    assertPreprocessedText("Some <selection>   </selection> text", "https://example.com")
  }

  private fun assertPreprocessedText(fileText: String, expected: String) {
    myFixture.configureByText("test.md", fileText)
    val processor = CopyPastePreProcessor.EP_NAME.extensionList.single {
      it.javaClass.name == "org.intellij.plugins.markdown.editor.MarkdownLinkPastePreProcessor"
    }
    val actual = timeoutRunBlocking {
      readAction {
        processor.preprocessOnPaste(
          myFixture.project,
          myFixture.file,
          myFixture.editor,
          "https://example.com",
          null,
        )
      }
    }
    assertEquals(expected, actual)
  }
}
