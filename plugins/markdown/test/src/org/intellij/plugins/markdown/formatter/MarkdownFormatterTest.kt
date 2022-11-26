// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.PlatformTestUtil
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
    runWithTemporaryStyleSettings(project) { settings ->
      settings.apply {
        WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true
        getCommonSettings(MarkdownLanguage.INSTANCE).apply {
          RIGHT_MARGIN = 40
        }
        getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
          WRAP_TEXT_IF_LONG = true
          KEEP_LINE_BREAKS_INSIDE_TEXT_BLOCKS = false
          // These tests are not aware of the fact that tables can be reformatted now by TablePostFormatProcessor
          // and wrapping block quotes can be fixed be BlockQuotePostFormatProcessor
          FORMAT_TABLES = false
          INSERT_QUOTE_ARROWS_ON_WRAP = false
        }
      }
      configureByFile(before)
      performReformatting(project, file)
      checkResultByFile(after)
      //check idempotence of formatter
      performReformatting(project, file)
      checkResultByFile(after)
    }
  }

  companion object {
    internal fun runWithTemporaryStyleSettings(project: Project, block: (CodeStyleSettings) -> Unit) {
      val settings = CodeStyle.getSettings(project)
      CodeStyle.doWithTemporarySettings(project, settings, block)
    }

    internal fun performReformatting(project: Project, file: PsiFile) {
      WriteCommandAction.runWriteCommandAction(project) {
        CodeStyleManager.getInstance(project).reformatText(file, listOf(file.textRange))
      }
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
  }
}
