package org.intellij.plugins.markdown.reference

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableTestUtils
import org.intellij.plugins.markdown.lang.references.paths.MarkdownLinkDestinationWithSpacesInspection
import org.junit.Test

class FileLinkWithSpacesQuickFixTest: LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun `test file link with spaces`() {
    val content = "[](some file.md)"
    val after = "[](some%20file.md)"
    doTest(content, after, createFixText("some%20file.md"))
  }

  @Test
  fun `test file link with spaces and header reference`() {
    val content = "[](some file link.md#with-header-reference)"
    val after = "[](some%20file%20link.md#with-header-reference)"
    doTest(content, after, createFixText("some%20file%20link.md"))
  }

  private fun createFixText(replacement: String): String {
    return MarkdownBundle.message("markdown.link.destination.with.spaces.quick.fix.name", replacement)
  }

  private fun doTest(content: String, after: String, targetText: String) {
    TableTestUtils.runWithChangedSettings(myFixture.project) {
      myFixture.configureByText("some.md", content)
      myFixture.enableInspections(MarkdownLinkDestinationWithSpacesInspection())
      val fix = myFixture.getAllQuickFixes().find { it.text == targetText }
      assertNotNull(fix)
      myFixture.launchAction(fix!!)
      myFixture.checkResult(after)
    }
  }
}
