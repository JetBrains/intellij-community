package org.intellij.plugins.markdown.reference

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.references.paths.MarkdownLinkDestinationWithSpacesInspection
import org.junit.Test

class FileLinkWithSpacesInspectionTest: LightPlatformCodeInsightFixture4TestCase() {
  private val warningMessage by lazy { MarkdownBundle.message("markdown.link.destination.with.spaces.inspection.description") }

  @Test
  fun `test file link with spaces`() {
    val content = "[](<warning descr=\"$warningMessage\">some file.md</warning>)"
    doTest(content)
  }

  @Test
  fun `test file link with spaces and header reference`() {
    val content = "[](<warning descr=\"$warningMessage\">some file link.md</warning>#with-header-reference)"
    doTest(content)
  }

  @Test
  fun `test file link without spaces`() {
    val content = "[](some-file.md)"
    doTest(content)
  }

  private fun doTest(expected: String) {
    myFixture.configureByText("some.md", expected)
    myFixture.enableInspections(MarkdownLinkDestinationWithSpacesInspection())
    myFixture.checkHighlighting()
  }
}
