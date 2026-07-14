package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ListItemTabHandlerAdditionalTest: LightPlatformCodeInsightTestCase() {
  /**
   * Checks that list item is indented with 2 tabs (4 spaces) after pressing tab 2 times.
   */
  @Test
  fun `test multiple tabs are inserted correctly`() {
    // language=Markdown
    val content = """
    * Some list item
    <caret>* Some other item
    * Some
    """.trimIndent()
    // language=Markdown
    val expected = """
    * Some list item
        <caret>* Some other item
    * Some
    """.trimIndent()
    configureFromFileText("some.md", content)
    repeat(2) {
      executeAction(IdeActions.ACTION_EDITOR_TAB)
    }
    checkResultByText(expected)
  }

  @Test
  fun `test tab inside fenced code block within list item does not shift the list item`() {
    // language=Markdown
    val content = """
    * First item
    * Second item
      ```
      <caret>code
      ```
    """.trimIndent()
    // language=Markdown
    val expected = """
    * First item
    * Second item
      ```
        <caret>code
      ```
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test tab inside indented code block within list item does not shift the list item`() {
    // language=Markdown
    val content = """
    * First item
    * Second item

          <caret>code
    """.trimIndent()
    // language=Markdown
    val expected = """
    * First item
    * Second item

            <caret>code
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test tab inside table cell within list item does not shift the list item`() {
    // language=Markdown
    val content = """
    * First item
    * Second item
      | h1 | h2 |
      |----|----|
      | <caret>a  | b  |
    """.trimIndent()
    // language=Markdown
    val expected = """
    * First item
    * Second item
      | h1 | h2 |
      |----|----|
      |     <caret>a  | b  |
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test tab inside blockquote within list item does not shift the list item`() {
    // language=Markdown
    val content = """
    * First item
    * Second item
      > <caret>quoted
    """.trimIndent()
    // language=Markdown
    val expected = """
    * First item
    * Second item
      >     <caret>quoted
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }
}
