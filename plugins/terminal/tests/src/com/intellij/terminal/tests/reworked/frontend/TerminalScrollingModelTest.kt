package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.TerminalEditorFactory
import com.intellij.terminal.frontend.TerminalOutputScrollingModelImpl
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.update
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.updateCursor
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelImpl
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.math.ceil

@RunWith(JUnit4::class)
internal class TerminalScrollingModelTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `scroll position is on top when lines fit the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    doTest(editor, expectedScrollOffset = 0) {
      updateText(0, """
        
        
        
      """.trimIndent())
      updateText(0, """
        123
        456
        
      """.trimIndent())
      updateCursor(1, 3)
    }
  }

  @Test
  fun `scroll position follows the last non blank line`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    // two lines are hidden and there is an inset in the bottom
    val expected = TerminalUi.blockTopInset + TerminalUi.blockBottomInset + 2 * editor.lineHeight
    doTest(editor, expected) {
      updateText(0, """
        
        
        
      """.trimIndent())
      updateText(0, """
        1
        2
        
      """.trimIndent())
      updateText(2, """
        3
        4
      """.trimIndent())
      updateText(4, "5")
    }
  }

  @Test
  fun `scroll position follows the cursor`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    // the first line is hidden and there is an inset in the bottom
    val expected = TerminalUi.blockTopInset + TerminalUi.blockBottomInset + editor.lineHeight
    doTest(editor, expected) {
      updateText(0, """
        1
        2
        
        
      """.trimIndent())
      updateCursor(3, 0)
    }
  }

  @Test
  fun `scroll position follows the cursor when line is wrapped`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3, columns = 5)
    // the first line is hidden due to last line is wrapped and there is an inset in the bottom
    val expected = TerminalUi.blockTopInset + TerminalUi.blockBottomInset + editor.lineHeight
    doTest(editor, expected) {
      updateText(0, """
        1
        2
        12345
      """.trimIndent())
      updateCursor(2, 5)

      updateText(2, """
        12345678
      """.trimIndent())
      updateCursor(2, 8)
    }
  }

  @Test
  fun `scroll position follows screen top when cursor is in the middle of the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    // the first line be partially hidden but still visible due to the top inset, the last 5 lines are visible.
    val expected = editor.lineHeight
    doTest(editor, expected) {
      updateText(0, """
        1
        2
        3
      """.trimIndent())
      updateCursor(2, 1)
      updateText(3, """
        
        
        
      """.trimIndent())
    }
  }

  @Test
  fun `scroll position doesn't take into account cursor when it is not visible`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    // the first line is partially hidden to make the last 5 lines visible
    val expected = editor.lineHeight
    doTest(editor, expected, showCursor = false) {
      updateText(0, """
        1
        2
        3
      """.trimIndent())
      updateCursor(2, 1)
      updateText(3, """
        
        
        
      """.trimIndent())
      updateCursor(5, 0)
    }
  }

  @Test
  fun `scroll position is on top after Ctrl+L in the top of the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    // the first line be partially visible due to the top inset.
    val expected = 1 * editor.lineHeight
    doTest(editor, expected) {
      // prepare: fill the screen
      updateText(0, """
        prompt> pwd
        
        
        
        
      """.trimIndent())
      updateCursor(0, 11)

      // Ctrl+L will first replace the current line and add the new line
      updateText(0, """
        prompt> pwd
        
        
        
        
        
      """.trimIndent())
      updateCursor(0, 11)

      // Then it will print the new prompt on a new line
      updateText(1, """
        prompt> 
        
        
        
        
      """.trimIndent())
      updateCursor(1, 8)

      // Then it will print the command
      updateText(1, """
        prompt> pwd
        
        
        
        
      """.trimIndent())
      updateCursor(1, 11)
    }
  }

  @Test
  fun `scroll position is on top after Ctrl+L in the bottom of the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    // the initial 7 lines will be hidden, the 7th line will be partially visible due to the top inset.
    val expected = 7 * editor.lineHeight
    doTest(editor, expected) {
      // prepare: fill the screen
      updateText(0, """
        1
        2
        3
        4
        5
        6
        prompt> pwd
      """.trimIndent())
      updateCursor(6, 11)

      // Ctrl+L will first replace the current line and add the new lines
      updateText(6, """
        prompt> pwd
        
        
        
        
        
      """.trimIndent())
      updateCursor(6, 11)

      // Then it will print the new prompt on a new line
      updateText(7, """
        prompt> 
        
        
        
        
      """.trimIndent())
      updateCursor(7, 8)

      // Then it will print the command
      updateText(7, """
        prompt> pwd
        
        
        
        
      """.trimIndent())
      updateCursor(7, 11)
    }
  }

  @Test
  fun `scroll position is on top after Ctrl+L in the middle of the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    // the initial 3 lines will be hidden, the 3rd line will be partially visible due to the top inset.
    val expected = 3 * editor.lineHeight
    doTest(editor, expected) {
      // prepare: fill the screen
      updateText(0, """
        1
        2
        prompt> pwd
        
        
      """.trimIndent())
      updateCursor(2, 11)

      // Ctrl+L will first replace the current line and add the new line
      updateText(2, """
        prompt> pwd
        
        
        
        
        
      """.trimIndent())
      updateCursor(2, 11)

      // Then it will print the new prompt on a new line
      updateText(3, """
        prompt> 
        
        
        
        
      """.trimIndent())
      updateCursor(3, 8)

      // Then it will print the command
      updateText(3, """
        prompt> pwd
        
        
        
        
      """.trimIndent())
      updateCursor(3, 11)
    }
  }

  @Test
  fun `scroll position is on top after invoking clear in the top of the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    doTest(editor, expectedScrollOffset = 0) {
      // prepare: fill the screen
      updateText(0, """
        prompt> clear
        
        
      """.trimIndent())
      updateCursor(0, 13)

      // "clear" first replaces all lines with empty
      updateText(0, """
        
        
        
      """.trimIndent())
      updateCursor(0, 0)

      // Then it will print the new prompt on the first line
      updateText(0, """
        prompt> 
        
        
      """.trimIndent())
      updateCursor(0, 8)
    }
  }

  @Test
  fun `scroll position is on top after invoking clear in the bottom of the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    doTest(editor, expectedScrollOffset = 0) {
      // prepare: fill the screen
      updateText(0, """
        1
        2
        3
        4
        5
        6
        prompt> clear
      """.trimIndent())
      updateCursor(6, 13)

      // "Clear" first adds the new line
      updateText(6, """
        prompt> clear
        
      """.trimIndent())
      updateCursor(7, 0)

      // Then it replaces everything with empty lines
      updateText(0, """
        
        
        
        
        
      """.trimIndent())
      updateCursor(0, 0)

      // Then it will print the new prompt on the first line
      updateText(0, """
        prompt> 
        
        
        
        
      """.trimIndent())
      updateCursor(0, 8)
    }
  }

  @Test
  fun `scroll position is on top after invoking clear in the middle of the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    doTest(editor, expectedScrollOffset = 0) {
      // prepare: fill the screen
      updateText(0, """
        1
        2
        prompt> clear
        
        
      """.trimIndent())
      updateCursor(2, 13)

      // "clear" first replaces all lines with empty
      updateText(0, """
        
        
        
        
        
      """.trimIndent())
      updateCursor(0, 0)

      // Then it will print the new prompt on the first line
      updateText(0, """
        prompt> 
        
        
        
        
      """.trimIndent())
      updateCursor(0, 8)
    }
  }

  @Test
  fun `scroll position doesn't go up if line in the bottom is removed`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    // The first four lines are hidden, others lines are fully visible with the bottom inset.
    val expected = TerminalUi.blockTopInset + TerminalUi.blockBottomInset + 4 * editor.lineHeight
    doTest(editor, expected) {
      // prepare: fill the screen
      updateText(0, """
        1
        2
        3
        4
        5
        6
        prompt> command
      """.trimIndent())
      updateCursor(6, 15)

      // Suppose command printed two lines
      updateText(6, """
        prompt> command
        persistentOutput
        tempOutput
      """.trimIndent())
      updateCursor(8, 10)

      // Then it removed the "tempOutput" line
      updateText(8, "")
      updateCursor(7, 16)
    }
  }


  private suspend fun CoroutineScope.doTest(
    editor: EditorEx,
    expectedScrollOffset: Int,
    showCursor: Boolean = true,
    operations: suspend ScrollingModelTestContext.() -> Unit,
  ) {
    val scrollingModelScope = childScope("TerminalOutputScrollingModel")
    try {
      val outputModel = TerminalOutputModelImpl(editor.document, maxOutputLength = 0)
      val sessionModel = createSessionModel(showCursor)
      val scrollingModel = TerminalOutputScrollingModelImpl(editor, outputModel, sessionModel, scrollingModelScope)

      val context = ScrollingModelTestContext(outputModel, scrollingModel)
      context.operations()

      val offset = editor.scrollingModel.verticalScrollOffset
      assertThat(offset)
        .overridingErrorMessage {
          val text = outputModel.document.text
          val textWithCursor = StringBuilder(text).insert(outputModel.cursorOffsetState.value, "<cursor>")
          "Expected scroll offset: ${expectedScrollOffset}, but got $offset. Output text:\n$textWithCursor"
        }
        .isEqualTo(expectedScrollOffset)
    }
    finally {
      scrollingModelScope.cancel()
    }
  }

  private fun createEditor(rows: Int, columns: Int = 20): EditorEx {
    val editor = TerminalEditorFactory.createOutputEditor(project, JBTerminalSystemSettingsProvider(), testRootDisposable)
    setTerminalEditorSize(editor, rows, columns)
    return editor
  }

  private fun setTerminalEditorSize(editor: EditorImpl, rows: Int, columns: Int) {
    val grid = editor.characterGrid ?: error("Character grid is not initialized")
    val heightInPixels = rows * editor.lineHeight
    val widthInPixels = ceil(columns * grid.charWidth).toInt()
    EditorTestUtil.setEditorVisibleSizeInPixels(editor, widthInPixels, heightInPixels)

    assertThat(grid.rows).isEqualTo(rows)
    assertThat(grid.columns).isEqualTo(columns)
  }

  private fun createSessionModel(isCursorVisible: Boolean): TerminalSessionModel {
    val sessionModel = TerminalSessionModelImpl()
    val newState = sessionModel.terminalState.value.copy(
      isShellIntegrationEnabled = true, // Scrolling model relies on shell integration presence to take into account the top inset
      isCursorVisible = isCursorVisible
    )
    sessionModel.updateTerminalState(newState)
    return sessionModel
  }

  private class ScrollingModelTestContext(
    private val outputModel: TerminalOutputModel,
    private val scrollingModel: TerminalOutputScrollingModelImpl,
  ) {
    suspend fun updateText(absoluteLineIndex: Long, text: String) {
      outputModel.update(absoluteLineIndex, text)
      scrollingModel.awaitEventProcessing()
    }

    suspend fun updateCursor(absoluteLineIndex: Long, column: Int) {
      outputModel.updateCursor(absoluteLineIndex, column)
      scrollingModel.awaitEventProcessing()
    }
  }
}