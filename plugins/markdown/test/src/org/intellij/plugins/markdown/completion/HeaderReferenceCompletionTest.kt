package org.intellij.plugins.markdown.completion

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/completion/headerAnchor")
class HeaderReferenceCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true)
  }

  @Test
  fun testHeader1() {
    doTest()
  }

  @Test
  fun testHeader2() {
    doTest()
  }

  @Test
  fun testInBullet() {
    doTest()
  }

  @Test
  fun testInTable() {
    myFixture.configureByText("test.md", """
      # Header One
      ## Header Two

      | column |
      |--------|
      | [link](#<caret>) |
    """.trimIndent())
    val variants = myFixture.completeBasic().orEmpty().map { it.lookupString }
    assertThat(variants).contains("header-one", "header-two")
  }

  @Test
  fun testMultipleHeaders() {
    myFixture.testCompletionVariants(getBeforeFileName(),
                                     "environment-variables",
                                     "how-do-i-get-set-up",
                                     "mxbezier3scalar",
                                     "plugin-list",
                                     "requirements",
                                     "what-is-this-repository-for")
  }

  private fun getBeforeFileName() = testName + ".md"

  private fun doTest() {
    myFixture.testCompletion(getBeforeFileName(), testName + "_after.md")
  }

  @Test
  fun testRelativePath() {
    myFixture.testCompletion("relativePath.md", "relativePath_after.md")
  }

  @Test
  fun testAFileHeaders1() {
    myFixture.copyFileToProject("relativePath.md", "src/relativePath.md")
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getBeforeFileName(), "src/${getBeforeFileName()}"))
    assertNotNull(myFixture.completeBasic(), "No lookup was shown, probably there was only one lookup element that was inserted automatically")
    assertThat(myFixture.lookupElementStrings).containsExactlyInAnyOrder("aFileHeaders1.md", "relativePath.md")
  }

  @Test
  fun testAFileHeaders2() {
    myFixture.configureByFile("multipleHeaders.md")
    myFixture.testCompletionVariants(getBeforeFileName(), "environment-variables",
                                     "how-do-i-get-set-up",
                                     "mxbezier3scalar",
                                     "plugin-list",
                                     "requirements",
                                     "what-is-this-repository-for")
  }

  @Test
  fun testAFileHeadersNoExtension() {
    myFixture.configureByFile("multipleHeaders.md")
    myFixture.testCompletionVariants(getBeforeFileName(), "environment-variables",
                                     "how-do-i-get-set-up",
                                     "mxbezier3scalar",
                                     "plugin-list",
                                     "requirements",
                                     "what-is-this-repository-for")
  }

  @Test
  fun testGfmLowercase() {
    myFixture.testCompletionVariants(getBeforeFileName(), "what-is-this-repository-for", "what-is-this-for")
  }

  @Test
  fun testGfmPunctuation() {
    myFixture.testCompletionVariants(getBeforeFileName(), "100-april-8-2018", "100-april-82018")
  }

  @Test
  fun testGfmSpecial() {
    myFixture.testCompletionVariants(getBeforeFileName(), "get-method", "get-call")
  }
}