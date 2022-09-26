// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings

class MarkdownFormatterTest: LightPlatformCodeInsightTestCase() {
  fun `test smoke`() = doTest()

  fun `test headers`() = doTest()

  fun `test paragraphs`() = doTest()

  fun `test lists`() = doTest()

  //For now alignment of fence parts is not supported
  fun `test fences`() = doTest()

  fun `test blockquotes`() = doTest()

  fun `test codeblocks`() = doTest()

  fun `test tables`() = doTest()

  fun `test reflow`() = doTest()

  fun `test long table`() = doTest()

  fun `test punctuation`() = doTest()

  fun `test emphasis`() = doTest()

  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/formatter/"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  private fun doTest() {
    val before = getTestName(true) + "_before.md"
    val after = getTestName(true) + "_after.md"
    runWithTemporaryStyleSettings { settings ->
      settings.apply {
        WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true
        getCommonSettings(MarkdownLanguage.INSTANCE).apply {
          RIGHT_MARGIN = 40
        }
        getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
          WRAP_TEXT_IF_LONG = true
          KEEP_LINE_BREAKS_INSIDE_TEXT_BLOCKS = false
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
    configureByFile(before)
    WriteCommandAction.runWriteCommandAction(project) {
      CodeStyleManager.getInstance(project).reformat(file)
    }
    checkResultByFile(after)
  }
}
