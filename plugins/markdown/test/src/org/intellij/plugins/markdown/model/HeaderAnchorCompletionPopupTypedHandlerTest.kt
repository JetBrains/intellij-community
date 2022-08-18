package org.intellij.plugins.markdown.model

import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase
import com.intellij.util.application
import junit.framework.TestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class HeaderAnchorCompletionPopupTypedHandlerTest: CompletionAutoPopupTestCase() {
  override fun setUp() {
    super.setUp()
    application.invokeAndWait {
      myFixture.copyDirectoryToProject("", "")
    }
  }

  @Test
  fun `test reference to own header`() {
    doTest(
      "header-in-main",
      "other-header-in-main"
    )
  }

  @Test
  fun `test reference to header in other file`() {
    doTest(
      "header-near-main",
      "other-header-near-main"
    )
  }

  @Test
  fun `test reference to header in subdirectory`() {
    doTest(
      "header-in-subdirectory",
      "other-header-in-subdirectory"
    )
  }

  private fun doTest(vararg expectedLookupString: String) {
    val testName = getTestName(true)
    myFixture.configureByFile("$testName.md")
    type("#")
    TestCase.assertNotNull(lookup)
    val elements = myFixture.lookupElementStrings ?: emptyList()
    UsefulTestCase.assertSameElements(elements, *expectedLookupString)
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/model/headers/completion"
  }
}
