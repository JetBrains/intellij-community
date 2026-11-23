package org.intellij.plugins.markdown.editor.lists.inspections

import com.intellij.markdown.backend.inspections.IncorrectListNumberingInspection
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings.ListNumberingType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/lists/inspections/incorrectNumbering")
class MarkdownIncorrectListNumberingInspectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true).trimStart().replace(' ', '_')
  }

  @Test
  fun `test problem registered in simple list`(): Unit = doTest()

  @Test
  fun `test problem registered only for incorrect items`(): Unit = doTest()

  @Test
  fun `test problem registered in sublists`(): Unit = doTest()

  @Test
  fun `test problem registered in deep sublists`(): Unit = doTest()

  @Test
  fun `test no warning for paragraph content`(): Unit = doTest()

  @Test
  fun `test no warning for list item with code block`(): Unit = doTest()

  @Test
  fun `test no warning for list starting from zero`(): Unit = doTest()

  @Test
  fun `test no warning for all ones list in sequential mode`(): Unit = doTest()

  @Test
  fun `test no warning for ordered marker inside unordered list`(): Unit = doTest()

  @Test
  fun `test no warning for ordered marker inside ordered list`(): Unit = doTest()

  @Test
  fun `test problem registered in ordered sublist of unordered list`(): Unit = doTest()

  @Test
  fun `test no warning for sequential list in ones mode`(): Unit = doTest(ListNumberingType.ONES)

  @Test
  fun `test all numbering styles accepted regardless of setting`() {
    ListNumberingType.entries.forEach(::doTest)
  }

  @Test
  fun `test no warning in previous number mode`(): Unit = doTest(ListNumberingType.PREVIOUS_NUMBER)

  private fun doTest(listNumberingType: ListNumberingType = ListNumberingType.SEQUENTIAL) {
    val settings = MarkdownCodeInsightSettings.getInstance()
    val previousListNumberingType = settings.state.listNumberingType
    settings.state.listNumberingType = listNumberingType
    try {
      val name = testName
      myFixture.configureByFile("$name.md")
      myFixture.enableInspections(IncorrectListNumberingInspection())
      myFixture.checkHighlighting()
    } finally {
      settings.state.listNumberingType = previousListNumberingType
    }
  }
}
