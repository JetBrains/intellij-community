package org.intellij.plugins.markdown.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.ui.actions.styling.SetHeaderLevelImpl

class SetHeaderLevelActionTest: LightPlatformCodeInsightTestCase() {
  fun `test header with selection at file end`() {
    // language=Markdown
    val content = """
    Some first<selection> level header</selection>
    """.trimIndent()
    // language=Markdown
    val expected = """
    # Some first<selection> level header</selection>
    """.trimIndent()
    doTest(content, expected, SetHeaderLevelImpl.Title())
  }

  fun `test consistency between normal and header`() {
    // language=Markdown
    val content = """
    # Some first<selection> level header</selection>
    """.trimIndent()
    // language=Markdown
    val expected = """
    Some first<selection> level header</selection>
    """.trimIndent()
    doTest(content, expected, SetHeaderLevelImpl.Normal())
    doTest(expected, content, SetHeaderLevelImpl.Title())
  }

  fun `test header from single line in multiline paragraph`() {
    // language=Markdown
    val content = """
    Lorem ipsum dolor sit amet, consectetur adipiscing elit.
    <selection>Phasellus posuere fermentum elit placerat dapibus.</selection>
    Donec blandit dolor a mauris lacinia, ut facilisis quam vulputate.
    """.trimIndent()
    // language=Markdown
    val expected = """
    Lorem ipsum dolor sit amet, consectetur adipiscing elit.
    
    ## <selection>Phasellus posuere fermentum elit placerat dapibus.</selection>
    
    Donec blandit dolor a mauris lacinia, ut facilisis quam vulputate.
    """.trimIndent()
    doTest(content, expected, SetHeaderLevelImpl.Subtitle())
  }

  fun `test in list`() {
    // language=Markdown
    val content = """
    * First item
    * <selection>Second item</selection>
    * Third item
    """.trimIndent()
    // language=Markdown
    val expected = """
    * First item
    * ### <selection>Second item</selection>
    * Third item
    """.trimIndent()
    doTest(content, expected, SetHeaderLevelImpl.Heading(level = 3))
  }

  fun `test in sublist`() {
    // language=Markdown
    val content = """
    * First outer item
      * First inner item
      * <selection>Second inner item</selection>
      * Third inner item
    * Third outer item
    """.trimIndent()
    // language=Markdown
    val expected = """
    * First outer item
      * First inner item
      * ### <selection>Second inner item</selection>
      * Third inner item
    * Third outer item
    """.trimIndent()
    doTest(content, expected, SetHeaderLevelImpl.Heading(level = 3))
  }

  private fun doTest(content: String, expected: String, action: AnAction) {
    configureFromFileText("some.md", content, true)
    executeCommand(project, editor.document) {
      EditorTestUtil.executeAction(editor, true, action)
    }
    checkResultByText(expected)
  }

  companion object {
    private inline fun executeCommand(project: Project, document: Document, crossinline block: () -> Unit) {
      CommandProcessor.getInstance().executeCommand(project, Runnable { block() }, "", null, document)
    }
  }
}
