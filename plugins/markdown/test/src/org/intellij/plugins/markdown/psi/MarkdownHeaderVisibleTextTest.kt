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
class MarkdownHeaderVisibleTextTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)
  private val firstElement
    get() = myFixture.file.firstChild!!

  @Test
  fun `test simple`() {
    // language=Markdown
    val content = """
    # Some header text
    """.trimIndent()
    doTest(content, "Some header text")
  }

  @Test
  fun `test horizontal line header`() {
    // language=Markdown
    val content = """
    Some header text
    ---
    """.trimIndent()
    doTest(content, "Some header text")
  }

  @Test
  fun `test horizontal line header with link`() {
    // language=Markdown
    val content = """
    Some header [Some link text](https://jetbrains.com) suffix
    ---
    """.trimIndent()
    doTest(content, "Some header Some link text suffix")
  }

  @Test
  fun `test inline link`() {
    // language=Markdown
    val content = """
    # Some header [Some link text](https://jetbrains.com) suffix
    """.trimIndent()
    doTest(content, "Some header Some link text suffix")
  }

  @Test
  fun `test image`() {
    // language=Markdown
    val content = """
    # Some header ![Some image text](https://jetbrains.com) suffix
    """.trimIndent()
    doTest(content, "Some header ![Some image text](https://jetbrains.com) suffix")
  }

  @Test
  fun `test hide image`() {
    // language=Markdown
    val content = """
    # Some header ![Some image text](https://jetbrains.com) suffix
    """.trimIndent()
    doTest(content, "Some header  suffix", true)
  }

  @Test
  fun `test image inside inline link`() {
    // language=Markdown
    val content = """
    # Some header [Link ![Some image text](https://jetbrains.com) text](https://example.com) suffix
    """.trimIndent()
    doTest(content, "Some header Link ![Some image text](https://jetbrains.com) text suffix")
  }

  @Test
  fun `test image inside inline link without text`() {
    // language=Markdown
    val content = """
    # Some header [![Some image text](https://jetbrains.com)](https://example.com) suffix
    """.trimIndent()
    doTest(content, "Some header ![Some image text](https://jetbrains.com) suffix")
  }

  @Test
  fun `test hide image inside inline link`() {
    // language=Markdown
    val content = """
    # Some header [![Some image text](https://jetbrains.com)](https://example.com) suffix
    """.trimIndent()
    doTest(content, "Some header  suffix", true)
  }

  @Test
  fun `test html tags`() {
    // language=Markdown
    val content = """
    # Some <b>bold</b> text
    """.trimIndent()
    doTest(content, "Some bold text")
  }

  private fun doTest(content: String, expected: String, hideImages: Boolean = false): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    readAction {
      val header = firstElement as MarkdownHeader
      val visibleText = header.buildVisibleText(hideImages)
      assertEquals(expected, visibleText)
    }
  }
}
