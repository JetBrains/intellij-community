package org.intellij.plugins.markdown.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class HeaderReferenceCompletionTest : BasePlatformTestCase() {
  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/completion/headerAnchor/"

  fun testHeader1() {
    doTest()
  }

  fun testHeader2() {
    doTest()
  }

  fun testInBullet() {
    doTest()
  }

  fun testMultipleHeaders() {
    myFixture.testCompletionVariants(getBeforeFileName(),
                                     "environment-variables",
                                     "how-do-i-get-set-up",
                                     "mxbezier3scalar",
                                     "plugin-list",
                                     "requirements",
                                     "what-is-this-repository-for")
  }

  private fun getBeforeFileName() = getTestName(true) + ".md"

  private fun doTest() {
    myFixture.testCompletion(getBeforeFileName(), getTestName(true) + "_after.md")
  }

  fun testRelativePath() {
    myFixture.testCompletion("relativePath.md", "relativePath_after.md")
  }

  fun testAFileHeaders1() {
    myFixture.configureByFile("relativePath.md")
    myFixture.testCompletionVariants(getBeforeFileName(), "aFileHeaders1.md", "relativePath.md")
  }

  fun testAFileHeaders2() {
    myFixture.configureByFile("multipleHeaders.md")
    myFixture.testCompletionVariants(getBeforeFileName(), "environment-variables",
                                     "how-do-i-get-set-up",
                                     "mxbezier3scalar",
                                     "plugin-list",
                                     "requirements",
                                     "what-is-this-repository-for")
  }

  fun testGfmLowercase() {
    myFixture.testCompletionVariants(getBeforeFileName(), "what-is-this-repository-for", "what-is-this-for")
  }

  fun testGfmPunctuation() {
    myFixture.testCompletionVariants(getBeforeFileName(), "100-april-8-2018", "100-april-82018")
  }

  fun testGfmSpecial() {
    myFixture.testCompletionVariants(getBeforeFileName(), "get-method", "-get----call")
  }


}