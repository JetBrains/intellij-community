package org.intellij.plugins.markdown.editor

import com.intellij.codeInspection.htmlInspections.XmlInspectionToolProvider
import com.intellij.grazie.GrazieTestBase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import com.intellij.markdown.backend.inspections.MarkdownIncorrectTableFormattingInspection

internal class MarkdownSuppressionTest: GrazieTestBase() {

  override fun getTestDataPath(): String = "${MarkdownTestingUtil.TEST_DATA_PATH}/editor/suppression/"

  fun `test multiple suppressions with table`() = doTest()
  fun `test suppressions with yaml header`() = doTest()
  fun `test multiple suppressions with toml header`() = doTest()

  fun `test unknown html tags and attributes are suppressed`() {
    myFixture.enableInspections(XmlInspectionToolProvider())
    myFixture.configureByText(
      "test.md",
      """
        <rule unknown-attribute="value">
        Always use the Oxford comma.
        </rule>
        <div unknown-attribute="value"></div>
      """.trimIndent()
    )
    myFixture.doHighlighting()
  }

  private fun doTest() {
    val name = getTestName(true)
    myFixture.enableInspections(MarkdownIncorrectTableFormattingInspection())
    myFixture.configureByFile("$name.md")

    val intention = myFixture.availableIntentions.first { it.text == "Suppress for file" }
    myFixture.launchAction(intention)
    myFixture.checkResultByFile("$name.after.md")
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String =
    super.getTestName(lowercaseFirstLetter).trimStart().replace(' ', '_')
}
