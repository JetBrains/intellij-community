package org.intellij.plugins.markdown.model

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.model.psi.headers.UnresolvedHeaderReferenceInspection

class UnresolvedHeaderReferenceInspectionTest: BasePlatformTestCase() {
  fun `test header in single file is unresolved`() = doTest()

  fun `test header in single file is resolved`() = doTest()

  fun `test headers in project are resolved`() = doTest()

  fun `test headers in project are resolved without file extensions`() = doTest()

  fun `test headers in project are unresolved`() = doTest()

  fun `test anchors in web links are ignored`() = doTest()

  fun `test header with uppercase anchor is resolved`() = doTest()

  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("", "")
  }

  private fun doTest() {
    val name = getTestName(true)
    myFixture.enableInspections(UnresolvedHeaderReferenceInspection())
    myFixture.configureByFile("$name.md")
    myFixture.testHighlighting(true, false, true)
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/model/headers/inspection/"
  }
}
