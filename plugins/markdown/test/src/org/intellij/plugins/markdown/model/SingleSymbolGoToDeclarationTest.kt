package org.intellij.plugins.markdown.model

import com.intellij.idea.TestFor
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.executeAction
import org.junit.jupiter.api.Test

/**
 * Verify that there is a single target to navigate to and navigation to that target works fine.
 *
 * The test will (hopefully) fail if there are multiple targets
 * (there will be no caret at expected position).
 */
@TestFor(issues = ["IDEA-306020"])
@TestApplication
class SingleSymbolGoToDeclarationTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test link label`() {
    // language=Markdown
    val content = """
    [full reference link][link<caret>]

    [link]: http://some.com
    """.trimIndent()
    // language=Markdown
    val after = """
    [full reference link][link]

    [<caret>link]: http://some.com
    """.trimIndent()
    doTest(content, after)
  }

  @Test
  fun `test collapsed link label`() {
    // language=Markdown
    val content = """
    [li<caret>nk  title][]

    [LINK title]: http://some.com
    """.trimIndent()
    // language=Markdown
    val after = """
    [link  title][]

    [<caret>LINK title]: http://some.com
    """.trimIndent()
    doTest(content, after)
  }

  @Test
  fun `test shortcut link label`() {
    // language=Markdown
    val content = """
    [li<caret>nk]

    [LINK]: http://some.com
    """.trimIndent()
    // language=Markdown
    val after = """
    [link]

    [<caret>LINK]: http://some.com
    """.trimIndent()
    doTest(content, after)
  }

  @Test
  fun `test first equivalent declaration wins`() {
    // language=Markdown
    val content = """
    [<caret>foo]

    [foo]: http://first.com
    [FOO]: http://second.com
    """.trimIndent()
    // language=Markdown
    val after = """
    [foo]

    [<caret>foo]: http://first.com
    [FOO]: http://second.com
    """.trimIndent()
    doTest(content, after)
  }

  @Test
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

  private fun doTest(content: String, after: String): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_GOTO_DECLARATION)
      myFixture.checkResult(after)
    }
  }
}
