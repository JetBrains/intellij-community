package org.intellij.plugins.markdown.reference

import com.intellij.markdown.backend.inspections.MarkdownLinkDestinationWithSpacesInspection
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.jupiter.api.Test

@TestApplication
class FileLinkWithSpacesInspectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val warningMessage by lazy { MarkdownBundle.message("markdown.link.destination.with.spaces.inspection.description") }

  @Test
  fun `test file link with spaces`() {
    val content = "[](<warning descr=\"$warningMessage\">some file.md</warning>)"
    doTest(content)
  }

  @Test
  fun `test complex file link with spaces`() {
    val content = "[](<warning descr=\"$warningMessage\">some path/some file.md</warning>)"
    doTest(content)
  }

  @Test
  fun `test file link with spaces and header reference`() {
    val content = "[](<warning descr=\"$warningMessage\">some file link.md</warning>#with-header-reference)"
    doTest(content)
  }

  @Test
  fun `test complex file link with spaces and header reference`() {
    val content = "[](<warning descr=\"$warningMessage\">some path/some file link.md</warning>#with-header-reference)"
    doTest(content)
  }

  @Test
  fun `test file link without spaces`() {
    val content = "[](some-file.md)"
    doTest(content)
  }

  @Test
  fun `test file link with spaces enclosed in angle brackets`() {
    val content = "[Target](<markdowns/Target File.md>)"
    doTest(content)
  }

  private fun doTest(expected: String) {
    myFixture.configureByText("some.md", expected)
    myFixture.enableInspections(MarkdownLinkDestinationWithSpacesInspection())
    myFixture.checkHighlighting()
  }
}
