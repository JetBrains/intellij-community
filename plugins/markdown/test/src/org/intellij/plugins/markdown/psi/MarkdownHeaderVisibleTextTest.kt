package org.intellij.plugins.markdown.psi

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader

class MarkdownHeaderVisibleTextTest: LightPlatformCodeInsightTestCase() {
  private val firstElement
    get() = file.firstChild!!

  fun `test simple`() {
    // language=Markdown
    val content = """
    # Some header text
    """.trimIndent()
    doTest(content, "Some header text")
  }

  fun `test horizontal line header`() {
    // language=Markdown
    val content = """
    Some header text
    ---
    """.trimIndent()
    doTest(content, "Some header text")
  }

  fun `test horizontal line header with link`() {
    // language=Markdown
    val content = """
    Some header [Some link text](https://jetbrains.com) suffix
    ---
    """.trimIndent()
    doTest(content, "Some header Some link text suffix")
  }

  fun `test inline link`() {
    // language=Markdown
    val content = """
    # Some header [Some link text](https://jetbrains.com) suffix
    """.trimIndent()
    doTest(content, "Some header Some link text suffix")
  }

  fun `test image`() {
    // language=Markdown
    val content = """
    # Some header ![Some image text](https://jetbrains.com) suffix
    """.trimIndent()
    doTest(content, "Some header ![Some image text](https://jetbrains.com) suffix")
  }

  fun `test hide image`() {
    // language=Markdown
    val content = """
    # Some header ![Some image text](https://jetbrains.com) suffix
    """.trimIndent()
    doTest(content, "Some header  suffix", true)
  }

  fun `test image inside inline link`() {
    // language=Markdown
    val content = """
    # Some header [Link ![Some image text](https://jetbrains.com) text](https://example.com) suffix
    """.trimIndent()
    doTest(content, "Some header Link ![Some image text](https://jetbrains.com) text suffix")
  }

  fun `test image inside inline link without text`() {
    // language=Markdown
    val content = """
    # Some header [![Some image text](https://jetbrains.com)](https://example.com) suffix
    """.trimIndent()
    doTest(content, "Some header ![Some image text](https://jetbrains.com) suffix")
  }

  fun `test hide image inside inline link`() {
    // language=Markdown
    val content = """
    # Some header [![Some image text](https://jetbrains.com)](https://example.com) suffix
    """.trimIndent()
    doTest(content, "Some header  suffix", true)
  }

  private fun doTest(content: String, expected: String, hideImages: Boolean = false) {
    configureFromFileText("some.md", content)
    val header = firstElement as MarkdownHeader
    val visibleText = header.buildVisibleText(hideImages)
    TestCase.assertEquals(expected, visibleText)
  }
}
