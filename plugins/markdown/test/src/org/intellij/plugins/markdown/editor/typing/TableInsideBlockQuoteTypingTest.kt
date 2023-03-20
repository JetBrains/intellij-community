package org.intellij.plugins.markdown.editor.typing

import com.intellij.idea.TestFor
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.editor.MarkdownCodeInsightSettingsRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@TestFor(issues = ["IDEA-277092"])
@RunWith(JUnit4::class)
class TableInsideBlockQuoteTypingTest: LightPlatformCodeInsightTestCase() {
  @get:Rule
  val rule = MarkdownCodeInsightSettingsRule { it.copy(insertHtmlLineBreakInsideTables = false) }

  @Test
  fun `test table inside quote without last pipe`() {
    val content = """
    > | Table | Frezze | Bug  | 
    > | ---   | ------ | ---- | 
    > | foo   | some   | fix  |
    > | bar   | item   | plz 
    |
    """.trimIndent()
    configureFromFileText("some.md", content)
    checkResultByText(content)
  }

  @Test
  fun `test press enter in table inside quote right before last pipe`() {
    val content = """
    > | Table | Frezze | Bug  | 
    > | ---   | ------ | ---- | 
    > | foo   | some   | fix  |
    > | bar   | item   | plz  <caret>|
    """.trimIndent()
    val expected = """
    > | Table | Frezze | Bug  | 
    > | ---   | ------ | ---- | 
    > | foo   | some   | fix  |
    > | bar   | item   | plz  
    <caret>|
    """.trimIndent()
    configureFromFileText("some.md", content)
    type('\n')
    checkResultByText(expected)
  }
}
