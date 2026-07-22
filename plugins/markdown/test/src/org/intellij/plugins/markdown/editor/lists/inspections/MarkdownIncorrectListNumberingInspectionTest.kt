package org.intellij.plugins.markdown.editor.lists.inspections

import com.intellij.markdown.backend.inspections.IncorrectListNumberingInspection
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings.ListNumberingType
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

  @Test
  fun `test no warning for list item with code block`() = doTest()

  @Test
  fun `test no warning for list starting from zero`() = doTest()

  @Test
  fun `test warning for list starting from zero in ones mode`() = doTest(ListNumberingType.ONES)

  @Test
  fun `test no warning in previous number mode`() = doTest(ListNumberingType.PREVIOUS_NUMBER)

  private fun doTest(listNumberingType: ListNumberingType = ListNumberingType.SEQUENTIAL) {
    val settings = MarkdownCodeInsightSettings.getInstance()
    val previousListNumberingType = settings.state.listNumberingType
    settings.state.listNumberingType = listNumberingType
    try {
      val name = getTestName(true)
      myFixture.configureByFile("$name.md")
      myFixture.enableInspections(IncorrectListNumberingInspection())
      myFixture.checkHighlighting()
    } finally {
      settings.state.listNumberingType = previousListNumberingType
    }
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/editor/lists/inspections/incorrectNumbering/"
  }
}
