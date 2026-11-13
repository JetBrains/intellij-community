package org.intellij.plugins.markdown.editor.lists.inspections

import com.intellij.markdown.backend.inspections.IncorrectListNumberingInspection
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/lists/inspections/incorrectNumbering")
class MarkdownIncorrectListNumberingQuickFixTest {
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
  fun `test fix in simple list`(): Unit = doTest()

  @Test
  fun `test fix in deep sublist`(): Unit = doTest()

  @Test
  fun `test fix in unparsed sublist`() {
    doTest()
    val targetText = MarkdownBundle.message("markdown.fix.list.items.numbering,quick.fix.text")
    assertEquals(1, myFixture.getAllQuickFixes().count { it.text == targetText })
  }

  private fun doTest() {
    val name = testName
    myFixture.configureByFile("$name.md")
    myFixture.enableInspections(IncorrectListNumberingInspection())
    val targetText = MarkdownBundle.message("markdown.fix.list.items.numbering,quick.fix.text")
    val fix = myFixture.getAllQuickFixes().find { it.text == targetText }
    assertNotNull(fix)
    myFixture.launchAction(fix!!)
    myFixture.checkResultByFile("$name.after.md")
  }
}
