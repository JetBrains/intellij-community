package org.intellij.plugins.markdown.actions

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.formatter.settings.OrderedListMarker
import org.intellij.plugins.markdown.lang.formatter.settings.UnorderedListMarker
import org.intellij.plugins.markdown.ui.actions.styling.CreateOrChangeListActionGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class CreateOrChangeListActionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
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

  @Test
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

  @Test
  fun `test creates unordered list with asterisk by default`() {
    doSelectionTest("First\nSecond", "* First\n* Second", CreateOrChangeListActionGroup.UnorderedList())
  }

  @Test
  fun `test creates unordered list with forced dash`() {
    doSelectionTest(
      "First\nSecond",
      "- First\n- Second",
      CreateOrChangeListActionGroup.UnorderedList(),
      unordered = UnorderedListMarker.DASH,
    )
  }

  @Test
  fun `test creates ordered list with dot by default`() {
    doSelectionTest("First\nSecond", "1. First\n2. Second", CreateOrChangeListActionGroup.OrderedList())
  }

  @Test
  fun `test creates ordered list with forced parenthesis`() {
    doSelectionTest(
      "First\nSecond",
      "1) First\n2) Second",
      CreateOrChangeListActionGroup.OrderedList(),
      ordered = OrderedListMarker.PARENTHESIS,
    )
  }

  @Test
  fun `test creates checkmark list with forced parenthesis`() {
    doSelectionTest(
      "First\nSecond",
      "1) [ ] First\n2) [ ] Second",
      CreateOrChangeListActionGroup.CheckmarkList(),
      ordered = OrderedListMarker.PARENTHESIS,
    )
  }

  @Test
  fun `test converts ordered list to unordered list with forced plus`() {
    doSelectionTest(
      "1. First\n2. Second",
      "+ First\n+ Second",
      CreateOrChangeListActionGroup.UnorderedList(),
      unordered = UnorderedListMarker.PLUS,
    )
  }

  @Test
  fun `test converts dot list to forced parenthesis`() {
    doSelectionTest(
      "1. First\n2. Second",
      "1) First\n2) Second",
      CreateOrChangeListActionGroup.OrderedList(),
      ordered = OrderedListMarker.PARENTHESIS,
    )
  }

  @Test
  fun `test removes ordered list with forced parenthesis`() {
    doSelectionTest(
      "1) First\n2) Second",
      "First\nSecond",
      CreateOrChangeListActionGroup.OrderedList(),
      ordered = OrderedListMarker.PARENTHESIS,
    )
  }

  @Test
  fun `test converts checkmark list to forced parenthesis and keeps the checkboxes`() {
    doSelectionTest(
      "1. [ ] First\n2. [x] Second",
      "1) [ ] First\n2) [x] Second",
      CreateOrChangeListActionGroup.CheckmarkList(),
      ordered = OrderedListMarker.PARENTHESIS,
    )
  }

  @Test
  fun `test converts asterisk list to forced dash`() {
    doSelectionTest(
      "* First\n* Second",
      "- First\n- Second",
      CreateOrChangeListActionGroup.UnorderedList(),
      unordered = UnorderedListMarker.DASH,
    )
  }

  @Test
  fun `test removes unordered list with forced dash`() {
    doSelectionTest(
      "- First\n- Second",
      "First\nSecond",
      CreateOrChangeListActionGroup.UnorderedList(),
      unordered = UnorderedListMarker.DASH,
    )
  }

  @Test
  fun `test removes unordered list with dash marker`() {
    doSelectionTest("- First\n- Second", "First\nSecond", CreateOrChangeListActionGroup.UnorderedList())
  }

  @Test
  fun `test removes unordered list with plus marker`() {
    doSelectionTest("+ First\n+ Second", "First\nSecond", CreateOrChangeListActionGroup.UnorderedList())
  }

  private fun doSelectionTest(
    content: String,
    expected: String,
    action: AnAction,
    unordered: UnorderedListMarker = UnorderedListMarker.KEEP,
    ordered: OrderedListMarker = OrderedListMarker.KEEP,
  ): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      CodeStyle.doWithTemporarySettings(myFixture.project, CodeStyle.getSettings(myFixture.project)) { settings ->
        settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).apply {
          unorderedListMarker = unordered
          orderedListMarker = ordered
        }
        val editor = myFixture.editor
        editor.caretModel.primaryCaret.setSelection(0, content.length)
        executeCommand(myFixture.project, editor.document) {
          EditorTestUtil.executeAction(editor, true, action)
        }
        assertEquals(expected, editor.document.text)
      }
    }
  }

  private fun doMultiCaretTest(
    content: String,
    expected: String,
    action: AnAction,
    firstSelection: IntRange,
    secondSelection: IntRange,
  ): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      val editor = myFixture.editor
      editor.caretModel.primaryCaret.setSelection(firstSelection.first, firstSelection.last + 1)
      val secondCaret = editor.caretModel.addCaret(editor.offsetToVisualPosition(secondSelection.first))!!
      secondCaret.setSelection(secondSelection.first, secondSelection.last + 1)
      executeCommand(myFixture.project, editor.document) {
        EditorTestUtil.executeAction(editor, true, action)
      }
      assertEquals(expected, editor.document.text)
    }
  }

  private inline fun executeCommand(project: Project, document: Document, crossinline block: () -> Unit) {
    CommandProcessor.getInstance().executeCommand(project, Runnable { block() }, "", null, document)
  }
}
