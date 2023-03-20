package org.intellij.plugins.markdown.psi

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightPlatformCodeInsightTestCase

class MarkdownCodeFenceManipulatorSanityTest: LightPlatformCodeInsightTestCase() {
  // language=Markdown
  private val commonContent = """
  ```
  Some text inside code fence
  And some more text on the next line
  ```
  """.trimIndent()

  fun `test replacement at the start`() {
    // language=Markdown
    val expected = """
    ```
    test text inside code fence
    And some more text on the next line
    ```
    """.trimIndent()
    doReplacementTest(commonContent, expected, replacement = "test", range = TextRange(4, 8))
  }

  fun `test replacement at the start with info string`() {
    // language=Markdown
    val content = """
    ```text
    Some text inside code fence
    And some more text on the next line
    ```
    """.trimIndent()
    // language=Markdown
    val expected = """
    ```text
    test text inside code fence
    And some more text on the next line
    ```
    """.trimIndent()
    doReplacementTest(content, expected, replacement = "test", range = TextRange(8, 12))
  }

  fun `test replacement in the middle`() {
    // language=Markdown
    val expected = """
    ```
    Some text test code fence
    And some more text on the next line
    ```
    """.trimIndent()
    doReplacementTest(commonContent, expected, replacement = "test", range = TextRange(14, 20))
  }

  fun `test replace all content`() {
    // language=Markdown
    val expected = """
    ```
    replaced
    ```
    """.trimIndent()
    doReplacementTest(commonContent, expected, replacement = "replaced", range = null)
  }

  // IDEA-291426
  fun `test replace inside double blockquote simple one line`() {
    // language=Markdown
    val content = """
    > > ```
    > > > some text and link http://
    > > ```
    """.trimIndent()
    val expected = """
    > > ```
    > > > some text and link https://
    > > ```
    """.trimIndent()
    doReplacementTest(content, expected, replacement = "https://", range = TextRange(29, 36), this::doubleBlockquoteGetter)
  }

  fun `test replace inside double blockquote simple one line with suffix`() {
    // language=Markdown
    val content = """
    > > ```
    > > > some text and link http://example.com
    > > ```
    """.trimIndent()
    val expected = """
    > > ```
    > > > some text and link https://example.com
    > > ```
    """.trimIndent()
    doReplacementTest(content, expected, replacement = "https://", range = TextRange(29, 36), this::doubleBlockquoteGetter)
  }

  fun `test replace with block quote 1`() {
    // language=Markdown
    val content = """
    > ```
    > > some http://
    > ```
    """.trimIndent()
    // language=Markdown
    val expected = """
    > ```
    > > some test
    > ```
    """.trimIndent()
    doReplacementTest(content, expected, replacement = "test", range = TextRange(13, 20), this::singleBlockquoteGetter)
  }


  private fun plainCodeFenceGetter(file: PsiFile): PsiElement? {
    return file.firstChild
  }

  private fun singleBlockquoteGetter(file: PsiFile): PsiElement? {
    return file.firstChild.firstChild.nextSibling
  }

  private fun doubleBlockquoteGetter(file: PsiFile): PsiElement? {
    return file.firstChild.firstChild.nextSibling.firstChild.nextSibling
  }

  private fun doReplacementTest(
    content: String,
    expected: String,
    replacement: String,
    range: TextRange? = null,
    elementGetter: (PsiFile) -> PsiElement? = this::plainCodeFenceGetter
  ) {
    configureFromFileText("some.md", content)
    val element = elementGetter.invoke(file)
    checkNotNull(element)
    val manipulator = ElementManipulators.getNotNullManipulator(element)
    runWriteActionAndWait {
      when (range) {
        null -> manipulator.handleContentChange(element, replacement)
        else -> manipulator.handleContentChange(element, range, replacement)
      }
      commitAllDocuments()
    }
    checkResultByText(expected)
  }
}
