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
class MarkdownHeaderNonWhitespaceRangeTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)
  private val firstElement
    get() = myFixture.file.firstChild!!

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

  private fun doTest(content: String, expectedSubstring: String): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    readAction {
      val header = firstElement as MarkdownHeader
      val contentElement = header.contentElement!!
      val range = contentElement.nonWhitespaceRange
      val substring = range.substring(contentElement.text)
      assertEquals(expectedSubstring, substring)
    }
  }
}
