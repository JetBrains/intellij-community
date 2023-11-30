package org.intellij.plugins.markdown.model

import com.intellij.refactoring.suggested.startOffset
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.JUnitSoftAssertions
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.lang.psi.MarkdownRecursiveElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

@RunWith(JUnit4::class)
class ProjectLevelHeaderFindUsagesTest: BasePlatformTestCase() {
  @get:Rule
  val softAssertions = JUnitSoftAssertions()

  override fun setUp() {
    super.setUp()
    myFixture.copyDirectoryToProject("", "")
  }

  @Test
  fun `test main`() = doTest()

  @Test
  fun `test other`() = doTest()

  @Test
  fun `test inner`() = doTest("subdirectory")

  private fun doTest(subdirectory: String? = null) {
    val testName = getTestName(true)
    val contentFile = when (subdirectory) {
      null -> "$testName.md"
      else -> "$subdirectory/$testName.md"
    }
    myFixture.configureByFile(contentFile)
    val headers = collectHeaders()
    assertThat(headers).isNotEmpty
    for (header in headers) {
      val offset = header.startOffset + 1
      myFixture.editor.caretModel.primaryCaret.moveToOffset(offset)
      val usages = myFixture.testFindUsagesUsingAction(contentFile).map { it.toString() }.sorted()
      val text = usages.joinToString(separator = "\n", postfix = "\n")
      val expectedUsagesFileName = "$testName-${header.anchorText!!}.txt".lowercase()
      val expectedUsagesFilePath = Path("$testDataPath/$expectedUsagesFileName")
      softAssertions.assertThat(expectedUsagesFilePath.exists())
        .withFailMessage { "Expected file $expectedUsagesFileName does not exist. Expected full path: $expectedUsagesFilePath" }
        .isTrue
      if (expectedUsagesFilePath.exists()) {
        val expectedText = expectedUsagesFilePath.readText()
        softAssertions.assertThat(text).isEqualTo(expectedText)
      }
    }
  }

  private fun collectHeaders(): Collection<MarkdownHeader> {
    val elements = mutableListOf<MarkdownHeader>()
    val visitor = object: MarkdownRecursiveElementVisitor() {
      override fun visitHeader(header: MarkdownHeader) {
        super.visitHeader(header)
        elements.add(header)
      }
    }
    myFixture.file.accept(visitor)
    return elements
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/model/headers/usages/project"
  }
}
