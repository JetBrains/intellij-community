package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.impl.TerminalEditorFactory
import com.intellij.terminal.frontend.view.impl.TerminalWheelScrollAccumulator
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.block.ui.calculateTerminalSize
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.awt.Panel
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.math.ceil

@TestApplication
internal class TerminalWheelScrollAccumulatorTest {
  companion object {
    private val projectFixture = projectFixture()

    /**  Mirrors [com.intellij.ui.scroll.TouchScrollUtil] private constants */
    private const val TOUCH_BEGIN = 2
    private const val TOUCH_UPDATE = 3
    private const val TOUCH_END = 4

    private const val UNKNOWN_SCROLL_TYPE = 99
  }

  private val project: Project by projectFixture
  private val source = Panel()

  private fun doTest(rows: Int = 5, block: (TerminalWheelScrollAccumulator, EditorEx) -> Unit) {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val scope = terminalProjectScope(project).childScope("TerminalWheelScrollAccumulatorTest")
      try {
        val editor = TerminalEditorFactory.createOutputEditor(project, JBTerminalSystemSettingsProvider(), scope)
        val characterGrid = editor.characterGrid ?: error("Character grid is not initialized")
        val widthInPixels = ceil(20 * characterGrid.charWidth).toInt()
        EditorTestUtil.setEditorVisibleSizeInPixels(editor, widthInPixels, rows * editor.lineHeight)

        block(TerminalWheelScrollAccumulator(editor), editor)
      }
      finally {
        scope.cancel()
      }
    }
  }

  // Touch-phase scroll: checked first. TOUCH_BEGIN/TOUCH_END are pure phase markers with
  // no delta of their own; only TOUCH_UPDATE carries an already-smooth, per-frame pixel delta.

  @Test
  fun `precise trackpad deltas smaller than a row accumulate across events before emitting anything`(): Unit =
    doTest { accumulator, editor ->
      val rowHeight = editor.lineHeight
      val perEventPixels = rowHeight / 4.0

      // Four events at 1/4 of a row each stay under the threshold...
      val firstThree = (1..3).map { accumulator.consumeLines(touchUpdateEvent(pixels = perEventPixels)) }
      // ...the fourth crosses it: exactly one row, no remainder.
      val fourth = accumulator.consumeLines(touchUpdateEvent(pixels = perEventPixels))

      assertThat(firstThree).containsExactly(0, 0, 0)
      assertThat(fourth).isEqualTo(1)
    }

  @Test
  fun `precise trackpad remainder is preserved across calls and the running total matches physical distance`(): Unit =
    doTest { accumulator, editor ->
      val rowHeight = editor.lineHeight
      val perEventPixels = rowHeight / 5.0

      // 25 events at 1/5 of a row each = exactly 5 rows, regardless of how that's chopped into callbacks.
      val totalLines = (1..25).sumOf { accumulator.consumeLines(touchUpdateEvent(pixels = perEventPixels)) }

      assertThat(totalLines).isEqualTo(5)
    }

  @Test
  fun `precise trackpad deltas in the opposite direction preserve the sign`(): Unit = doTest { accumulator, editor ->
    val rowHeight = editor.lineHeight
    val perEventPixels = rowHeight / 5.0

    val totalLines = (1..15).sumOf { accumulator.consumeLines(touchUpdateEvent(pixels = -perEventPixels)) }

    assertThat(totalLines).isEqualTo(-3) // 15 * (rowHeight/5) toward the user = exactly 3 rows
  }

  @Test
  fun `touch begin and end markers contribute no delta of their own`(): Unit = doTest { accumulator, editor ->
    val rowHeight = editor.lineHeight

    // If begin/end markers were (mis)read as real deltas, this would already cross the row threshold.
    val beginLines = accumulator.consumeLines(touchPhaseEvent(scrollType = TOUCH_BEGIN, pixels = rowHeight * 100.0))
    val updateLines = accumulator.consumeLines(touchUpdateEvent(pixels = rowHeight / 4.0))
    val endLines = accumulator.consumeLines(touchPhaseEvent(scrollType = TOUCH_END, pixels = rowHeight * 100.0))

    assertThat(beginLines).isEqualTo(0)
    assertThat(updateLines).isEqualTo(0) // only a quarter-row accumulated so far, begin/end contributed nothing
    assertThat(endLines).isEqualTo(0)
  }

  // Block scroll (WHEEL_BLOCK_SCROLL): treated as one full page

  @Test
  fun `a block scroll reports exactly one page of rows`(): Unit = doTest { accumulator, editor ->
    val rows = editor.calculateTerminalSize()!!.rows

    val lines = accumulator.consumeLines(blockScrollEvent(direction = 1))

    assertThat(lines).isEqualTo(rows)
  }

  @Test
  fun `a block scroll in the opposite direction preserves the sign`(): Unit = doTest { accumulator, editor ->
    val rows = editor.calculateTerminalSize()!!.rows

    val lines = accumulator.consumeLines(blockScrollEvent(direction = -1))

    assertThat(lines).isEqualTo(-rows)
  }

  // Wheel notch (WHEEL_UNIT_SCROLL): platform-specific, mirroring JBScrollBar#getPreciseDelta.

  @Test
  @EnabledOnOs(OS.MAC)
  fun `on macOS, a wheel notch is converted via the fixed 10x pixel scale, ignoring scrollAmount`(): Unit = doTest { accumulator, editor ->
    // preciseWheelRotation defaults to wheelRotation; pick a value whose x10-pixel conversion lands on an
    // exact, known row count regardless of the real font's line height.
    val rowHeight = editor.lineHeight
    // wheelRotation == rowHeight makes 10 * wheelRotation / rowHeight cancel out to exactly 10.0, regardless
    // of the real font's line height - avoiding any integer-division truncation risk.
    val expectedLines = (10.0 * rowHeight / rowHeight).toInt()

    val lines = accumulator.consumeLines(unitScrollEvent(scrollAmount = 3, wheelRotation = rowHeight))

    assertThat(lines).isEqualTo(expectedLines)
  }

  @Test
  @EnabledOnOs(OS.MAC)
  fun `on macOS, a wheel notch in the opposite direction preserves the sign`(): Unit = doTest { accumulator, editor ->
    val rowHeight = editor.lineHeight
    val expectedLines = (10.0 * rowHeight / rowHeight).toInt()

    val lines = accumulator.consumeLines(unitScrollEvent(scrollAmount = 3, wheelRotation = -rowHeight))

    assertThat(lines).isEqualTo(-expectedLines)
  }

  @Test
  @DisabledOnOs(OS.MAC)
  fun `elsewhere, a wheel notch converts the OS lines-per-notch setting to that many rows in one call`(): Unit = doTest { accumulator, _ ->
    val lines = accumulator.consumeLines(unitScrollEvent(scrollAmount = 3, wheelRotation = 1))

    assertThat(lines).isEqualTo(3)
  }

  @Test
  @DisabledOnOs(OS.MAC)
  fun `elsewhere, a wheel notch in the opposite direction preserves the sign`(): Unit = doTest { accumulator, _ ->
    val lines = accumulator.consumeLines(unitScrollEvent(scrollAmount = 3, wheelRotation = -1))

    assertThat(lines).isEqualTo(-3)
  }

  @Test
  fun `an unrecognized scroll type contributes nothing rather than guessing`(): Unit = doTest { accumulator, _ ->
    val lines = accumulator.consumeLines(
      MouseWheelEvent(
        source, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, 0, 0, 0, 0,
        1, false, UNKNOWN_SCROLL_TYPE, 5, 1, 1.0,
      ),
    )

    assertThat(lines).isEqualTo(0)
  }

  private fun unitScrollEvent(scrollAmount: Int, wheelRotation: Int): MouseWheelEvent {
    return MouseWheelEvent(
      source, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, 0, 0,
      1, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, scrollAmount, wheelRotation,
    )
  }

  private fun blockScrollEvent(direction: Int): MouseWheelEvent {
    return MouseWheelEvent(
      source, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, 0, 0,
      1, false, MouseWheelEvent.WHEEL_BLOCK_SCROLL, 1, direction,
    )
  }

  private fun touchUpdateEvent(pixels: Double): MouseWheelEvent = touchPhaseEvent(TOUCH_UPDATE, pixels)

  private fun touchPhaseEvent(scrollType: Int, pixels: Double): MouseWheelEvent {
    val sign = if (pixels < 0) -1 else 1
    return MouseWheelEvent(
      source, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, 0, 0, 0, 0,
      1, false, scrollType, 1, sign, pixels,
    )
  }
}
