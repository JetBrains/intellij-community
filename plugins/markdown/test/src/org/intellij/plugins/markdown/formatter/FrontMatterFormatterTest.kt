package org.intellij.plugins.markdown.formatter

import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.formatter.MarkdownFormatterTest.Companion.performReformatting
import org.junit.jupiter.api.Test

@TestApplication
class FrontMatterFormatterTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test formatter does not affect frontmatter header content`() {
    // language=Markdown
    val content = """
    ---
    title: Some title
    other_property: Some property value
    some_other_property: 'quoted string literal'
    tags:
      - some tag
      - some other tag
    ---
    
    # Some header
    
    Some paragraph text
    """.trimIndent()
    doTest(content, content)
  }

  @Test
  fun `test there should be a single line after frontmatter header`() {
    // language=Markdown
    val content = """
    ---
    title: Some title
    ---
    Some paragraph text
    """.trimIndent()
    // language=Markdown
    val expected = """
    ---
    title: Some title
    ---
    
    Some paragraph text
    """.trimIndent()
    doTest(content, expected)
  }

  private fun doTest(content: String, expected: String): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      performReformatting(myFixture.project, myFixture.file)
      myFixture.checkResult(expected)
      performReformatting(myFixture.project, myFixture.file)
      myFixture.checkResult(expected)
    }
  }
}
