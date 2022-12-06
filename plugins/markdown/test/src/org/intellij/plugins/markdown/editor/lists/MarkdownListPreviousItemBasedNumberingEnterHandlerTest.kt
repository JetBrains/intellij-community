package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkdownListPreviousItemBasedNumberingEnterHandlerTest: LightPlatformCodeInsightTestCase() {
  @Test
  fun `test simple list`() {
    // language=Markdown
    val content = """
    1. First item<caret>
    """.trimIndent()
    // language=Markdown
    val expected = """
    1. First item
    2. <caret>
    """.trimIndent()
    doTest(content, expected)
  }

  @Test
  fun `test list with incorrect numbering`() {
    // language=Markdown
    val content = """
    1. First item
    42. Second item<caret>
    """.trimIndent()
    // language=Markdown
    val expected = """
    1. First item
    42. Second item
    43. <caret>
    """.trimIndent()
    doTest(content, expected)
  }

  @Test
  fun `test in sublist`() {
    // language=Markdown
    val content = """
    1. Some item
       1. Some item
          1. Some item
          1. Some item<caret>
       1. Some item
    1. Some item
    """.trimIndent()
    // language=Markdown
    val expected = """
    1. Some item
       1. Some item
          1. Some item
          1. Some item
          2. <caret>
       1. Some item
    1. Some item
    """.trimIndent()
    doTest(content, expected)
  }

  private fun doTest(content: String, expected: String) {
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByText(expected)
  }
}
