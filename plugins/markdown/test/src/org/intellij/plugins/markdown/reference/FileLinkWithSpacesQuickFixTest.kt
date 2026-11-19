package org.intellij.plugins.markdown.reference

import com.intellij.markdown.backend.inspections.MarkdownLinkDestinationWithSpacesInspection
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.MarkdownBundle
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@TestApplication
class FileLinkWithSpacesQuickFixTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test file link with spaces`() {
    val content = "[](some file.md)"
    val after = "[](some%20file.md)"
    doTest(content, after, createFixText("some%20file.md"))
  }

  @Test
  fun `test complex file link with spaces`() {
    val content = "[](some path/some file.md)"
    val after = "[](some%20path/some%20file.md)"
    doTest(content, after, createFixText("some%20path/some%20file.md"))
  }

  @Test
  fun `test file link with spaces and header reference`() {
    val content = "[](some file link.md#with-header-reference)"
    val after = "[](some%20file%20link.md#with-header-reference)"
    doTest(content, after, createFixText("some%20file%20link.md"))
  }

  @Test
  fun `test complex file link with spaces and header reference`() {
    val content = "[](some path/some file link.md#with-header-reference)"
    val after = "[](some%20path/some%20file%20link.md#with-header-reference)"
    doTest(content, after, createFixText("some%20path/some%20file%20link.md"))
  }

  private fun createFixText(replacement: String): String {
    return MarkdownBundle.message("markdown.link.destination.with.spaces.quick.fix.name", replacement)
  }

  private fun doTest(content: String, after: String, targetText: String) {
    myFixture.configureByText("some.md", content)
    myFixture.enableInspections(MarkdownLinkDestinationWithSpacesInspection())
    val fix = myFixture.getAllQuickFixes().find { it.text == targetText }
    assertNotNull(fix)
    myFixture.launchAction(fix!!)
    myFixture.checkResult(after)
  }
}
