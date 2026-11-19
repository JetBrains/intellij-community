package org.intellij.plugins.markdown.editor.lists

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

@TestApplication
class MarkdownListPreviousItemBasedNumberingEnterHandlerTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

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

  @Test
  fun `test in sublist with extra indent`() {
    // language=Markdown
    val content = """
    1. Top Level
        1. Nested<caret>
    """.trimIndent()
    // language=Markdown
    val expected = """
    1. Top Level
        1. Nested
        2. <caret>
    """.trimIndent()
    doTest(content, expected)
  }

  private fun doTest(content: String, expected: String): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.checkResult(expected)
    }
  }
}
