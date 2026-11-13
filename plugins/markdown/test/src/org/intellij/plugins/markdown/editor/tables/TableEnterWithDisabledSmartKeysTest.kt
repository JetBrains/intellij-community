package org.intellij.plugins.markdown.editor.tables

import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.executeAction
import org.intellij.plugins.markdown.editor.markdownCodeInsightSettingsFixture
import org.intellij.plugins.markdown.editor.typeInEditor
import org.junit.jupiter.api.Test

@TestApplication
@Suppress("MarkdownIncorrectTableFormatting", "MarkdownNoTableBorders")
class TableEnterWithDisabledSmartKeysTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val settings = markdownCodeInsightSettingsFixture {
    it.insertHtmlLineBreakInsideTables = false
    it.insertNewTableRowOnShiftEnter = false
  }

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

  private fun doTest(content: String, expected: String, shift: Boolean = false): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      when {
        shift -> myFixture.executeAction("EditorStartNewLine")
        else -> myFixture.typeInEditor("\n")
      }
      myFixture.checkResult(expected)
    }
  }
}
