package org.intellij.plugins.markdown.editor

import com.intellij.codeInspection.htmlInspections.XmlInspectionToolProvider
import com.intellij.grazie.grazieTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.MarkdownTestingUtil
import com.intellij.markdown.backend.inspections.MarkdownIncorrectTableFormattingInspection
import org.junit.jupiter.api.Test

@TestApplication
internal class MarkdownSuppressionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(projectFixture, testDataPath = "${MarkdownTestingUtil.TEST_DATA_PATH}/editor/suppression/")

  @Test
  fun `test multiple suppressions with table`(): Unit = doTest()
  @Test
  fun `test suppressions with yaml header`(): Unit = doTest()
  @Test
  fun `test multiple suppressions with toml header`(): Unit = doTest()

  @Test
  fun `test unknown html tags and attributes are suppressed`() {
    grazie.fixture.enableInspections(XmlInspectionToolProvider())
    grazie.fixture.configureByText(
      "test.md",
      """
        <rule unknown-attribute="value">
        Always use the Oxford comma.
        </rule>
        <div unknown-attribute="value"></div>
      """.trimIndent()
    )
    grazie.fixture.doHighlighting()
  }

  private fun doTest() {
    val name = testFileName()
    grazie.fixture.enableInspections(MarkdownIncorrectTableFormattingInspection())
    grazie.fixture.configureByFile("$name.md")

    val intention = grazie.fixture.availableIntentions.first { it.text == "Suppress for file" }
    grazie.fixture.launchAction(intention)
    grazie.fixture.checkResultByFile("$name.after.md")
  }

  private fun testFileName(): String =
    grazie.getTestName(true).trimStart().replace(' ', '_')
}
