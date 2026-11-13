package org.intellij.plugins.markdown.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class FrontMatterSchemaCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
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

  @Test
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
    assertThat(lookups).containsExactlyInAnyOrderElementsOf(expected)
  }
}
