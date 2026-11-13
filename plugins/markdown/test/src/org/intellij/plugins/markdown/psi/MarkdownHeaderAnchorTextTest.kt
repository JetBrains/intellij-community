package org.intellij.plugins.markdown.psi

import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
open class MarkdownHeaderAnchorTextTest {
  companion object {
    @JvmStatic
    protected val projectFixture = projectFixture(openAfterCreation = true)
  }

  protected val myFixture by codeInsightModuleFixture(projectFixture)

  protected val firstElement
    get() = myFixture.file.firstChild!!

  @Test
  fun `test simple`() {
    // language=Markdown
    val content = """
    # Some header text
    """.trimIndent()
    doTest(content, "some-header-text")
  }

  @Test
  fun `test horizontal line header`() {
    // language=Markdown
    val content = """
    Some header text
    ---
    """.trimIndent()
    doTest(content, "some-header-text")
  }

  @Test
  fun `test horizontal line header with equality signs`() {
    // language=Markdown
    val content = """
    Some header text
    ===
    """.trimIndent()
    doTest(content, "some-header-text")
  }

  @Test
  fun `test horizontal line header with link`() {
    // language=Markdown
    val content = """
    Some header [Some link text](https://jetbrains.com) suffix
    ---
    """.trimIndent()
    doTest(content, "some-header-some-link-text-suffix")
  }

  @Test
  fun `test inline link`() {
    // language=Markdown
    val content = """
    # Some header [Some link text](https://jetbrains.com) suffix
    """.trimIndent()
    doTest(content, "some-header-some-link-text-suffix")
  }

  @Test
  fun `test image`() {
    // language=Markdown
    val content = """
    # Some header ![Some image text](https://jetbrains.com) suffix
    """.trimIndent()
    doTest(content, "some-header--suffix")
  }

  @Test
  fun `test image at the end`() {
    // language=Markdown
    val content = """
    # Some header ![Some image text](https://jetbrains.com)
    """.trimIndent()
    doTest(content, "some-header-")
  }

  @Test
  fun `test image at the start`() {
    // language=Markdown
    val content = """
    # ![Some image text](https://jetbrains.com) Some header
    """.trimIndent()
    doTest(content, "-some-header")
  }

  @Test
  fun `test image inside inline link`() {
    // language=Markdown
    val content = """
    # Some header [![Some image text](https://jetbrains.com)](https://example.com) suffix
    """.trimIndent()
    doTest(content, "some-header--suffix")
  }

  @Test
  fun `test image inside inline link with text`() {
    // language=Markdown
    val content = """
    # Some header [Some link ![Some image text](https://jetbrains.com) text](https://example.com) suffix
    """.trimIndent()
    doTest(content, "some-header-some-link--text-suffix")
  }

  @Test
  fun `test special symbols are removed`() {
    val content = """
    # -  ^Foo* ?baR <baz
    """.trimIndent()
    doTest(content, "--foo-bar-baz")
  }

  @Test
  fun `test unicode`() {
    val content = """
    # This header has Unicode in it 한글
    """.trimIndent()
    doTest(content, "this-header-has-unicode-in-it-한글")
  }

  @Test
  fun `test floating point number and parenthesis`() {
    val content = """
    # This header has 3.5 in it (and parentheses)
    """.trimIndent()
    doTest(content, "this-header-has-35-in-it-and-parentheses")
  }

  @Test
  fun `test floating point number and square brackets`() {
    val content = """
    # This header has 3.5 in it [and brackets]
    """.trimIndent()
    doTest(content, "this-header-has-35-in-it-and-brackets")
  }

  protected open fun doTest(content: String, expected: String): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    readAction {
      val header = firstElement as MarkdownHeader
      val anchorText = header.anchorText
      assertEquals(expected, anchorText)
    }
  }
}
