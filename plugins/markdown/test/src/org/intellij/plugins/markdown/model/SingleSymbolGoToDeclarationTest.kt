package org.intellij.plugins.markdown.model

import com.intellij.idea.TestFor
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase

/**
 * Verify that there is a single target to navigate to and navigation to that target works fine.
 *
 * The test will (hopefully) fail if there are multiple targets
 * (there will be no caret at expected position).
 */
@TestFor(issues = ["IDEA-306020"])
class SingleSymbolGoToDeclarationTest: LightPlatformCodeInsightTestCase() {
  fun `test link label`() {
    // language=Markdown
    val content = """
    [full reference link][link<caret>]

    [link]: http://some.com
    """.trimIndent()
    // language=Markdown
    val after = """
    [full reference link][link]

    <caret>[link]: http://some.com
    """.trimIndent()
    doTest(content, after)
  }

  fun `test header reference`() {
    // language=Markdown
    val content = """
    # Some header

    [link to header](#some-hea<caret>der)
    """.trimIndent()
    // language=Markdown
    val after = """
    # <caret>Some header

    [link to header](#some-header)
    """.trimIndent()
    doTest(content, after)
  }

  private fun doTest(content: String, after: String) {
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_GOTO_DECLARATION)
    checkResultByText(after)
  }
}
