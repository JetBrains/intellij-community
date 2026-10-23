package org.intellij.plugins.markdown.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.ui.actions.styling.CreateOrChangeListActionGroup

class CreateOrChangeListActionTest: LightPlatformCodeInsightTestCase() {
  fun `test creates unordered lists for multiple carets`() {
    val content = "First line\n\nSecond line"
    val firstLineEnd = content.indexOf('\n')
    val secondLineStart = content.lastIndexOf('\n') + 1
    doMultiCaretTest(
      content,
      "* First line\n\n* Second line",
      CreateOrChangeListActionGroup.UnorderedList(),
      firstSelection = 0 until firstLineEnd,
      secondSelection = secondLineStart until content.length,
    )
  }

  fun `test removes unordered lists for multiple carets`() {
    val content = "* First line\n\n* Second line"
    val firstLineEnd = content.indexOf('\n')
    val secondLineStart = content.lastIndexOf('\n') + 3
    doMultiCaretTest(
      content,
      "First line\n\nSecond line",
      CreateOrChangeListActionGroup.UnorderedList(),
      firstSelection = 2 until firstLineEnd,
      secondSelection = secondLineStart until content.length,
    )
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

  private inline fun executeCommand(project: Project, document: Document, crossinline block: () -> Unit) {
    CommandProcessor.getInstance().executeCommand(project, Runnable { block() }, "", null, document)
  }
}
