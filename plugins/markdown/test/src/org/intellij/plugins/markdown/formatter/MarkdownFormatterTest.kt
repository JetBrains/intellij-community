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

  fun `test codespan`() = doTest()

  fun `test tables`() = doTest()

  fun `test reflow`() = doTest()

  fun `test long table`() = doTest()

  fun `test punctuation`() = doTest()

  fun `test reflow parenthesized text`() = doTest()

  fun `test reflow short parenthesized text`() = doTest()

  fun `test reflow short codespan parenthesized text`() = doTest()

  fun `test reflow emphasis parenthesized text margin 80`() = doTest(rightMargin = 80)

  fun `test reflow emphasis parenthesized text margin 60`() = doTest(rightMargin = 60)

  fun `test reflow emphasis parenthesized text margin 40`() = doTest(rightMargin = 40)

  fun `test reflow linked parenthesized text`() = doTest()

  fun `test reflow opening parenthesis`() = doTest()

  fun `test reflow closing parenthesis`() = doTest()

  fun `test reflow no extra new lines`() = doTest(rightMargin = 80)

  fun `test reflow no extra new lines keep line breaks margin 80`() = doTest(rightMargin = 80, keepLineBreaks = true)

  fun `test reflow no extra new lines keep line breaks margin 60`() = doTest(rightMargin = 60, keepLineBreaks = true)

  fun `test reflow no extra new lines keep line breaks margin 40`() = doTest(rightMargin = 40, keepLineBreaks = true)

  fun `test emphasis`() = doTest()

  fun `test links without blank lines`() = doTest(rightMargin = 80)

  fun `test blockquote with emphasis wrap`() = doTest(rightMargin = 80, insertQuoteArrows = true)

  fun `test blockquote with list item wrap`() = doTest(rightMargin = 80, insertQuoteArrows = true)

  fun `test blockquote with numbered list`() = doTest(rightMargin = 80, insertQuoteArrows = true)

  fun `test non-breaking space before text`() = doTest(rightMargin = 20)

  fun `test do not wrap codespan when wrap settings disabled`() = doTest(
    rightMargin = 120,
    wrapOnTyping = false,
    wrapTextIfLong = false,
  )

  fun `test reflow apostrophe as word boundary`() = doTest(rightMargin = 120)

  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/formatter/"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  private fun doTest(
    rightMargin: Int = 40,
    keepLineBreaks: Boolean = false,
    insertQuoteArrows: Boolean = false,
    wrapOnTyping: Boolean = true,
    wrapTextIfLong: Boolean = true,
  ) {
    val before = getTestName(true) + "_before.md"
    val after = getTestName(true) + "_after.md"
    runWithTemporaryStyleSettings(project) { settings ->
      settings.apply {
        WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = wrapOnTyping
        getCommonSettings(MarkdownLanguage.INSTANCE).apply {
          RIGHT_MARGIN = rightMargin
        }
        getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
          WRAP_TEXT_IF_LONG = wrapTextIfLong
          KEEP_LINE_BREAKS_INSIDE_TEXT_BLOCKS = keepLineBreaks
          // These tests are not aware of the fact that tables can be reformatted now by TablePostFormatProcessor
          // and wrapping block quotes can be fixed be BlockQuotePostFormatProcessor
          FORMAT_TABLES = false
          INSERT_QUOTE_ARROWS_ON_WRAP = insertQuoteArrows
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
