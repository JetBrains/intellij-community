package org.intellij.plugins.markdown.psi

import com.intellij.codeInsight.intention.impl.ConvertRelativePathToAbsoluteIntentionAction
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Test

class ConvertToAbsolutePathIntentionSanityTest : LightPlatformCodeInsightFixture4TestCase() {
  @Test
  fun `test no exceptions`() {
    myFixture.addFileToProject("some.md", "# This is some md")
    // language=Markdown
    val content = """
    [some](./<caret>some.md)
    """.trimIndent()
    // language=Markdown
    val expected = """
    [some](/some.md)
    """.trimIndent()
    myFixture.configureByText("main.md", content)
    myFixture.checkPreviewAndLaunchAction(ConvertRelativePathToAbsoluteIntentionAction())
    myFixture.checkResult(expected)
  }
}
