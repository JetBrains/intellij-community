package org.intellij.plugins.markdown.editor.tables

import com.intellij.application.options.CodeStyle
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.editor.MarkdownCodeInsightSettingsRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@Suppress("MarkdownIncorrectTableFormatting", "MarkdownNoTableBorders")
class TableCellNavigationWithDisabledSmartKeysTest: LightPlatformCodeInsightTestCase() {
  @get:Rule
  val rule = MarkdownCodeInsightSettingsRule { it.copy(reformatTablesOnType = false, useTableCellNavigation = false) }

  private val tabSize
    get() = CodeStyle.getSettings(file).getIndentSize(file.fileType)

  private val tab
    get() = " ".repeat(tabSize)

  @Test
  fun `test single tab forward`() {
    // language=Markdown
    val content = """
    | none | none |
    |------|------|
    | so<caret>me | some |
    """.trimIndent()
    configureFromFileText("some.md", content)
    // language=Markdown
    val expected = """
    | none | none |
    |------|------|
    | so$tab<caret>me | some |
    """.trimIndent()
    executeAction("EditorTab")
    checkResultByText(expected)
  }

  @Test
  fun `test single tab backward`() {
    // language=Markdown
    val content = """
    | none | none |
    |------|------|
    | some | so<caret>me |
    """.trimIndent()
    configureFromFileText("some.md", content)
    // language=Markdown
    val expected = """
    | none | none |
    |------|------|
    | some | so<caret>me |
    """.trimIndent()
    executeAction("EditorUnindentSelection")
    checkResultByText(expected)
  }
}
