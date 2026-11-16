package org.intellij.plugins.markdown.actions

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.formatter.settings.OrderedListMarker
import org.intellij.plugins.markdown.lang.formatter.settings.UnorderedListMarker
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

  fun `test creates unordered list with asterisk by default`() {
    doSelectionTest("First\nSecond", "* First\n* Second", CreateOrChangeListActionGroup.UnorderedList())
  }

  fun `test creates unordered list with forced dash`() {
    doSelectionTest(
      "First\nSecond",
      "- First\n- Second",
      CreateOrChangeListActionGroup.UnorderedList(),
      unordered = UnorderedListMarker.DASH,
    )
  }

  fun `test creates ordered list with dot by default`() {
    doSelectionTest("First\nSecond", "1. First\n2. Second", CreateOrChangeListActionGroup.OrderedList())
  }

  fun `test creates ordered list with forced parenthesis`() {
    doSelectionTest(
      "First\nSecond",
      "1) First\n2) Second",
      CreateOrChangeListActionGroup.OrderedList(),
      ordered = OrderedListMarker.PARENTHESIS,
    )
  }

  fun `test creates checkmark list with forced parenthesis`() {
    doSelectionTest(
      "First\nSecond",
      "1) [ ] First\n2) [ ] Second",
      CreateOrChangeListActionGroup.CheckmarkList(),
      ordered = OrderedListMarker.PARENTHESIS,
    )
  }

  fun `test converts ordered list to unordered list with forced plus`() {
    doSelectionTest(
      "1. First\n2. Second",
      "+ First\n+ Second",
      CreateOrChangeListActionGroup.UnorderedList(),
      unordered = UnorderedListMarker.PLUS,
    )
  }

  fun `test converts dot list to forced parenthesis`() {
    doSelectionTest(
      "1. First\n2. Second",
      "1) First\n2) Second",
      CreateOrChangeListActionGroup.OrderedList(),
      ordered = OrderedListMarker.PARENTHESIS,
    )
  }

  fun `test removes ordered list with forced parenthesis`() {
    doSelectionTest(
      "1) First\n2) Second",
      "First\nSecond",
      CreateOrChangeListActionGroup.OrderedList(),
      ordered = OrderedListMarker.PARENTHESIS,
    )
  }

  fun `test converts checkmark list to forced parenthesis and keeps the checkboxes`() {
    doSelectionTest(
      "1. [ ] First\n2. [x] Second",
      "1) [ ] First\n2) [x] Second",
      CreateOrChangeListActionGroup.CheckmarkList(),
      ordered = OrderedListMarker.PARENTHESIS,
    )
  }

  fun `test converts asterisk list to forced dash`() {
    doSelectionTest(
      "* First\n* Second",
      "- First\n- Second",
      CreateOrChangeListActionGroup.UnorderedList(),
      unordered = UnorderedListMarker.DASH,
    )
  }

  fun `test removes unordered list with forced dash`() {
    doSelectionTest(
      "- First\n- Second",
      "First\nSecond",
      CreateOrChangeListActionGroup.UnorderedList(),
      unordered = UnorderedListMarker.DASH,
    )
  }

  fun `test removes unordered list with dash marker`() {
    doSelectionTest("- First\n- Second", "First\nSecond", CreateOrChangeListActionGroup.UnorderedList())
  }

  fun `test removes unordered list with plus marker`() {
    doSelectionTest("+ First\n+ Second", "First\nSecond", CreateOrChangeListActionGroup.UnorderedList())
  }

  private fun doSelectionTest(
    content: String,
    expected: String,
    action: AnAction,
    unordered: UnorderedListMarker = UnorderedListMarker.KEEP,
    ordered: OrderedListMarker = OrderedListMarker.KEEP,
  ) {
    CodeStyle.doWithTemporarySettings(project, CodeStyle.getSettings(project)) { settings ->
      settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
        unorderedListMarker = unordered
        orderedListMarker = ordered
      }
      configureFromFileText("some.md", content)
      editor.caretModel.primaryCaret.setSelection(0, content.length)
      executeCommand(project, editor.document) {
        EditorTestUtil.executeAction(editor, true, action)
      }
      assertEquals(expected, editor.document.text)
    }
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
