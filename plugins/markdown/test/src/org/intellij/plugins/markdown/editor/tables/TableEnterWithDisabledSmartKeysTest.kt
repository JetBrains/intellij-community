package org.intellij.plugins.markdown.editor.tables

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.editor.MarkdownCodeInsightSettingsRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@Suppress("MarkdownIncorrectTableFormatting", "MarkdownNoTableBorders")
class TableEnterWithDisabledSmartKeysTest: LightPlatformCodeInsightTestCase() {
  @get:Rule
  val rule = MarkdownCodeInsightSettingsRule { it.copy(insertHtmlLineBreakInsideTables = false, insertNewTableRowOnShiftEnter = false) }

  @Test
  fun `test single enter inside cell`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|
    | some<caret> | some |
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some
    <caret>| some |
    """.trimIndent()
    doTest(before, after)
  }

  @Test
  fun `test shift enter at the row end`() {
    // language=Markdown
    val before = """
    | none | none |
    |------|------|
    | some | some |<caret>
    """.trimIndent()
    // language=Markdown
    val after = """
    | none | none |
    |------|------|
    | some | some |
    <caret>
    """.trimIndent()
    doTest(before, after, shift = true)
  }

  private fun doTest(content: String, expected: String, shift: Boolean = false) {
    configureFromFileText("some.md", content)
    when {
      shift -> executeAction("EditorStartNewLine")
      else -> type("\n")
    }
    checkResultByText(expected)
  }
}
