package org.intellij.plugins.markdown.editor.tables

import com.intellij.application.options.CodeStyle
import com.intellij.application.options.codeStyle.excludedFiles.GlobPatternDescriptor
import com.intellij.idea.TestFor
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@TestFor(issues = ["IDEA-300286"])
@Suppress("MarkdownIncorrectTableFormatting")
class TableHandlersWithFileExcludedFromFormattingTest: LightPlatformCodeInsightTestCase() {
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
    TableTestUtils.runWithChangedSettings(project) {
      configureFromFileText("some.md", content)
      runWithDisabledFormatting(file) {
        type("some")
        checkResultByText(expected)
      }
    }
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
    TableTestUtils.runWithChangedSettings(project) {
      configureFromFileText("some.md", content)
      runWithDisabledFormatting(file) {
        backspace()
        checkResultByText(expected)
      }
    }
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
    TableTestUtils.runWithChangedSettings(project) {
      configureFromFileText("some.md", content)
      runWithDisabledFormatting(file) {
        type("some")
        checkResultByText(expected)
      }
    }
  }

  private fun runWithDisabledFormatting(file: PsiFile, block: () -> Unit) {
    val settings = CodeStyle.getSettings(file)
    val old = settings.excludedFiles.descriptors.toList()
    settings.excludedFiles.addDescriptor(GlobPatternDescriptor("*.md"))
    try {
      block.invoke()
    }
    finally {
      settings.excludedFiles.apply {
        clear()
        old.forEach(this::addDescriptor)
      }
    }
  }
}
