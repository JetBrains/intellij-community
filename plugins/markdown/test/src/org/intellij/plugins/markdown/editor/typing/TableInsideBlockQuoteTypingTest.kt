package org.intellij.plugins.markdown.editor.typing

import com.intellij.idea.TestFor
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.markdownCodeInsightSettingsFixture
import org.intellij.plugins.markdown.editor.typeInEditor
import org.junit.jupiter.api.Test

@TestFor(issues = ["IDEA-277092"])
@TestApplication
class TableInsideBlockQuoteTypingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val settings = markdownCodeInsightSettingsFixture { it.insertHtmlLineBreakInsideTables = false }

  @Test
  fun `test table inside quote without last pipe`() {
    val content = """
    > | Table | Frezze | Bug  | 
    > | ---   | ------ | ---- | 
    > | foo   | some   | fix  |
    > | bar   | item   | plz 
    |
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    myFixture.checkResult(content)
  }

  @Test
  fun `test press enter in table inside quote right before last pipe`(): Unit = timeoutRunBlocking {
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
    > <caret>|
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor('\n')
      myFixture.checkResult(expected)
    }
  }
}
