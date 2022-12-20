package com.intellij.mermaid.lang.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.mermaid.lang.MermaidBaseTestCase
import com.intellij.mermaid.lang.MermaidLanguage
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

  private fun doTest() {
    val before = getTestName(true) + "_before.mermaid"
    val after = getTestName(true) + "_after.mermaid"
    runWithTemporaryStyleSettings { settings ->
      settings.apply {
        getLanguageIndentOptions(MermaidLanguage).apply {
          INDENT_SIZE = 2
          TAB_SIZE = 2
        }
      }
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
