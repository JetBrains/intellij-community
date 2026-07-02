package org.intellij.plugins.markdown.editor.tables

import com.intellij.idea.TestFor
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.formatter.MarkdownFormatterTest.Companion.performReformatting
import org.intellij.plugins.markdown.formatter.MarkdownFormatterTest.Companion.runWithTemporaryStyleSettings
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@TestFor(issues = ["IDEA-298828"])
class MarkdownTablePostFormatProcessorTest: LightPlatformCodeInsightTestCase() {
  @Test
  fun `single table`() = doTest()

  @Test
  fun `multiple tables`() = doTest()

  @Test
  fun `table without end newline`() = doTest()

  @Test
  fun `chinese table test`() = doTest()

  @Test
  fun `emoji table`() = doTest()

  @Test
  fun `table with colored boxes`() = doTest()

  @Test
  fun `emoji sequence table`() = doTest()

  @Test
  fun `table inside list item`() = doTest()

  @Test
  fun `reformat table after wrapped block quote does not throw`() {
    // language=Markdown
    val text = """
      > This is a long sentence that contains formatted text at a place that will be _reformatted_ and when it is reformatted, it will be reformatted in a way that
      > is not the way it should be done.

      | one | two | three | four |
      |-----|-----|-------|------|
      | 1 | 2 | 3 | 4 |
      | 5 | 6 | 7 | 8 |
    """.trimIndent()
    runWithTemporaryStyleSettings(project) { settings ->
      settings.getCommonSettings(MarkdownLanguage.INSTANCE).RIGHT_MARGIN = 80
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
        FORMAT_TABLES = true
        INSERT_QUOTE_ARROWS_ON_WRAP = true
      }
      configureFromFileText("some.md", text)
      performReformatting(project, file)
    }
  }

  private fun doTest() {
    val before = getTestName(true) + ".before.md"
    val after = getTestName(true) + ".after.md"
    runWithTemporaryStyleSettings(project) { settings ->
      settings.apply {
        getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
          FORMAT_TABLES = true
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

  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/editor/tables/format/"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }
}
