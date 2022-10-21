package org.intellij.plugins.markdown.editor.tables

import com.intellij.application.options.CodeStyle
import com.intellij.idea.TestFor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RegistryKeyRule
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@TestFor(issues = ["IDEA-298828"])
class MarkdownTablePostFormatProcessorTest: LightPlatformCodeInsightTestCase() {
  @get:Rule
  val rule = RegistryKeyRule("markdown.tables.editing.support.enable", true)

  @Test
  fun `single table`() = doTest()

  @Test
  fun `multiple tables`() = doTest()

  @Test
  fun `table without end newline`() = doTest()

  private fun doTest() {
    val before = getTestName(true) + ".before.md"
    val after = getTestName(true) + ".after.md"
    runWithTemporaryStyleSettings { settings ->
      settings.apply {
        getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
          FORMAT_TABLES = true
        }
      }
      configureByFile(before)
      performReformatting()
      checkResultByFile(after)
      //check idempotence of formatter
      performReformatting()
      checkResultByFile(after)
    }
  }

  private fun runWithTemporaryStyleSettings(block: (CodeStyleSettings) -> Unit) {
    val settings = CodeStyle.getSettings(project)
    CodeStyle.doWithTemporarySettings(project, settings, block)
  }

  private fun performReformatting() {
    WriteCommandAction.runWriteCommandAction(project) {
      CodeStyleManager.getInstance(project).reformatText(file, listOf(file.textRange))
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/editor/tables/format/"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }
}
