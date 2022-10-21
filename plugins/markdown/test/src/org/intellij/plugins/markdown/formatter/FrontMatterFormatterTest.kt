package org.intellij.plugins.markdown.formatter

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.RegistryKeyRule
import org.intellij.plugins.markdown.formatter.MarkdownFormatterTest.Companion.performReformatting
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FrontMatterFormatterTest: LightPlatformCodeInsightTestCase() {
  @Rule
  @JvmField
  val rule = RegistryKeyRule("markdown.experimental.frontmatter.support.enable", true)

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

  private fun doTest(content: String, expected: String) {
    configureFromFileText("some.md", content)
    performReformatting(project, file)
    checkResultByText(expected)
    performReformatting(project, file)
    checkResultByText(expected)
  }
}
