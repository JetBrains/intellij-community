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
}
