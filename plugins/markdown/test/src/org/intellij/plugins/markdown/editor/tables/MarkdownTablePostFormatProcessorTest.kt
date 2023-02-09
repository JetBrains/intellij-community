package org.intellij.plugins.markdown.editor.tables

import com.intellij.idea.TestFor
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.formatter.MarkdownFormatterTest.Companion.performReformatting
import org.intellij.plugins.markdown.formatter.MarkdownFormatterTest.Companion.runWithTemporaryStyleSettings
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
