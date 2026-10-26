package com.intellij.mermaid.lang.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.mermaid.lang.formatter.settings.MermaidCustomCodeStyleSettings
import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class MermaidFormatterTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "formatter")

  @Test
  fun `test flowchart`(): Unit = doTest()

  @Test
  fun `test sequence`(): Unit = doTest()

  @Test
  fun `test class`(): Unit = doTest()

  @Test
  fun `test state`(): Unit = doTest()

  @Test
  fun `test er`(): Unit = doTest()

  @Test
  fun `test journey`(): Unit = doTest()

  @Test
  fun `test gantt`(): Unit = doTest()

  @Test
  fun `test pie`(): Unit = doTest()

  @Test
  fun `test requirement`(): Unit = doTest()

  @Test
  fun `test gitgraph`(): Unit = doTest()

  @Test
  fun `test c4`(): Unit = doTest()

  @Test
  fun `test mindmap`(): Unit = doTest()

  @Test
  fun `test timeline`(): Unit = doTest()

  @Test
  fun `test quadrant`(): Unit = doTest()

  @Test
  fun `test sankey`(): Unit = doTest()

  @Test
  fun `test xychart`(): Unit = doTest()

  @Test
  fun `test block`(): Unit = doTest()
  
  @Test
  fun `test semicolon as separator`(): Unit = doTest {
    getLanguageIndentOptions(MermaidLanguage).apply {
      INDENT_SIZE = 2
      TAB_SIZE = 2
      CONTINUATION_INDENT_SIZE = 4
    }
    getCustomSettings(MermaidCustomCodeStyleSettings::class.java).apply {
      KEEP_LINES_BETWEEN_OTHER_STATEMENTS = 0
      KEEP_LINES_WITHIN_STRUCTURES = 0
      MIN_LINES_BETWEEN_OTHER_STATEMENTS = 1
    }
  }

  private fun doTest() {
    doTest {
      getLanguageIndentOptions(MermaidLanguage).apply {
        INDENT_SIZE = 2
        TAB_SIZE = 2
        CONTINUATION_INDENT_SIZE = 4
      }
      getCustomSettings(MermaidCustomCodeStyleSettings::class.java).apply {
        KEEP_LINES_BETWEEN_OTHER_STATEMENTS = 1
        KEEP_LINES_WITHIN_STRUCTURES = 1
      }
    }
  }
  
  private fun doTest(codeStyleSettings: CodeStyleSettings.() -> Unit) {
    val before = mermaid.getTestName(true) + "_before.mermaid"
    val after = mermaid.getTestName(true) + "_after.mermaid"
    runWithTemporaryStyleSettings { settings ->
      settings.apply(codeStyleSettings)
      doReformatTest(before, after)
      //check idempotence of formatter
      doReformatTest(after, after)
    }
  }

  private fun runWithTemporaryStyleSettings(block: (CodeStyleSettings) -> Unit) {
    val settings = CodeStyle.getSettings(mermaid.project)
    CodeStyle.doWithTemporarySettings(mermaid.project, settings, block)
  }

  private fun doReformatTest(before: String, after: String) {
    mermaid.fixture.configureByFile(before)
    WriteCommandAction.runWriteCommandAction(mermaid.project) {
      CodeStyleManager.getInstance(mermaid.project).reformat(mermaid.fixture.file)
    }
    mermaid.fixture.checkResultByFile(after)
  }
}
