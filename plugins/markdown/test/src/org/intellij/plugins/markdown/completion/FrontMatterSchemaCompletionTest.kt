package org.intellij.plugins.markdown.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FrontMatterSchemaCompletionTest: BasePlatformTestCase() {
  fun `test yaml header has completion`() {
    val content = """
    ---
    t<caret>
    ---
    """.trimIndent()
    val expected = listOf(
      "tags",
      "title",
      "layout",
      "categories",
      "category",
      "date"
    )
    doTest(content, expected)
  }

  fun `test toml header has completion`() {
    val content = """
    +++
    t<caret>
    +++
    """.trimIndent()
    val expected = listOf(
      "tags",
      "title",
      "layout",
      "categories",
      "category",
      "date"
    )
    doTest(content, expected)
  }

  private fun doTest(content: String, expected: List<String>) {
    myFixture.configureByText("some.md", content)
    val variants =  myFixture.complete(CompletionType.BASIC)
    checkNotNull(variants) { "Failed to obtain lookup variants" }
    val lookups = variants.map { it.lookupString }
    assertSameElements(lookups, expected)
  }
}
