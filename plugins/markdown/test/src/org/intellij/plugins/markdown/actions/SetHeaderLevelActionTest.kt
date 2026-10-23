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

  fun `test applies header level to multiple carets`() {
    val content = "First line\n\nSecond line"
    val firstLineEnd = content.indexOf('\n')
    val secondLineStart = content.lastIndexOf('\n') + 1
    doMultiCaretTest(
      content,
      "# First line\n\n# Second line",
      SetHeaderLevelImpl.Title(),
      firstSelection = 0 until firstLineEnd,
      secondSelection = secondLineStart until content.length,
    )
  }

  fun `test removes header level from multiple carets`() {
    val content = "# First line\n\n# Second line"
    val firstLineStart = 2
    val firstLineEnd = content.indexOf('\n')
    val secondLineStart = content.lastIndexOf('\n') + 3
    doMultiCaretTest(
      content,
      "First line\n\nSecond line",
      SetHeaderLevelImpl.Normal(),
      firstSelection = firstLineStart until firstLineEnd,
      secondSelection = secondLineStart until content.length,
    )
  }

  private fun doTest(content: String, expected: String, action: AnAction) {
    configureFromFileText("some.md", content, true)
    executeCommand(project, editor.document) {
      EditorTestUtil.executeAction(editor, true, action)
    }
    checkResultByText(expected)
  }

  private fun doMultiCaretTest(
    content: String,
    expected: String,
    action: AnAction,
    firstSelection: IntRange,
    secondSelection: IntRange,
  ) {
    configureFromFileText("some.md", content)
    editor.caretModel.primaryCaret.setSelection(firstSelection.first, firstSelection.last + 1)
    val secondCaret = editor.caretModel.addCaret(editor.offsetToVisualPosition(secondSelection.first))!!
    secondCaret.setSelection(secondSelection.first, secondSelection.last + 1)
    executeCommand(project, editor.document) {
      EditorTestUtil.executeAction(editor, true, action)
    }
    assertEquals(expected, editor.document.text)
  }

  companion object {
    private inline fun executeCommand(project: Project, document: Document, crossinline block: () -> Unit) {
      CommandProcessor.getInstance().executeCommand(project, Runnable { block() }, "", null, document)
    }
  }
}
