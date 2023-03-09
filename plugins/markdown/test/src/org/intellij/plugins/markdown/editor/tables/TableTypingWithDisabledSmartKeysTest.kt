package org.intellij.plugins.markdown.editor.tables

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.editor.MarkdownCodeInsightSettingsRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@Suppress("MarkdownIncorrectTableFormatting")
class TableTypingWithDisabledSmartKeysTest: LightPlatformCodeInsightTestCase() {
  // Just for resetting settings after every test
  @get:Rule
  val rule = MarkdownCodeInsightSettingsRule { it.copy(reformatTablesOnType = false) }

  @Test
  fun `test no column expand`() {
    // language=Markdown
    val content = """
    | some |
    |------|
    | <caret>     |
    """.trimIndent()
    // language=Markdown
    val expected = """
    | some |
    |------|
    | some<caret>     |
    """.trimIndent()
    configureFromFileText("some.md", content)
    type("some")
    checkResultByText(expected)
  }

  @Test
  fun `test no column shrink`() {
    // language=Markdown
    val content = """
    | some         |
    |--------------|
    | some text<caret>    |
    """.trimIndent()
    // language=Markdown
    val expected = """
    | some         |
    |--------------|
    | some tex<caret>    |
    """.trimIndent()
    configureFromFileText("some.md", content)
    backspace()
    checkResultByText(expected)
  }

  @Test
  fun `test no alignment correction`() {
    // language=Markdown
    val content = """
    | some content |
    |:------------:|
    | <caret>             |
    """.trimIndent()
    // language=Markdown
    val expected = """
    | some content |
    |:------------:|
    | some<caret>             |
    """.trimIndent()
    configureFromFileText("some.md", content)
    type("some")
    checkResultByText(expected)
  }
}
