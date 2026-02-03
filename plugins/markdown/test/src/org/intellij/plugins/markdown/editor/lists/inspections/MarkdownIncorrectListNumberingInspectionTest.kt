package org.intellij.plugins.markdown.editor.lists.inspections

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkdownIncorrectListNumberingInspectionTest: LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun `test problem registered in simple list`() = doTest()

  @Test
  fun `test problem registered only for incorrect items`() = doTest()

  @Test
  fun `test problem registered in sublists`() = doTest()

  @Test
  fun `test problem registered in deep sublists`() = doTest()

  private fun doTest() {
    val name = getTestName(true)
    myFixture.configureByFile("$name.md")
    myFixture.enableInspections(IncorrectListNumberingInspection())
    myFixture.checkHighlighting()
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/editor/lists/inspections/incorrectNumbering/"
  }
}
