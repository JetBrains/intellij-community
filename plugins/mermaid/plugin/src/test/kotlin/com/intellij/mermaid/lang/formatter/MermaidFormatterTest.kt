package com.intellij.mermaid.lang.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.mermaid.lang.MermaidBaseTestCase
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.mermaid.lang.formatter.settings.MermaidCustomCodeStyleSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings

class MermaidFormatterTest : MermaidBaseTestCase("formatter") {
  fun `test flowchart`() = doTest()

  fun `test sequence`() = doTest()

  fun `test class`() = doTest()

  fun `test state`() = doTest()

  fun `test er`() = doTest()

  fun `test journey`() = doTest()

  fun `test gantt`() = doTest()

  fun `test pie`() = doTest()

  fun `test requirement`() = doTest()

  fun `test gitgraph`() = doTest()

  fun `test c4`() = doTest()

  fun `test mindmap`() = doTest()

  fun `test timeline`() = doTest()

  fun `test quadrant`() = doTest()

  fun `test sankey`() = doTest()

  fun `test xychart`() = doTest()

  fun `test block`() = doTest()
  
  fun `test semicolon as separator`() = doTest {
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
    val before = getTestName(true) + "_before.mermaid"
    val after = getTestName(true) + "_after.mermaid"
    runWithTemporaryStyleSettings { settings ->
      settings.apply(codeStyleSettings)
      doReformatTest(before, after)
      //check idempotence of formatter
      doReformatTest(after, after)
    }
  }

  private fun runWithTemporaryStyleSettings(block: (CodeStyleSettings) -> Unit) {
    val settings = CodeStyle.getSettings(project)
    CodeStyle.doWithTemporarySettings(project, settings, block)
  }

  private fun doReformatTest(before: String, after: String) {
    myFixture.configureByFile(before)
    WriteCommandAction.runWriteCommandAction(project) {
      CodeStyleManager.getInstance(project).reformat(myFixture.file)
    }
    myFixture.checkResultByFile(after)
  }
}
