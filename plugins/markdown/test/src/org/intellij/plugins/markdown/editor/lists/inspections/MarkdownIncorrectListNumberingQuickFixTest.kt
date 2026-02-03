package org.intellij.plugins.markdown.editor.lists.inspections

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkdownIncorrectListNumberingQuickFixTest: LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun `test fix in simple list`() = doTest()

  @Test
  fun `test fix in deep sublist`() = doTest()

  private fun doTest() {
    val name = getTestName(true)
    myFixture.configureByFile("$name.md")
    myFixture.enableInspections(IncorrectListNumberingInspection())
    val targetText = MarkdownBundle.message("markdown.fix.list.items.numbering,quick.fix.text")
    val fix = myFixture.getAllQuickFixes().find { it.text == targetText }
    assertNotNull(fix)
    myFixture.launchAction(fix!!)
    myFixture.checkResultByFile("$name.after.md")
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/editor/lists/inspections/incorrectNumbering/"
  }
}
