// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.application.options.codeStyle.properties.LanguageCodeStylePropertyMapper
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/formatter")
class MarkdownFormatterTest {
  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true).trimStart().replace(' ', '_')
  }

  @Test
  fun `test smoke`(): Unit = doTest()

  @Test
  fun `test blank line settings use correct external names`() {
    val settings = CodeStyle.createTestSettings(CodeStyleSettings.getDefaults())
    settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
      MAX_LINES_AROUND_HEADER = 2
      MIN_LINES_AROUND_HEADER = 3
      MAX_LINES_AROUND_BLOCK_ELEMENTS = 4
      MIN_LINES_AROUND_BLOCK_ELEMENTS = 5
      MAX_LINES_BETWEEN_PARAGRAPHS = 6
      MIN_LINES_BETWEEN_PARAGRAPHS = 7
    }
    val mapper = LanguageCodeStylePropertyMapper(settings, MarkdownLanguage.INSTANCE, null)
    mapOf(
      "min_lines_around_header" to 2,
      "max_lines_around_header" to 3,
      "min_lines_around_block_elements" to 4,
      "max_lines_around_block_elements" to 5,
      "min_lines_between_paragraphs" to 6,
      "max_lines_between_paragraphs" to 7,
    ).forEach { (propertyName, value) ->
      assertEquals(value.toString(), mapper.getAccessor(propertyName)!!.getAsString())
    }
  }

  @Test
  fun `test headers`(): Unit = doTest()

  @Test
  fun `test do not wrap ATX headers with codespans`(): Unit = doTest(rightMargin = 90)

  @Test
  fun `test paragraphs`(): Unit = doTest()

  @Test
  fun `test lists`(): Unit = doTest()

  @Test
  fun `test reflow list items with unbreakable first elements`(): Unit = doTest()

  @Test
  fun `test keep long unbreakable list items on one line`(): Unit = doTest(rightMargin = 80)

  @Test
  fun `test reflow after a long first list word`(): Unit = doTest(rightMargin = 80)

  @Test
  fun `test reflow list items with long words and links at default margin`(): Unit = doTest(
    rightMargin = 120,
    keepLineBreaks = true,
    singleFile = true,
  )

  //For now alignment of fence parts is not supported
  @Test
  fun `test fences`(): Unit = doTest()

  @Test
  fun `test blockquotes`(): Unit = doTest()

  @Test
  fun `test codeblocks`(): Unit = doTest()

  @Test
  fun `test html code block`(): Unit = doTest()

  @Test
  fun `test codespan`(): Unit = doTest()

  @Test
  fun `test reflow codespan`(): Unit = doTest(rightMargin = 120)

  @Test
  fun `test tables`(): Unit = doTest()

  @Test
  fun `test reflow`(): Unit = doTest()

  @Test
  fun `test long table`(): Unit = doTest()

  @Test
  fun `test punctuation`(): Unit = doTest()

  @Test
  fun `test reflow parenthesized text`(): Unit = doTest()

  @Test
  fun `test reflow short parenthesized text`(): Unit = doTest()

  @Test
  fun `test reflow short codespan parenthesized text`(): Unit = doTest()

  @Test
  fun `test reflow emphasis parenthesized text margin 80`(): Unit = doTest(rightMargin = 80)

  @Test
  fun `test reflow emphasis parenthesized text margin 60`(): Unit = doTest(rightMargin = 60)

  @Test
  fun `test reflow emphasis parenthesized text margin 40`(): Unit = doTest(rightMargin = 40)

  @Test
  fun `test reflow linked parenthesized text`(): Unit = doTest()

  @Test
  fun `test reflow opening parenthesis`(): Unit = doTest()

  @Test
  fun `test reflow closing parenthesis`(): Unit = doTest()

  @Test
  fun `test reflow no extra new lines`(): Unit = doTest(rightMargin = 80)

  @Test
  fun `test reflow no extra new lines keep line breaks margin 60`(): Unit = doTest(rightMargin = 60)

  @Test
  fun `test reflow no extra new lines keep line breaks margin 40`(): Unit = doTest(rightMargin = 40)

  @Test
  fun `test keep line breaks inside text block`(): Unit = doTest(rightMargin = 120, keepLineBreaks = true)

  @Test
  fun `test keep line breaks before inline elements`(): Unit = doTest(rightMargin = 120, keepLineBreaks = true)

  @Test
  fun `test reflow text glued to emphasis`(): Unit = doTest()

  @Test
  fun `test reflow text glued to parenthesis`(): Unit = doTest(rightMargin = 120)

  // IJPL-241496: a single long line wrapped at 100 and then reformatted at 80 must reflow cleanly,
  // not accumulate extra line breaks, when "keep line breaks inside text blocks" is disabled.
  @Test
  fun `test text block reflow after decreasing margin`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.apply {
          WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = true
          getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
            FORMAT_TABLES = false
            WRAP_TEXT_IF_LONG = true
            KEEP_LINE_BREAKS_INSIDE_TEXT_BLOCKS = false
          }
        }
        val common = settings.getCommonSettings(MarkdownLanguage.INSTANCE)
        val after = testName + "_after.md"
        myFixture.configureByFile(testName + "_before.md")
        common.RIGHT_MARGIN = 100
        performReformatting(myFixture.project, myFixture.file)
        common.RIGHT_MARGIN = 80
        performReformatting(myFixture.project, myFixture.file)
        myFixture.checkResultByFile(after)
        // reformatting again at 80 must stay stable
        performReformatting(myFixture.project, myFixture.file)
        myFixture.checkResultByFile(after)
      }
    }
  }

  @Test
  fun `test emphasis`(): Unit = doTest()

  @Test
  fun `test links without blank lines`(): Unit = doTest(rightMargin = 80)

  @Test
  fun `test blockquote with emphasis wrap`(): Unit = doTest(rightMargin = 80, insertQuoteArrows = true)

  @Test
  fun `test do not wrap text inside block quotes`(): Unit = doTest(rightMargin = 80, wrapTextInsideBlockquotes = false)

  @Test
  fun `test blockquote with list item wrap`(): Unit = doTest(rightMargin = 80, insertQuoteArrows = true)

  @Test
  fun `test blockquote with numbered list`(): Unit = doTest(rightMargin = 80, insertQuoteArrows = true)

  @Test
  fun `test blockquote wrapping does not create numbered list`(): Unit = doTest(rightMargin = 120, insertQuoteArrows = true)

  @Test
  fun `test unusual whitespace before text`(): Unit = doTest(rightMargin = 20)

  @Test
  fun `test do not wrap codespan when wrap settings disabled`(): Unit = doTest(
    rightMargin = 120,
    wrapOnTyping = false,
    wrapTextIfLong = false,
  )

  @Test
  fun `test reflow apostrophe as word boundary`(): Unit = doTest(rightMargin = 120)

  @Test
  fun `test admonitions`(): Unit = doTest()

  @Test
  fun `test admonitions with blank line`(): Unit = doTest()

  @Test
  fun `test admonitions with custom title`(): Unit = doTest()

  @Test
  fun `test admonitions multi paragraph`(): Unit = doTest()

  @Test
  fun `test admonitions collapsible`(): Unit = doTest()

  @Test
  fun `test admonitions followed by paragraph`(): Unit = doTest()

  @Test
  fun `test reflow does not split emphasis markers`(): Unit = doTest()

  @Test
  fun `tests sublists with fixed indents enabled`(): Unit = doSublistIndentationTest(useFixedIndents = true)

  @Test
  fun `tests sublists with fixed indents disabled`(): Unit = doSublistIndentationTest(useFixedIndents = false)

  @Test
  fun `test fixed two space indent keeps unordered sublist under ordered item`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).USE_FIXED_INDENTS_FOR_SUBLISTS = true
        settings.getCommonSettings(MarkdownLanguage.INSTANCE).indentOptions!!.INDENT_SIZE = 2
        myFixture.configureByText("nestedList.md", "1. foo\n   * bar\n      * baz")

        performReformatting(myFixture.project, myFixture.file)

        myFixture.checkResult("1. foo\n   * bar\n     * baz")
      }
    }
  }

  private fun doSublistIndentationTest(useFixedIndents: Boolean): Unit = timeoutRunBlocking {
    val testName = if (useFixedIndents) "sublists_with_fixed_indents_enabled" else "sublists_with_fixed_indents_disabled"
    val beforeFile = "${testName}_before.md"
    val afterFile = "${testName}_after.md"
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).USE_FIXED_INDENTS_FOR_SUBLISTS = useFixedIndents
        settings.getCommonSettings(MarkdownLanguage.INSTANCE).indentOptions!!.INDENT_SIZE = 4
        myFixture.configureByFile(beforeFile)
        performReformatting(myFixture.project, myFixture.file)
        myFixture.checkResultByFile(afterFile)
        performReformatting(myFixture.project, myFixture.file)
        myFixture.checkResultByFile(afterFile)
      }
    }
  }

  private fun doTest(
    rightMargin: Int = 40,
    keepLineBreaks: Boolean = false,
    insertQuoteArrows: Boolean = false,
    wrapOnTyping: Boolean = true,
    wrapTextIfLong: Boolean = true,
    wrapTextInsideBlockquotes: Boolean = true,
    singleFile: Boolean = false,
  ): Unit = timeoutRunBlocking {
    val before = testName + (if (singleFile) ".md" else "_before.md")
    val after = if (singleFile) before else "${testName}_after.md"
    withContext(Dispatchers.EDT) {
      runWithTemporaryStyleSettings(myFixture.project) { settings ->
        settings.apply {
          WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN = wrapOnTyping
          getCommonSettings(MarkdownLanguage.INSTANCE).apply {
            RIGHT_MARGIN = rightMargin
          }
          getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
            WRAP_TEXT_IF_LONG = wrapTextIfLong
            WRAP_TEXT_INSIDE_BLOCKQUOTES = wrapTextInsideBlockquotes
            KEEP_LINE_BREAKS_INSIDE_TEXT_BLOCKS = keepLineBreaks
            // These tests are not aware of the fact that tables can be reformatted now by TablePostFormatProcessor
            // and wrapping block quotes can be fixed be BlockQuotePostFormatProcessor
            FORMAT_TABLES = false
            INSERT_QUOTE_ARROWS_ON_WRAP = insertQuoteArrows
          }
        }
        myFixture.configureByFile(before)
        performReformatting(myFixture.project, myFixture.file)
        myFixture.checkResultByFile(after)
        //check idempotence of formatter
        performReformatting(myFixture.project, myFixture.file)
        myFixture.checkResultByFile(after)
      }
    }
  }

  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    internal fun runWithTemporaryStyleSettings(project: Project, block: (CodeStyleSettings) -> Unit) {
      val settings = CodeStyle.getSettings(project)
      CodeStyle.doWithTemporarySettings(project, settings, block)
    }

    @RequiresEdt
    internal fun performReformatting(project: Project, file: PsiFile) {
      WriteCommandAction.runWriteCommandAction(project) {
        CodeStyleManager.getInstance(project).reformatText(file, listOf(file.textRange))
      }
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
  }
}
