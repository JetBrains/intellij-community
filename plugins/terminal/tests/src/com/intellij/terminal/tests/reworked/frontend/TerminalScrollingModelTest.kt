package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.actions.TerminalActionUtil
import com.intellij.terminal.frontend.view.impl.TerminalEditorFactory
import com.intellij.terminal.frontend.view.impl.TerminalOutputScrollingModel
import com.intellij.terminal.frontend.view.impl.TerminalOutputScrollingModelImpl
import com.intellij.terminal.tests.reworked.util.TerminalOutputPattern
import com.intellij.terminal.tests.reworked.util.outputPattern
import com.intellij.terminal.tests.reworked.util.updateContent
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModelImpl
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
      updateText(0, outputPattern("""


      """.trimIndent()), screenTopLine = 0)
      updateText(0, outputPattern("""
        123
        456<cursor>

      """.trimIndent()), screenTopLine = 0)
    }
  }

  @Test
  fun `scroll position follows the last non blank line`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    // two lines are hidden and there is an inset in the bottom
    val expected = TerminalUi.blockTopInset + TerminalUi.blockBottomInset + 2 * editor.lineHeight
    doTest(editor, expected) {
      updateText(0, outputPattern("""


      """.trimIndent()), screenTopLine = 0)
      updateText(0, outputPattern("""
        1
        2

      """.trimIndent()), screenTopLine = 0)
      updateText(2, outputPattern("""
        3
        4
      """.trimIndent()), screenTopLine = 1)
      updateText(4, outputPattern("5"), screenTopLine = 2)
    }
  }

  @Test
  fun `scroll position follows the cursor`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    // the first line is hidden and there is an inset in the bottom
    val expected = TerminalUi.blockTopInset + TerminalUi.blockBottomInset + editor.lineHeight
    doTest(editor, expected) {
      updateText(0, outputPattern("""
        1
        2

        <cursor>
      """.trimIndent()), screenTopLine = 1)
    }
  }

  @Test
  fun `scroll position follows the cursor when line is wrapped`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3, columns = 5)
    // the first line is hidden due to last line is wrapped and there is an inset in the bottom
    val expected = TerminalUi.blockTopInset + TerminalUi.blockBottomInset + editor.lineHeight
    doTest(editor, expected) {
      updateText(0, outputPattern("""
        1
        2
        12345<cursor>
      """.trimIndent()), screenTopLine = 0)

      updateText(2, outputPattern("""
        12345678<cursor>
      """.trimIndent()), screenTopLine = 0)
    }
  }

  @Test
  fun `scroll position follows screen top when cursor is in the middle of the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    // the first line be partially hidden but still visible due to the top inset, the last 5 lines are visible.
    val expected = editor.lineHeight
    doTest(editor, expected) {
      updateText(0, outputPattern("""
        1
        2
        3<cursor>
      """.trimIndent()), screenTopLine = 0)
      updateText(3, outputPattern("""



      """.trimIndent()), screenTopLine = 1)
    }
  }

  @Test
  fun `scroll position doesn't take into account cursor when it is not visible`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    // the first line is partially hidden to make the last 5 lines visible
    val expected = editor.lineHeight
    doTest(editor, expected, showCursor = false) {
      updateText(0, outputPattern("""
        1
        2
        3<cursor>
      """.trimIndent()), screenTopLine = 0)
      updateText(3, outputPattern("""


        <cursor>
      """.trimIndent()), screenTopLine = 1)
    }
  }

  @Test
  fun `scroll position is on top after Ctrl+L in the top of the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    // the first line be partially visible due to the top inset.
    val expected = 1 * editor.lineHeight
    doTest(editor, expected) {
      // prepare: fill the screen
      updateText(0, outputPattern("""
        prompt> pwd<cursor>




      """.trimIndent()), screenTopLine = 0)

      // Ctrl+L will first replace the current line and add the new line
      updateText(0, outputPattern("""
        prompt> pwd<cursor>





      """.trimIndent()), screenTopLine = 1)

      // Then it will print the new prompt on a new line
      updateText(1, outputPattern("""
        prompt> <cursor>




      """.trimIndent()), screenTopLine = 1)

      // Then it will print the command
      updateText(1, outputPattern("""
        prompt> pwd<cursor>




      """.trimIndent()), screenTopLine = 1)
    }
  }

  @Test
  fun `scroll position is on top after Ctrl+L in the bottom of the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    // the initial 7 lines will be hidden, the 7th line will be partially visible due to the top inset.
    val expected = 7 * editor.lineHeight
    doTest(editor, expected) {
      // prepare: fill the screen
      updateText(0, outputPattern("""
        1
        2
        3
        4
        5
        6
        prompt> pwd<cursor>
      """.trimIndent()), screenTopLine = 2)

      // Ctrl+L will first replace the current line and add the new lines
      updateText(6, outputPattern("""
        prompt> pwd<cursor>





      """.trimIndent()), screenTopLine = 7)

      // Then it will print the new prompt on a new line
      updateText(7, outputPattern("""
        prompt> <cursor>




      """.trimIndent()), screenTopLine = 7)

      // Then it will print the command
      updateText(7, outputPattern("""
        prompt> pwd<cursor>




      """.trimIndent()), screenTopLine = 7)
    }
  }

  @Test
  fun `scroll position is on top after Ctrl+L in the middle of the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    // the initial 3 lines will be hidden, the 3rd line will be partially visible due to the top inset.
    val expected = 3 * editor.lineHeight
    doTest(editor, expected) {
      // prepare: fill the screen
      updateText(0, outputPattern("""
        1
        2
        prompt> pwd<cursor>


      """.trimIndent()), screenTopLine = 0)

      // Ctrl+L will first replace the current line and add the new line
      updateText(2, outputPattern("""
        prompt> pwd<cursor>





      """.trimIndent()), screenTopLine = 3)

      // Then it will print the new prompt on a new line
      updateText(3, outputPattern("""
        prompt> <cursor>




      """.trimIndent()), screenTopLine = 3)

      // Then it will print the command
      updateText(3, outputPattern("""
        prompt> pwd<cursor>




      """.trimIndent()), screenTopLine = 3)
    }
  }

  @Test
  fun `scroll position is on top after invoking clear in the top of the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    doTest(editor, expectedScrollOffset = 0) {
      // prepare: fill the screen
      updateText(0, outputPattern("""
        prompt> clear<cursor>


      """.trimIndent()), screenTopLine = 0)

      // "clear" first replaces all lines with empty
      updateText(0, outputPattern("""
        <cursor>


      """.trimIndent()), screenTopLine = 0)

      // Then it will print the new prompt on the first line
      updateText(0, outputPattern("""
        prompt> <cursor>


      """.trimIndent()), screenTopLine = 0)
    }
  }

  @Test
  fun `scroll position is on top after invoking clear in the bottom of the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    doTest(editor, expectedScrollOffset = 0) {
      // prepare: fill the screen
      updateText(0, outputPattern("""
        1
        2
        3
        4
        5
        6
        prompt> clear<cursor>
      """.trimIndent()), screenTopLine = 2)

      // "Clear" first adds the new line
      updateText(6, outputPattern("""
        prompt> clear
        <cursor>
      """.trimIndent()), screenTopLine = 3)

      // Then it replaces everything with empty lines
      updateText(0, outputPattern("""
        <cursor>




      """.trimIndent()), screenTopLine = 0)

      // Then it will print the new prompt on the first line
      updateText(0, outputPattern("""
        prompt> <cursor>




      """.trimIndent()), screenTopLine = 0)
    }
  }

  @Test
  fun `scroll position is on top after invoking clear in the middle of the screen`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    doTest(editor, expectedScrollOffset = 0) {
      // prepare: fill the screen
      updateText(0, outputPattern("""
        1
        2
        prompt> clear<cursor>


      """.trimIndent()), screenTopLine = 0)

      // "clear" first replaces all lines with empty
      updateText(0, outputPattern("""
        <cursor>




      """.trimIndent()), screenTopLine = 0)

      // Then it will print the new prompt on the first line
      updateText(0, outputPattern("""
        prompt> <cursor>




      """.trimIndent()), screenTopLine = 0)
    }
  }

  @Test
  fun `scroll position doesn't go up if line in the bottom is removed`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 5)
    // The first four lines are hidden, others lines are fully visible with the bottom inset.
    val expected = TerminalUi.blockTopInset + TerminalUi.blockBottomInset + 4 * editor.lineHeight
    doTest(editor, expected) {
      // prepare: fill the screen
      updateText(0, outputPattern("""
        1
        2
        3
        4
        5
        6
        prompt> command<cursor>
      """.trimIndent()), screenTopLine = 2)

      // Suppose command printed two lines
      updateText(6, outputPattern("""
        prompt> command
        persistentOutput
        tempOutput<cursor>
      """.trimIndent()), screenTopLine = 4)

      // Then it removed the "tempOutput" line. The screen top ("5", line 4) is unaffected by this edit,
      // so it is not reported again here - it stays valid, exactly like a real session would leave it.
      updateText(8, outputPattern(""))
      updateCursor(7, 16)
    }
  }


  @Test
  fun `scrolling action stops following the cursor`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    // Scrolled 1 line up from the auto-followed position and snapped to that whole line's top (per-line stepping):
    // the new output below must not pull it back down.
    val expected = TerminalUi.blockTopInset + 2 * editor.lineHeight
    doTest(editor, expected) {
      updateText(0, outputPattern("""
        1
        2
        3
        4
        5
        6<cursor>
      """.trimIndent()), screenTopLine = 3)

      invokeAction("Terminal.LineUp")

      // Following stopped, so this update never triggers updateScrollPosition - no screenTop needed.
      updateText(6, outputPattern("7<cursor>"))
    }
  }

  @Test
  fun `scrolling action back at the bottom resumes following the cursor`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    // Scrolled back down to the exact bottom by the same kind of action, so following resumes for the new output.
    val expected = TerminalUi.blockTopInset + TerminalUi.blockBottomInset + 4 * editor.lineHeight
    doTest(editor, expected) {
      updateText(0, outputPattern("""
        1
        2
        3
        4
        5
        6<cursor>
      """.trimIndent()), screenTopLine = 3)

      invokeAction("Terminal.LineUp")
      invokeAction("Terminal.LineDown")

      updateText(6, outputPattern("7<cursor>"), screenTopLine = 4)
    }
  }

  @Test
  fun `viewport change without a user action doesn't stop following the cursor`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    val expected = TerminalUi.blockTopInset + TerminalUi.blockBottomInset + 4 * editor.lineHeight
    doTest(editor, expected) {
      updateText(0, outputPattern("""
        1
        2
        3
        4
        5
        6<cursor>
      """.trimIndent()), screenTopLine = 3)

      scrollWithoutUserAction(TerminalUi.blockTopInset + TerminalUi.blockBottomInset + 2 * editor.lineHeight)

      updateText(6, outputPattern("7<cursor>"), screenTopLine = 4)
    }
  }

  @Test
  fun `scrollToCursor with force resumes following the cursor`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    // Mirrors what happens when the user types while scrolled up: TerminalKeyEventsHandlerImpl calls
    // scrollToCursor(force = true) directly, and following must resume for the subsequent output too.
    val expected = TerminalUi.blockTopInset + TerminalUi.blockBottomInset + 4 * editor.lineHeight
    doTest(editor, expected) {
      updateText(0, outputPattern("""
        1
        2
        3
        4
        5
        6<cursor>
      """.trimIndent()), screenTopLine = 3)

      invokeAction("Terminal.LineUp")
      scrollToCursor(true)

      updateText(6, outputPattern("7<cursor>"), screenTopLine = 4)
    }
  }

  @Test
  fun `scrollToCursor without force keeps the scrolled-up position`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    // Contrast with the force = true test: once the user has scrolled up, a non-forced scrollToCursor must NOT snap
    // back to the bottom. Following stays off, so the position after the line-up is preserved for the later output.
    val expected = TerminalUi.blockTopInset + 2 * editor.lineHeight
    doTest(editor, expected) {
      updateText(0, outputPattern("""
        1
        2
        3
        4
        5
        6<cursor>
      """.trimIndent()), screenTopLine = 3)

      invokeAction("Terminal.LineUp")
      scrollToCursor(false)

      updateText(6, outputPattern("7<cursor>"))
    }
  }


  @Test
  fun `line stepping snaps to whole line boundaries`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    // Two line-ups from the followed bottom must land exactly on a whole line's top (no bottom inset remainder).
    val expected = TerminalUi.blockTopInset + 3 * editor.lineHeight
    doTest(editor, expected) {
      updateText(0, outputPattern("""
        1
        2
        3
        4
        5
        6
        7
        8<cursor>
      """.trimIndent()), screenTopLine = 5)

      invokeAction("Terminal.LineUp")
      invokeAction("Terminal.LineUp")
    }
  }

  @Test
  fun `line stepping up past the first line rests at the top`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    // Stepping up more times than there are hidden lines must rest exactly at the top (offset 0, revealing the top inset)
    // without over-scrolling, and following must stay off there (the new output does not pull it back down).
    doTest(editor, expectedScrollOffset = 0) {
      updateText(0, outputPattern("""
        1
        2
        3
        4
        5
        6
        7
        8<cursor>
      """.trimIndent()), screenTopLine = 5)

      repeat(10) { invokeAction("Terminal.LineUp") }

      updateText(8, outputPattern("9<cursor>"))
    }
  }

  @Test
  fun `scroll position keeps showing the same content when output is trimmed while not following`() =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val editor = createEditor(rows = 3)
      // Same setup as 'scrolling action stops following the cursor': one line up from the followed bottom lands at
      // TerminalUi.blockTopInset + 2 * editor.lineHeight there. Trimming one whole line above the viewport must then
      // shift that position up by one more line height, so the same content ("3", "4", "5") stays on screen.
      val expected = TerminalUi.blockTopInset + editor.lineHeight
      // Exactly the length of the initial fill ("1\n2\n3\n4\n5\n6", 11 chars), so appending "\n7" trims exactly "1\n":
      // one whole line removed from the top, cleanly, with nothing left over from a partially trimmed line.
      doTest(editor, expected, maxOutputLength = 11) {
        updateText(0, outputPattern("""
        1
        2
        3
        4
        5
        6<cursor>
      """.trimIndent()), screenTopLine = 3)

        // The platform only keeps the scroll position stable across this trim if the real editor caret sits past the
        // trimmed range (TerminalKeyilEventsHandlerImpl.syncEditorCaretWithModel does this on every keystroke); simulate
        // that here since this test never types into the editor.
        syncCaretWithCursor()

        invokeAction("Terminal.LineUp")
        updateText(6, outputPattern("7"))
      }
    }

  @Test
  fun `page down at the bottom resumes following the cursor`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(rows = 3)
    // Page up unsticks, paging back down to the bottom resumes following, so the later output is followed again.
    val expected = TerminalUi.blockTopInset + TerminalUi.blockBottomInset + 6 * editor.lineHeight
    doTest(editor, expected) {
      updateText(0, outputPattern("""
        1
        2
        3
        4
        5
        6
        7
        8<cursor>
      """.trimIndent()), screenTopLine = 5)

      invokeAction("Terminal.PageUp")
      invokeAction("Terminal.PageDown")

      updateText(8, outputPattern("9<cursor>"), screenTopLine = 6)
    }
  }

  @Test
  fun `scroll position is on top after Ctrl+L with Ghostty-style trimming in the top of the screen`() =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val editor = createEditor(rows = 5)
      doTest(editor, expectedScrollOffset = 0) {
        updateText(0, outputPattern("prompt> pwd<cursor>"), screenTopLine = 0)

        updateText(0, outputPattern("prompt> <cursor>"), screenTopLine = 0)
      }
    }

  @Test
  fun `scroll position is on top after Ctrl+L with Ghostty-style trimming in the middle of the screen`() =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val editor = createEditor(rows = 5)
      // Only 3 real lines exist below the top of the document, far short of the 5-row viewport: reaching this
      // scroll position at all requires the dynamic bottom padding, not just the corrected screen-top offset.
      val expected = 2 * editor.lineHeight
      doTest(editor, expected) {
        updateText(0, outputPattern("""
          1
          2
          prompt> pwd<cursor>
        """.trimIndent()), screenTopLine = 0)

        updateText(2, outputPattern("prompt> <cursor>"), screenTopLine = 2)
      }
    }

  @Test
  fun `scroll position is on top after Ctrl+L with Ghostty-style trimming in the bottom of the screen`() =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val editor = createEditor(rows = 5)
      // Under the old screenRows-count-back heuristic this reaches into the pre-Ctrl+L scrollback (lines "3".."4")
      // instead of the real new screen top, and the un-padded content is far shorter than the viewport - this
      // needs both the screenTopOffset fix and the dynamic bottom padding to land on the redrawn prompt.
      val expected = 6 * editor.lineHeight
      doTest(editor, expected) {
        updateText(0, outputPattern("""
          1
          2
          3
          4
          5
          6
          prompt> pwd<cursor>
        """.trimIndent()), screenTopLine = 2)

        updateText(6, outputPattern("prompt> <cursor>"), screenTopLine = 6)
      }
    }

  @Test
  fun `scroll padding shrinks back as real output fills the screen after a Ghostty-style Ctrl+L`() =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val editor = createEditor(rows = 5)
      // After the Ctrl+L trim below, "a".."d" fill the screen back up to exactly 5 rows (prompt + 4 lines):
      // the screen is full again, so following the bottom must resume normally with no leftover padding
      // pinning the scroll position too high.
      val expected = TerminalUi.blockTopInset + TerminalUi.blockBottomInset + 6 * editor.lineHeight
      doTest(editor, expected) {
        updateText(0, outputPattern("""
          1
          2
          3
          4
          5
          6
          prompt> pwd<cursor>
        """.trimIndent()), screenTopLine = 2)

        updateText(6, outputPattern("prompt> <cursor>"), screenTopLine = 6)

        // Real output now fills the screen back up.
        updateText(6, outputPattern("""
          prompt>
          a
          b
          c
          d<cursor>
        """.trimIndent()), screenTopLine = 6)
      }
    }

  @Test
  fun `scroll position reveals the top inset at the very first line even without shell integration`() =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val editor = createEditor(rows = 3)
      // Right after a terminal opens, isShellIntegrationEnabled starts false and flips true only once the
      // handshake completes. Without the screenTopVisualLine==0 special case in getTopInset, this state alone
      // (with isShellIntegrationEnabled=false, nothing about the actual content changing) would leave the cursor
      // touching the top border instead of revealing the platform-wide top inset that is always there regardless.
      doTest(editor, expectedScrollOffset = 0, isShellIntegrationEnabled = false) {
        updateText(0, outputPattern("prompt> <cursor>"), screenTopLine = 0)
      }
    }

  @Test
  fun `scroll position recovers to the same high-water mark after a real line-count shrink without a screen top move`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val editor = createEditor(rows = 5)
      // The first three lines are hidden, the rest fully visible with the bottom inset - the usual steady state.
      val expected = TerminalUi.blockTopInset + TerminalUi.blockBottomInset + 3 * editor.lineHeight
      doTest(editor, expected) {
        updateText(0, outputPattern("1\n2\n3\n4\n5\n6\n7\n8<cursor>"), screenTopLine = 3)
        val highWaterMark = currentScrollOffset()

        // A build tool rewrites its progress lines (e.g. bazel): the cursor briefly moves back as real content
        // shrinks by one line, without the terminal's own screen top moving (unlike Ctrl+L / Terminal.ClearBuffer,
        // this is not a reset, so lastScrollY must not be discarded here). The editor's own scrolling model can
        // still auto-clamp its live offset down as a side effect of the shrink - the dip that leaks through from
        // that, if any, must stay well under one line (nowhere near the multi-line, repeated oscillation this
        // fixes), and must fully recover once content regrows, rather than leaving any lasting drift.
        updateText(6, outputPattern("7<cursor>"))
        assertThat(highWaterMark - currentScrollOffset()).isLessThan(editor.lineHeight)

        updateText(6, outputPattern("7\n8<cursor>"))
        assertThat(currentScrollOffset()).isEqualTo(highWaterMark)
      }
    }

  private suspend fun CoroutineScope.doTest(
    editor: EditorImpl,
    expectedScrollOffset: Int,
    showCursor: Boolean = true,
    maxOutputLength: Int = 0,
    isShellIntegrationEnabled: Boolean = true,
    operations: suspend ScrollingModelTestContext.() -> Unit,
  ) {
    val scrollingModelScope = childScope("TerminalOutputScrollingModel")
    try {
      val outputModel = MutableTerminalOutputModelImpl(editor.document, maxOutputLength)
      val sessionModel = createSessionModel(showCursor, isShellIntegrationEnabled)
      val scrollingModel = TerminalOutputScrollingModelImpl(editor, outputModel, sessionModel, scrollingModelScope)
      editor.putUserData(TerminalOutputScrollingModel.KEY, scrollingModel)

      val context = ScrollingModelTestContext(editor, outputModel, scrollingModel)
      context.operations()

      val offset = editor.scrollingModel.verticalScrollOffset
      assertThat(offset)
        .overridingErrorMessage {
          val text = outputModel.document.text
          val textWithCursor = StringBuilder(text).insert((outputModel.cursorOffset - outputModel.startOffset).toInt(), "<cursor>")
          "Expected scroll offset: ${expectedScrollOffset}, but got $offset. Output text:\n$textWithCursor"
        }
        .isEqualTo(expectedScrollOffset)
    }
    finally {
      scrollingModelScope.cancel()
    }
  }

  private fun createEditor(rows: Int, columns: Int = 20): EditorImpl {
    val scope = terminalProjectScope(project).childScope("TerminalOutputEditor")
    Disposer.register(testRootDisposable) { scope.cancel() }
    val editor = TerminalEditorFactory.createOutputEditor(project, JBTerminalSystemSettingsProvider(), scope)
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

  private fun createSessionModel(isCursorVisible: Boolean, isShellIntegrationEnabled: Boolean = true): TerminalSessionModel {
    val sessionModel = TerminalSessionModelImpl()
    val newState = sessionModel.terminalState.value.copy(
      isShellIntegrationEnabled = isShellIntegrationEnabled, // Affects the top inset away from the very first line, see getTopInset
      isCursorVisible = isCursorVisible
    )
    sessionModel.updateTerminalState(newState)
    return sessionModel
  }

  private class ScrollingModelTestContext(
    private val editor: EditorEx,
    private val outputModel: MutableTerminalOutputModel,
    private val scrollingModel: TerminalOutputScrollingModelImpl,
  ) {
    suspend fun updateText(
      absoluteLineIndex: Long,
      pattern: TerminalOutputPattern,
      screenTopLine: Long? = null,
      screenTopColumn: Int = 0,
    ) {
      outputModel.updateContent(absoluteLineIndex, pattern)
      if (screenTopLine != null) {
        outputModel.updateScreenTopPosition(screenTopLine, screenTopColumn)
      }
      scrollingModel.awaitEventProcessing()
    }

    suspend fun updateCursor(
      absoluteLineIndex: Long,
      column: Int,
      screenTopLine: Long? = null,
      screenTopColumn: Int = 0,
    ) {
      outputModel.updateCursorPosition(absoluteLineIndex, column)
      if (screenTopLine != null) {
        outputModel.updateScreenTopPosition(screenTopLine, screenTopColumn)
      }
      scrollingModel.awaitEventProcessing()
    }

    fun syncCaretWithCursor() {
      editor.caretModel.moveToOffset((outputModel.cursorOffset - outputModel.startOffset).toInt())
    }

    fun invokeAction(actionId: String) {
      val action = ActionManager.getInstance().getAction(actionId)!!
      val dataContext = SimpleDataContext.getSimpleContext(TerminalActionUtil.EDITOR_KEY, editor, editor.dataContext)
      val event = TestActionEvent.createTestEvent(action, dataContext)
      ActionUtil.performAction(action, event)
    }

    fun scrollWithoutUserAction(offset: Int) {
      editor.scrollingModel.scrollVertically(offset)
    }

    fun currentScrollOffset(): Int = editor.scrollingModel.verticalScrollOffset

    fun scrollToCursor(force: Boolean) {
      scrollingModel.scrollToCursor(force)
    }
  }
}
