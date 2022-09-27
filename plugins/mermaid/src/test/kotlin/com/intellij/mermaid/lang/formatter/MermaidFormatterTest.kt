package com.intellij.mermaid.lang.formatter

import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.LightPlatformCodeInsightTestCase

class MermaidFormatterTest : LightPlatformCodeInsightTestCase() {
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

  override fun getTestDataPath(): String {
    return "src/test/resources/formatter/"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  private fun doTest() {
    val before = getTestName(true) + "_before.mymermaid"
    val after = getTestName(true) + "_after.mymermaid"
    runWithTemporaryStyleSettings { settings ->
      settings.apply {
        getLanguageIndentOptions(MermaidLanguage).apply {
          INDENT_SIZE = 2
          TAB_SIZE = 2
        }
//        getCommonSettings(MermaidLanguage).apply {
//
//        }
//        getCustomSettings(MermaidCustomCodeStyleSettings::class.java).apply {
//
//        }
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
    configureByFile(before)
    WriteCommandAction.runWriteCommandAction(project) {
      CodeStyleManager.getInstance(project).reformat(file)
    }
    checkResultByFile(after)
  }
}
