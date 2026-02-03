package org.intellij.plugins.markdown.psi

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkdownHeaderNonWhitespaceRangeTest: LightPlatformCodeInsightTestCase() {
  private val firstElement
    get() = file.firstChild!!

  @Test
  fun `test range sanity in atx header`() {
    // language=Markdown
    val content = """
    # Some header
    """.trimIndent()
    doTest(content, "Some header")
  }

  @Test
  fun `test range sanity in setex header`() {
    // language=Markdown
    val content = """
    Some header
    ---
    """.trimIndent()
    doTest(content, "Some header")
  }

  private fun doTest(content: String, expectedSubstring: String) {
    configureFromFileText("some.md", content)
    val header = firstElement as MarkdownHeader
    val contentElement = header.contentElement!!
    val range = contentElement.nonWhitespaceRange
    val substring = range.substring(contentElement.text)
    TestCase.assertEquals(expectedSubstring, substring)
  }
}
