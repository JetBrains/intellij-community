package org.intellij.plugins.markdown.editor.tables

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.executeAction
import org.intellij.plugins.markdown.editor.markdownCodeInsightSettingsFixture
import org.junit.jupiter.api.Test

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting", "MarkdownNoTableBorders")
class TableCellNavigationWithDisabledSmartKeysTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val settings = markdownCodeInsightSettingsFixture {
    it.reformatTablesOnType = false
    it.useTableCellNavigation = false
  }

  private val tabSize
    get() = CodeStyle.getSettings(myFixture.file).getIndentSize(myFixture.file.fileType)

  private val tab
    get() = " ".repeat(tabSize)

  @Test
  fun `test single tab forward`(): Unit = timeoutRunBlocking {
    // language=Markdown
    val content = """
    | none | none |
    |------|------|
    | so<caret>me | some |
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    // language=Markdown
    val expected = """
    | none | none |
    |------|------|
    | so$tab<caret>me | some |
    """.trimIndent()
    withContext(Dispatchers.EDT) {
      myFixture.executeAction("EditorTab")
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test single tab backward`(): Unit = timeoutRunBlocking {
    // language=Markdown
    val content = """
    | none | none |
    |------|------|
    | some | so<caret>me |
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    // language=Markdown
    val expected = """
    | none | none |
    |------|------|
    | some | so<caret>me |
    """.trimIndent()
    withContext(Dispatchers.EDT) {
      myFixture.executeAction("EditorUnindentSelection")
      myFixture.checkResult(expected)
    }
  }
}
