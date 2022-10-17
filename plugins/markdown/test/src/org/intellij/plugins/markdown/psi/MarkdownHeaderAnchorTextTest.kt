package org.intellij.plugins.markdown.psi

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader

open class MarkdownHeaderAnchorTextTest: LightPlatformCodeInsightTestCase() {
  protected val firstElement
    get() = file.firstChild?.firstChild!!

  fun `test simple`() {
    // language=Markdown
    val content = """
    # Some header text
    """.trimIndent()
    doTest(content, "some-header-text")
  }

  fun `test horizontal line header`() {
    // language=Markdown
    val content = """
    Some header text
    ---
    """.trimIndent()
    doTest(content, "some-header-text")
  }

  fun `test horizontal line header with equality signs`() {
    // language=Markdown
    val content = """
    Some header text
    ===
    """.trimIndent()
    doTest(content, "some-header-text")
  }

  fun `test horizontal line header with link`() {
    // language=Markdown
    val content = """
    Some header [Some link text](https://jetbrains.com) suffix
    ---
    """.trimIndent()
    doTest(content, "some-header-some-link-text-suffix")
  }

  fun `test inline link`() {
    // language=Markdown
    val content = """
    # Some header [Some link text](https://jetbrains.com) suffix
    """.trimIndent()
    doTest(content, "some-header-some-link-text-suffix")
  }

  fun `test image`() {
    // language=Markdown
    val content = """
    # Some header ![Some image text](https://jetbrains.com) suffix
    """.trimIndent()
    doTest(content, "some-header--suffix")
  }

  fun `test image at the end`() {
    // language=Markdown
    val content = """
    # Some header ![Some image text](https://jetbrains.com)
    """.trimIndent()
    doTest(content, "some-header-")
  }

  fun `test image at the start`() {
    // language=Markdown
    val content = """
    # ![Some image text](https://jetbrains.com) Some header
    """.trimIndent()
    doTest(content, "-some-header")
  }

  fun `test image inside inline link`() {
    // language=Markdown
    val content = """
    # Some header [![Some image text](https://jetbrains.com)](https://example.com) suffix
    """.trimIndent()
    doTest(content, "some-header--suffix")
  }

  fun `test image inside inline link with text`() {
    // language=Markdown
    val content = """
    # Some header [Some link ![Some image text](https://jetbrains.com) text](https://example.com) suffix
    """.trimIndent()
    doTest(content, "some-header-some-link--text-suffix")
  }

  fun `test special symbols are removed`() {
    val content = """
    # -  ^Foo* ?baR <baz
    """.trimIndent()
    doTest(content, "--foo--bar--baz")
  }

  fun `test unicode`() {
    val content = """
    # This header has Unicode in it 한글
    """.trimIndent()
    doTest(content, "this-header-has-unicode-in-it-한글")
  }

  protected open fun doTest(content: String, expected: String) {
    configureFromFileText("some.md", content)
    val header = firstElement as MarkdownHeader
    val anchorText = header.anchorText
    TestCase.assertEquals(expected, anchorText)
  }
}
