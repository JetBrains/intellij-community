package com.intellij.terminal.tests.reworked.frontend

import com.intellij.execution.impl.EditorTextDecorationApplier
import com.intellij.execution.impl.buildHyperlink
import com.intellij.execution.impl.createTextDecorationId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.TerminalTextSelection
import com.intellij.terminal.frontend.view.TerminalTextSelectionChangeEvent
import com.intellij.terminal.frontend.view.TerminalTextSelectionListener
import com.intellij.terminal.frontend.view.TerminalTextSelectionModel
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.terminal.tests.reworked.util.ESC
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.text
import com.intellij.terminal.tests.reworked.util.TerminalViewFixture
import com.intellij.terminal.tests.reworked.util.assertBlocksModelState
import com.intellij.terminal.tests.reworked.util.assertOutputModelState
import com.intellij.terminal.tests.reworked.util.promptFinishedOsc
import com.intellij.terminal.tests.reworked.util.promptStartedOsc
import com.intellij.terminal.tests.reworked.util.shellIntegrationInitializedOsc
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.jediterm.terminal.emulator.mouse.MouseMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.dto.KeyEventProcessingResultDto
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.EnumSource
import java.awt.Color
import java.awt.Font
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.text.Normalizer
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds

/**
 * Checks the full effect of mouse events in the terminal: whether a hyperlink is followed, whether the event
 * is reported to the [TerminalSession], whether the editor's own handling (text/rectangular selection) kicks
 * in, and whether the event ends up consumed.
 *
 * Handling order in production is hyperlinks -> mouse reporting -> editor -> prompt clicks, which handle the click after the release.
 *
 * Tests are grouped by scenario: plain events, hyperlink interactions, text selection, rectangular (block) selection, prompt clicks.
 * And, within each, by whether mouse reporting is enabled.
 */
@TestApplication
internal class TerminalMouseEventsHandlingTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project: Project by projectFixture

  /** Mouse reporting is disabled by default, matching [TerminalSession.processMouseEvent] before any app enables it. */
  private fun doTest(
    isMouseReportingEnabled: Boolean = false,
    isAlternateScreenBuffer: Boolean = false,
    block: suspend (Fixture) -> Unit,
  ): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      Fixture(project, isMouseReportingEnabled = isMouseReportingEnabled).use { fixture ->
        if (isAlternateScreenBuffer) {
          fixture.switchToAlternateScreenBuffer()
        }
        block(fixture)
      }
    }

  @Nested
  inner class PlainMouseEvents {
    /** The default state: nothing here is ever reported to the running process */
    @Nested
    inner class MouseReportingDisabled {
      @Test
      fun `click places the editor caret`(): Unit = doTest { fixture ->
        fixture.setText("plain text")

        val point = fixture.pointAt(column = 4)
        val pressConsumed = fixture.press(point, InputEvent.BUTTON1_DOWN_MASK)
        val releaseConsumed = fixture.release(point, 0)

        assertThat(pressConsumed).isFalse()
        assertThat(releaseConsumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.editor.caretModel.offset).isEqualTo(4)
      }

      @Test
      fun `ctrl click places the editor caret`(): Unit = doTest { fixture ->
        // No hyperlink and no bound action here, so Ctrl/Cmd is inert: same as a plain click.
        fixture.setText("plain text")

        val point = fixture.pointAt(column = 4)
        val pressConsumed = fixture.press(point, ctrlModifierMask())
        val releaseConsumed = fixture.release(point, ctrlModifierMask())

        assertThat(pressConsumed).isFalse()
        assertThat(releaseConsumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.editor.caretModel.offset).isEqualTo(4)
      }

      @Test
      fun `move causes nothing`(): Unit = doTest { fixture ->
        fixture.setText("plain text")

        val consumed = fixture.move(fixture.pointAt(column = 4), 0)

        assertThat(consumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
      }
    }

    @Nested
    inner class MouseReportingEnabled {
      @Test
      fun `click causes nothing and is reported`(): Unit = doTest(isMouseReportingEnabled = true) { fixture ->
        fixture.setText("plain text")

        val point = fixture.pointAt(column = 4)
        val pressConsumed = fixture.press(point, InputEvent.BUTTON1_DOWN_MASK)
        val releaseConsumed = fixture.release(point, 0)

        assertThat(pressConsumed).isTrue() // both consumed by mouse reporting
        assertThat(releaseConsumed).isTrue()
        assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.RELEASED)
        assertThat(fixture.linkFollowedCount).isZero()
      }

      @Test
      fun `ctrl click causes nothing and is reported`(): Unit = doTest(isMouseReportingEnabled = true) { fixture ->
        fixture.setText("plain text")

        val point = fixture.pointAt(column = 4)
        val pressConsumed = fixture.press(point, ctrlModifierMask())
        val releaseConsumed = fixture.release(point, ctrlModifierMask())

        assertThat(pressConsumed).isTrue()
        assertThat(releaseConsumed).isTrue()
        assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.RELEASED)
        assertThat(fixture.linkFollowedCount).isZero()
      }

      @Test
      fun `move causes nothing and is reported`(): Unit = doTest(isMouseReportingEnabled = true) { fixture ->
        fixture.setText("plain text")

        val consumed = fixture.move(fixture.pointAt(column = 4), 0)

        assertThat(consumed).isTrue()
        assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.MOVED)
        assertThat(fixture.linkFollowedCount).isZero()
      }

      @Test
      fun `press-drag-release causes nothing and is reported`(): Unit = doTest(isMouseReportingEnabled = true) { fixture ->
        fixture.setText("plain text")

        val pressConsumed = fixture.press(fixture.pointAt(column = 2), InputEvent.BUTTON1_DOWN_MASK)
        val dragConsumed = fixture.drag(fixture.pointAt(column = 7), InputEvent.BUTTON1_DOWN_MASK)
        val releaseConsumed = fixture.release(fixture.pointAt(column = 7), 0)

        assertThat(pressConsumed).isTrue()
        assertThat(dragConsumed).isTrue()
        assertThat(releaseConsumed).isTrue()
        assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.DRAGGED, MouseEventKind.RELEASED)
        assertThat(fixture.linkFollowedCount).isZero()
      }
    }
  }

  /** A hyperlink is present at the interaction point. */
  @Nested
  inner class HyperlinkInteractions {
    /** The default state: what the hyperlink layer itself does is unaffected by reporting; what's left unconsumed reaches the editor instead of being reported. */
    @Nested
    inner class MouseReportingDisabled {
      @Test
      fun `click on visible hyperlink opens it and places the caret`(): Unit = doTest { fixture ->
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)
        fixture.hover(linkPoint)
        fixture.resetEffects()

        val pressConsumed = fixture.press(linkPoint, InputEvent.BUTTON1_DOWN_MASK)
        val releaseConsumed = fixture.release(linkPoint, 0)

        // Dual action: the link opens locally, and - unconsumed by the hyperlink layer - the click also
        // reaches the editor, which places the caret exactly as a plain click would.
        assertThat(pressConsumed).isFalse()
        assertThat(releaseConsumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.linkFollowedCount).isEqualTo(1)
        assertThat(fixture.editor.caretModel.offset).isEqualTo(7)
      }

      @Test
      fun `click on invisible hyperlink does not open it, but places the caret`(): Unit = doTest { fixture ->
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)
        fixture.hover(linkPoint)
        fixture.resetEffects()

        val pressConsumed = fixture.press(linkPoint, InputEvent.BUTTON1_DOWN_MASK)
        val releaseConsumed = fixture.release(linkPoint, 0)

        // A plain click only hints that Ctrl/Cmd-click opens an invisible link; it doesn't open it itself,
        // and the unconsumed click reaches the editor, which places the caret as usual.
        assertThat(pressConsumed).isFalse()
        assertThat(releaseConsumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.linkFollowedCount).isZero()
        assertThat(fixture.editor.caretModel.offset).isEqualTo(7)
      }

      @Test
      fun `ctrl click on visible hyperlink opens it`(): Unit = doTest { fixture ->
        // Consumed by the hyperlink layer itself, regardless of reporting - same outcome with reporting on.
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)
        fixture.hover(linkPoint)
        fixture.resetEffects()

        val pressConsumed = fixture.press(linkPoint, ctrlModifierMask())
        val releaseConsumed = fixture.release(linkPoint, ctrlModifierMask())

        assertThat(pressConsumed).isTrue()
        assertThat(releaseConsumed).isTrue()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.linkFollowedCount).isEqualTo(1)
      }

      @Test
      fun `ctrl click on invisible hyperlink opens it`(): Unit = doTest { fixture ->
        // Ctrl/Cmd bypasses the "click to open" hint and follows the link directly, consuming the event
        // pre-emptively - same outcome with reporting on.
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)
        fixture.hover(linkPoint)
        fixture.resetEffects()

        val pressConsumed = fixture.press(linkPoint, ctrlModifierMask())
        val releaseConsumed = fixture.release(linkPoint, ctrlModifierMask())

        assertThat(pressConsumed).isTrue()
        assertThat(releaseConsumed).isTrue()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.linkFollowedCount).isEqualTo(1)
      }

      @Test
      fun `move over visible hyperlink causes nothing`(): Unit = doTest { fixture ->
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)

        val consumed = fixture.move(linkPoint, 0)

        assertThat(consumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.linkFollowedCount).isZero()
      }

      @Test
      fun `move over invisible hyperlink underlines it`(): Unit = doTest { fixture ->
        // Hover styling is purely a hyperlink-layer concern - unaffected by reporting.
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)

        val consumed = fixture.move(linkPoint, 0)

        assertThat(consumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.linkFollowedCount).isZero()
        assertThat(fixture.hyperlinkTextAttributes(startOffset = 5, endOffset = 9)).isEqualTo(TEST_HOVERED_LINK_ATTRIBUTES)
      }

      @Test
      fun `ctrl move over visible hyperlink causes nothing`(): Unit = doTest { fixture ->
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)

        val consumed = fixture.move(linkPoint, ctrlModifierMask())

        assertThat(consumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.linkFollowedCount).isZero()
      }

      @Test
      fun `ctrl move over invisible hyperlink underlines it`(): Unit = doTest { fixture ->
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)

        val consumed = fixture.move(linkPoint, ctrlModifierMask())

        assertThat(consumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.linkFollowedCount).isZero()
        // Ctrl/Cmd always shows the real hyperlink color, ignoring our custom hover attributes.
        assertThat(fixture.hyperlinkTextAttributes(startOffset = 5, endOffset = 9)).isEqualTo(fixture.ctrlHoveredInvisibleLinkAttributes)
      }

      @Test
      fun `press-drag-release from a hyperlink does not open it, but selects text`(): Unit = doTest { fixture ->
        // Invisible behaves identically here: the press/release proximity check that gates hyperlink
        // handling never consults visibility, so it's not repeated for the invisible case.
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)
        val awayPoint = fixture.pointAt(column = 12) // over "here", not the link
        fixture.hover(linkPoint)
        fixture.resetEffects()

        val pressConsumed = fixture.press(linkPoint, InputEvent.BUTTON1_DOWN_MASK)
        val dragConsumed = fixture.drag(awayPoint, InputEvent.BUTTON1_DOWN_MASK)
        val releaseConsumed = fixture.release(awayPoint, 0)

        assertThat(pressConsumed).isFalse()
        assertThat(dragConsumed).isFalse()
        assertThat(releaseConsumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.linkFollowedCount).isZero()
        assertThat(fixture.editor.selectionModel.selectedText).isEqualTo("nk he") // offsets 7..12
      }

      @Test
      fun `ctrl press-drag-release from a hyperlink does not open it`(): Unit = doTest { fixture ->
        // Hover freezes on the link while a button is held (it only updates on mouseMoved), so every
        // event of the gesture is consumed pre-emptively by the hyperlink layer - before it can reach
        // the editor - yet release lands away from press, so the link is never actually opened.
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)
        val awayPoint = fixture.pointAt(column = 12) // over "here", not the link
        fixture.hover(linkPoint)
        fixture.resetEffects()

        val pressConsumed = fixture.press(linkPoint, ctrlModifierMask())
        val dragConsumed = fixture.drag(awayPoint, ctrlModifierMask())
        val releaseConsumed = fixture.release(awayPoint, ctrlModifierMask())

        assertThat(pressConsumed).isTrue()
        assertThat(dragConsumed).isTrue()
        assertThat(releaseConsumed).isTrue()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.linkFollowedCount).isZero()
        assertThat(fixture.editor.selectionModel.hasSelection()).isFalse()
      }
    }

    @Nested
    inner class MouseReportingEnabled {
      @Test
      fun `click on visible hyperlink opens it and is reported`(): Unit = doTest(isMouseReportingEnabled = true) { fixture ->
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)
        fixture.hover(linkPoint)
        fixture.resetEffects()

        val pressConsumed = fixture.press(linkPoint, InputEvent.BUTTON1_DOWN_MASK)
        val releaseConsumed = fixture.release(linkPoint, 0)

        // Dual action: the link opens locally, but the click is still fully reported to the shell.
        assertThat(pressConsumed).isTrue()
        assertThat(releaseConsumed).isTrue()
        assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.RELEASED)
        assertThat(fixture.linkFollowedCount).isEqualTo(1)
      }

      @Test
      fun `click on invisible hyperlink does not open it and is reported`(): Unit = doTest(isMouseReportingEnabled = true) { fixture ->
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)
        fixture.hover(linkPoint)
        fixture.resetEffects()

        val pressConsumed = fixture.press(linkPoint, InputEvent.BUTTON1_DOWN_MASK)
        val releaseConsumed = fixture.release(linkPoint, 0)

        // A plain click only hints that Ctrl/Cmd-click opens an invisible link; it doesn't open it itself.
        assertThat(pressConsumed).isTrue()
        assertThat(releaseConsumed).isTrue()
        assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.RELEASED)
        assertThat(fixture.linkFollowedCount).isZero()
      }

      @Test
      fun `ctrl click on visible hyperlink opens it and is not reported`(): Unit = doTest(isMouseReportingEnabled = true) { fixture ->
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)
        fixture.hover(linkPoint)
        fixture.resetEffects()

        val pressConsumed = fixture.press(linkPoint, ctrlModifierMask())
        val releaseConsumed = fixture.release(linkPoint, ctrlModifierMask())

        assertThat(pressConsumed).isTrue()
        assertThat(releaseConsumed).isTrue()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.linkFollowedCount).isEqualTo(1)
      }

      @Test
      fun `ctrl click on invisible hyperlink opens it and is not reported`(): Unit = doTest(isMouseReportingEnabled = true) { fixture ->
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)
        fixture.hover(linkPoint)
        fixture.resetEffects()

        val pressConsumed = fixture.press(linkPoint, ctrlModifierMask())
        val releaseConsumed = fixture.release(linkPoint, ctrlModifierMask())

        // Ctrl/Cmd bypasses the "click to open" hint and follows the link directly.
        assertThat(pressConsumed).isTrue()
        assertThat(releaseConsumed).isTrue()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.linkFollowedCount).isEqualTo(1)
      }

      @Test
      fun `move over visible hyperlink causes nothing and is reported`(): Unit = doTest(isMouseReportingEnabled = true) { fixture ->
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)

        val consumed = fixture.move(linkPoint, 0)

        assertThat(consumed).isTrue()
        assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.MOVED)
        assertThat(fixture.linkFollowedCount).isZero()
      }

      @Test
      fun `move over invisible hyperlink underlines it and is reported`(): Unit = doTest(isMouseReportingEnabled = true) { fixture ->
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)

        val consumed = fixture.move(linkPoint, 0)

        assertThat(consumed).isTrue()
        assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.MOVED)
        assertThat(fixture.linkFollowedCount).isZero()
        assertThat(fixture.hyperlinkTextAttributes(startOffset = 5, endOffset = 9)).isEqualTo(TEST_HOVERED_LINK_ATTRIBUTES)
      }

      @Test
      fun `ctrl move over visible hyperlink causes nothing and is reported`(): Unit = doTest(isMouseReportingEnabled = true) { fixture ->
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)

        val consumed = fixture.move(linkPoint, ctrlModifierMask())

        assertThat(consumed).isTrue()
        assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.MOVED)
        assertThat(fixture.linkFollowedCount).isZero()
      }

      @Test
      fun `ctrl move over invisible hyperlink underlines it and is reported`(): Unit = doTest(isMouseReportingEnabled = true) { fixture ->
        fixture.setText("open link here")
        val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)

        val consumed = fixture.move(linkPoint, ctrlModifierMask())

        assertThat(consumed).isTrue()
        assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.MOVED)
        assertThat(fixture.linkFollowedCount).isZero()
        // Ctrl/Cmd always shows the real hyperlink color, ignoring our custom hover attributes.
        assertThat(fixture.hyperlinkTextAttributes(startOffset = 5, endOffset = 9)).isEqualTo(fixture.ctrlHoveredInvisibleLinkAttributes)
      }

      @Test
      fun `press-drag-release from a hyperlink does not open it and is reported`(): Unit =
        doTest(isMouseReportingEnabled = true) { fixture ->
          // A plain press/drag/release must be reported in full even when it starts on a hyperlink, or
          // drag-selecting text that happens to start on a link would be invisible to the shell. Invisible
          // behaves identically here (the proximity check that gates hyperlink handling ignores visibility).
          fixture.setText("open link here")
          val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)
          val awayPoint = fixture.pointAt(column = 12) // over "here", not the link
          fixture.hover(linkPoint)
          fixture.resetEffects()

          val pressConsumed = fixture.press(linkPoint, InputEvent.BUTTON1_DOWN_MASK)
          val dragConsumed = fixture.drag(awayPoint, InputEvent.BUTTON1_DOWN_MASK)
          val releaseConsumed = fixture.release(awayPoint, 0)

          assertThat(pressConsumed).isTrue()
          assertThat(dragConsumed).isTrue()
          assertThat(releaseConsumed).isTrue()
          assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.DRAGGED, MouseEventKind.RELEASED)
          assertThat(fixture.linkFollowedCount).isZero()
        }

      @Test
      fun `ctrl press-drag-release from a hyperlink does not open it and is not reported`(): Unit =
        doTest(isMouseReportingEnabled = true) { fixture ->
          // Hover freezes on the link while a button is held (it only updates on mouseMoved), so every
          // event of the gesture is consumed pre-emptively - yet release lands away from press, so the
          // link is never actually opened.
          fixture.setText("open link here")
          val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)
          val awayPoint = fixture.pointAt(column = 12) // over "here", not the link
          fixture.hover(linkPoint)
          fixture.resetEffects()

          val pressConsumed = fixture.press(linkPoint, ctrlModifierMask())
          val dragConsumed = fixture.drag(awayPoint, ctrlModifierMask())
          val releaseConsumed = fixture.release(awayPoint, ctrlModifierMask())

          // All three consumed pre-emptively by the hyperlink layer, before mouse reporting even runs.
          assertThat(pressConsumed).isTrue()
          assertThat(dragConsumed).isTrue()
          assertThat(releaseConsumed).isTrue()
          assertThat(fixture.reportedEvents).isEmpty()
          assertThat(fixture.linkFollowedCount).isZero()
        }
    }
  }

  /** No hyperlink; a press-drag-release should select text one way or another. */
  @Nested
  inner class TextSelection {
    /** The default state: Shift is never needed to reach the editor, since nothing reports or consumes first. */
    @Nested
    inner class MouseReportingDisabled {
      @Test
      fun `press-drag-release selects text`(): Unit = doTest { fixture ->
        fixture.setText("select this text")

        val pressConsumed = fixture.press(fixture.pointAt(column = 0), InputEvent.BUTTON1_DOWN_MASK)
        val dragConsumed = fixture.drag(fixture.pointAt(column = 6), InputEvent.BUTTON1_DOWN_MASK)
        val releaseConsumed = fixture.release(fixture.pointAt(column = 6), 0)

        assertThat(pressConsumed).isFalse()
        assertThat(dragConsumed).isFalse()
        assertThat(releaseConsumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.editor.selectionModel.selectedText).isEqualTo("select")
      }

      @Test
      fun `plain click after a selection removes it`(): Unit = doTest { fixture ->
        fixture.setText("select this text")
        fixture.press(fixture.pointAt(column = 0), InputEvent.BUTTON1_DOWN_MASK)
        fixture.drag(fixture.pointAt(column = 6), InputEvent.BUTTON1_DOWN_MASK)
        fixture.release(fixture.pointAt(column = 6), 0)
        check(fixture.editor.selectionModel.hasSelection()) { "Setup failed: no selection to remove" }

        val pressConsumed = fixture.press(fixture.pointAt(column = 10), InputEvent.BUTTON1_DOWN_MASK)
        val releaseConsumed = fixture.release(fixture.pointAt(column = 10), 0)

        assertThat(pressConsumed).isFalse()
        assertThat(releaseConsumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.editor.selectionModel.hasSelection()).isFalse()
      }
    }

    /**
     * [TerminalSession.processMouseEvent] returns null for a Shift-held event (real terminal apps never see
     * Shift-clicks), so these fall through to the editor's own default text selection.
     */
    @Nested
    inner class MouseReportingEnabled {
      @Test
      fun `shift press-drag-release selects text and is not reported`(): Unit = doTest(isMouseReportingEnabled = true) { fixture ->
        fixture.setText("select this text")
        // Held throughout: EditorImpl.processMouseDragged requires the BUTTON1_DOWN_MASK bit to treat this as a drag at all.
        val shift = InputEvent.SHIFT_DOWN_MASK or InputEvent.BUTTON1_DOWN_MASK

        val pressConsumed = fixture.press(fixture.pointAt(column = 0), shift)
        val dragConsumed = fixture.drag(fixture.pointAt(column = 6), shift)
        val releaseConsumed = fixture.release(fixture.pointAt(column = 6), shift)

        assertThat(pressConsumed).isFalse()
        assertThat(dragConsumed).isFalse()
        assertThat(releaseConsumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.linkFollowedCount).isZero()
        assertThat(fixture.editor.selectionModel.selectedText).isEqualTo("select")
      }

      @Test
      fun `plain click after a shift selection removes it and is reported`(): Unit = doTest(isMouseReportingEnabled = true) { fixture ->
        fixture.setText("select this text")
        val shift = InputEvent.SHIFT_DOWN_MASK or InputEvent.BUTTON1_DOWN_MASK
        fixture.press(fixture.pointAt(column = 0), shift)
        fixture.drag(fixture.pointAt(column = 6), shift)
        fixture.release(fixture.pointAt(column = 6), shift)
        check(fixture.editor.selectionModel.hasSelection()) { "Setup failed: no selection to remove" }
        fixture.resetEffects()

        val pressConsumed = fixture.press(fixture.pointAt(column = 10), InputEvent.BUTTON1_DOWN_MASK)
        val releaseConsumed = fixture.release(fixture.pointAt(column = 10), 0)

        assertThat(pressConsumed).isTrue() // consumed by mouse reporting, same as any other plain click
        assertThat(releaseConsumed).isTrue()
        assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.RELEASED)
        assertThat(fixture.editor.selectionModel.hasSelection()).isFalse()
      }
    }
  }

  /** No hyperlink; a modified press-drag-release should create a rectangular (block) selection - one caret per spanned line. */
  @Nested
  inner class RectangularSelection {
    /** The default state: Alt alone (the platform's own default shortcut) is enough - Shift is not needed. */
    @Nested
    inner class MouseReportingDisabled {
      @Test
      fun `alt press-drag-release creates a rectangular selection`(): Unit = doTest { fixture ->
        fixture.setText("0123456789\n0123456789\n0123456789")
        val alt = InputEvent.ALT_DOWN_MASK or InputEvent.BUTTON1_DOWN_MASK

        val pressConsumed = fixture.press(fixture.pointAt(row = 0, column = 2), alt)
        val dragConsumed = fixture.drag(fixture.pointAt(row = 2, column = 5), alt)
        val releaseConsumed = fixture.release(fixture.pointAt(row = 2, column = 5), alt)

        assertThat(pressConsumed).isFalse()
        assertThat(dragConsumed).isFalse()
        assertThat(releaseConsumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.caretSelectionsByLine()).containsExactly("234", "234", "234")
      }

      @Test
      fun `plain click after a rectangular selection removes it`(): Unit = doTest { fixture ->
        fixture.setText("0123456789\n0123456789\n0123456789")
        val alt = InputEvent.ALT_DOWN_MASK or InputEvent.BUTTON1_DOWN_MASK
        fixture.press(fixture.pointAt(row = 0, column = 2), alt)
        fixture.drag(fixture.pointAt(row = 2, column = 5), alt)
        fixture.release(fixture.pointAt(row = 2, column = 5), alt)
        check(fixture.editor.caretModel.caretCount > 1) { "Setup failed: no rectangular selection to remove" }

        val pressConsumed = fixture.press(fixture.pointAt(row = 1, column = 0), InputEvent.BUTTON1_DOWN_MASK)
        val releaseConsumed = fixture.release(fixture.pointAt(row = 1, column = 0), 0)

        assertThat(pressConsumed).isFalse()
        assertThat(releaseConsumed).isFalse()
        assertThat(fixture.reportedEvents).isEmpty()
        assertThat(fixture.editor.caretModel.caretCount).isEqualTo(1)
        assertThat(fixture.editor.selectionModel.hasSelection()).isFalse()
      }
    }

    /**
     * The terminal registers an [com.intellij.openapi.editor.impl.EditorMouseActionsOverrider] so Shift+Alt-drag
     * (not just the platform-default plain Alt-drag, which reporting would swallow) creates the block selection.
     */
    @Nested
    inner class MouseReportingEnabled {
      @Test
      fun `shift+alt press-drag-release creates a rectangular selection and is not reported`(): Unit =
        doTest(isMouseReportingEnabled = true) { fixture ->
          fixture.setText("0123456789\n0123456789\n0123456789")
          val modifiers = InputEvent.SHIFT_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.BUTTON1_DOWN_MASK

          val pressConsumed = fixture.press(fixture.pointAt(row = 0, column = 2), modifiers)
          val dragConsumed = fixture.drag(fixture.pointAt(row = 2, column = 5), modifiers)
          val releaseConsumed = fixture.release(fixture.pointAt(row = 2, column = 5), modifiers)

          assertThat(pressConsumed).isFalse()
          assertThat(dragConsumed).isFalse()
          assertThat(releaseConsumed).isFalse()
          assertThat(fixture.reportedEvents).isEmpty()
          val carets = fixture.editor.caretModel.allCarets
          val blockSelections = carets.filter { it.hasSelection() }.sortedBy { it.logicalPosition.line }.map { it.selectedText }
          assertThat(blockSelections).containsExactly("234", "234", "234")
          // Known quirk: Shift+Alt also matches the platform's default EditorAddOrRemoveCaret shortcut (only
          // the terminal's own rectangular-selection action is overridden), so press additionally adds a caret.
          assertThat(carets.count { !it.hasSelection() }).isEqualTo(1)
        }

      @Test
      fun `plain click after a shift+alt rectangular selection removes it and is reported`(): Unit =
        doTest(isMouseReportingEnabled = true) { fixture ->
          fixture.setText("0123456789\n0123456789\n0123456789")
          val modifiers = InputEvent.SHIFT_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.BUTTON1_DOWN_MASK
          fixture.press(fixture.pointAt(row = 0, column = 2), modifiers)
          fixture.drag(fixture.pointAt(row = 2, column = 5), modifiers)
          fixture.release(fixture.pointAt(row = 2, column = 5), modifiers)
          check(fixture.editor.caretModel.caretCount > 1) { "Setup failed: no rectangular selection to remove" }
          fixture.resetEffects()

          val pressConsumed = fixture.press(fixture.pointAt(row = 1, column = 0), InputEvent.BUTTON1_DOWN_MASK)
          val releaseConsumed = fixture.release(fixture.pointAt(row = 1, column = 0), 0)

          assertThat(pressConsumed).isTrue() // consumed by mouse reporting, same as any other plain click
          assertThat(releaseConsumed).isTrue()
          assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.RELEASED)
          assertThat(fixture.editor.caretModel.caretCount).isEqualTo(1)
          assertThat(fixture.editor.selectionModel.hasSelection()).isFalse()
        }
    }
  }

  /** The alternate buffer editor, which the view configures apart from the output editor. No hyperlink. */
  @Nested
  inner class AlternateBuffer {
    @Test
    fun `click places the editor caret`(): Unit = doTest(isAlternateScreenBuffer = true) { fixture ->
      fixture.setText("plain text")

      val point = fixture.pointAt(column = 4)
      val pressConsumed = fixture.press(point, InputEvent.BUTTON1_DOWN_MASK)
      val releaseConsumed = fixture.release(point, 0)

      assertThat(pressConsumed).isFalse()
      assertThat(releaseConsumed).isFalse()
      assertThat(fixture.reportedEvents).isEmpty()
      assertThat(fixture.editor.caretModel.offset).isEqualTo(4)
    }

    @Test
    fun `click is reported when mouse reporting is enabled`(): Unit =
      doTest(isMouseReportingEnabled = true, isAlternateScreenBuffer = true) { fixture ->
        fixture.setText("plain text")

        val point = fixture.pointAt(column = 4)
        val pressConsumed = fixture.press(point, InputEvent.BUTTON1_DOWN_MASK)
        val releaseConsumed = fixture.release(point, 0)

        assertThat(pressConsumed).isTrue()
        assertThat(releaseConsumed).isTrue()
        assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.RELEASED)
      }

    @Test
    fun `press-drag-release selects text and updates the terminal text selection model`(): Unit =
      doTest(isAlternateScreenBuffer = true) { fixture ->
        fixture.setText("select this text")
        val selectionEvents = mutableListOf<TerminalTextSelectionChangeEvent>()
        val listenerDisposable = Disposer.newDisposable()
        fixture.textSelectionModel.addListener(listenerDisposable, object : TerminalTextSelectionListener {
          override fun selectionChanged(event: TerminalTextSelectionChangeEvent) {
            selectionEvents.add(event)
          }
        })

        try {
          fixture.press(fixture.pointAt(column = 0), InputEvent.BUTTON1_DOWN_MASK)
          fixture.drag(fixture.pointAt(column = 6), InputEvent.BUTTON1_DOWN_MASK)
          fixture.release(fixture.pointAt(column = 6), 0)
        }
        finally {
          Disposer.dispose(listenerDisposable)
        }

        val model = fixture.alternateBufferModel
        val expectedSelection = TerminalTextSelection.of(model.startOffset, model.startOffset + 6L)
        assertThat(fixture.editor.selectionModel.selectedText).isEqualTo("select")
        assertThat(fixture.textSelectionModel.selection).isEqualTo(expectedSelection)
        assertThat(selectionEvents).isNotEmpty()
        assertThat(selectionEvents.last().outputModel).isSameAs(model)
        assertThat(selectionEvents.last().newSelection).isEqualTo(expectedSelection)
      }
  }

  /**
   * A click in the typed command moves the shell cursor to the clicked character with the arrow keys.
   *
   * [RecordingTerminalSession] has no shell integration. So these tests use [TerminalViewFixture] for each emulator:
   * the prompt marks, the cursor, the mouse reporting and the written keys go through a real session.
   */
  @Nested
  @ParameterizedClass
  @EnumSource(TerminalEmulatorType::class)
  inner class PromptClicks(private val emulatorType: TerminalEmulatorType) {
    @Test
    fun `click in the typed command moves the cursor there`(): Unit =
      doPromptClickTest { fixture ->
        fixture.typeCommand("ls -la")

        val written = fixture.awaitInputEventsHandled { fixture.click(column = 5) }

        assertThat(written.joinToString("")).isEqualTo(LEFT_ARROW.repeat(3))
      }

    @Test
    fun `click on the right half of a character moves the cursor to this character`(): Unit =
      doPromptClickTest { fixture ->
        fixture.typeCommand("ls -la")

        val written = fixture.awaitInputEventsHandled { fixture.click(column = 5, xInCell = 0.75f) }

        assertThat(written.joinToString("")).isEqualTo(LEFT_ARROW.repeat(3))
      }

    @Test
    fun `click on the right half of the last character moves the cursor to it`(): Unit =
      doPromptClickTest { fixture ->
        fixture.typeCommand("ls -la")
        fixture.moveCursorToColumn(2)

        val written = fixture.awaitInputEventsHandled { fixture.click(column = 7, xInCell = 0.75f) }

        assertThat(written.joinToString("")).isEqualTo(RIGHT_ARROW.repeat(5))
      }

    @Test
    fun `click after the line end moves the cursor to the text end`(): Unit =
      doPromptClickTest { fixture ->
        fixture.typeCommand("ls -la")
        fixture.moveCursorToColumn(2)

        val written = fixture.awaitInputEventsHandled { fixture.click(column = 11, xInCell = 0.75f) }

        assertThat(written.joinToString("")).isEqualTo(RIGHT_ARROW.repeat(6))
      }

    @Test
    fun `click in the spaces that the user typed at the end moves the cursor there`(): Unit =
      doPromptClickTest { fixture ->
        fixture.typeCommand("ls   ")
        fixture.moveCursorToColumn(2)

        val written = fixture.awaitInputEventsHandled { fixture.click(column = 6) }

        assertThat(written.joinToString("")).isEqualTo(RIGHT_ARROW.repeat(4))
      }

    @Test
    fun `click on the second cell of a double-width character moves the cursor to it`(): Unit =
      doPromptClickTest { fixture ->
        // The character takes the cells 7 and 8.
        fixture.typeCommand("echo ${HANGUL_HAN}x")

        val written = fixture.awaitInputEventsHandled { fixture.click(column = 8) }

        assertThat(written.joinToString("")).isEqualTo(LEFT_ARROW.repeat(2))
      }

    @Test
    fun `click on the right half of a character with a combining mark moves the cursor to it`(): Unit =
      doPromptClickTest { fixture ->
        // The accented character takes the cell 5. JediTerm keeps it in the NFC form, Ghostty keeps the mark after the "e".
        fixture.typeCommand("cafe${COMBINING_ACUTE_ACCENT}x")

        val written = fixture.awaitInputEventsHandled { fixture.click(column = 5, xInCell = 0.75f) }

        assertThat(written.joinToString("")).isEqualTo(LEFT_ARROW.repeat(2))
      }

    @Test
    fun `click sends the application arrow keys when the program enables them`(): Unit =
      doPromptClickTest { fixture ->
        fixture.typeCommand("ls -la")
        fixture.connector.feed("$ESC[?1h")
        fixture.view.sessionModel.terminalState.first { it.isApplicationArrowKeys }

        val written = fixture.awaitInputEventsHandled { fixture.click(column = 5) }

        assertThat(written.joinToString("")).isEqualTo("${ESC}OD".repeat(3))
      }

    @Test
    fun `click is reported and does not move the cursor when the program tracks the mouse`(): Unit =
      doPromptClickTest { fixture ->
        fixture.typeCommand("ls -la")
        fixture.connector.feed("$ESC[?1000h$ESC[?1006h")
        fixture.view.sessionModel.terminalState.first { it.mouseMode != MouseMode.MOUSE_REPORTING_NONE }

        val written = fixture.awaitInputEventsHandled { fixture.click(column = 5) }

        // The press and the release in the SGR format, at the 1-based column 6 and row 1.
        assertThat(written.joinToString("")).isEqualTo("$ESC[<0;6;1M$ESC[<0;6;1m")
      }

    @Test
    fun `shift click does not move the cursor`(): Unit =
      doPromptClickTest { fixture ->
        fixture.typeCommand("ls -la")

        val written = fixture.awaitInputEventsHandled { fixture.click(column = 5, modifiers = InputEvent.SHIFT_DOWN_MASK) }

        assertThat(written).isEmpty()
      }

    @Test
    fun `press-drag-release selects text and does not move the cursor`(): Unit =
      doPromptClickTest { fixture ->
        fixture.typeCommand("ls -la")

        val written = fixture.awaitInputEventsHandled { fixture.selectByDrag(fromColumn = 2, toColumn = 5) }

        assertThat(written).isEmpty()
        assertThat(fixture.activeEditor.selectionModel.selectedText).isEqualTo("ls ")
      }

    @Test
    fun `click after a text selection removes the selection and moves the cursor`(): Unit =
      doPromptClickTest { fixture ->
        fixture.typeCommand("ls -la")
        fixture.selectByDrag(fromColumn = 2, toColumn = 5)
        check(fixture.activeEditor.selectionModel.hasSelection()) { "Setup failed: no selection to remove" }

        val written = fixture.awaitInputEventsHandled { fixture.click(column = 7) }

        assertThat(written.joinToString("")).isEqualTo(LEFT_ARROW)
        assertThat(fixture.activeEditor.selectionModel.hasSelection()).isFalse()
      }

    @Test
    fun `click inside a text selection removes the selection and moves the cursor`(): Unit =
      doPromptClickTest { fixture ->
        fixture.typeCommand("ls -la")
        fixture.selectByDrag(fromColumn = 2, toColumn = 6)
        check(fixture.activeEditor.selectionModel.selectedText == "ls -") { "Setup failed: no selection to click inside" }

        val written = fixture.awaitInputEventsHandled { fixture.click(column = 4) }

        assertThat(written.joinToString("")).isEqualTo(LEFT_ARROW.repeat(4))
        assertThat(fixture.activeEditor.selectionModel.hasSelection()).isFalse()
      }

    @Test
    fun `double click selects a word and moves the cursor only with the first click`(): Unit =
      doPromptClickTest { fixture ->
        fixture.typeCommand("ls -la")

        val written = fixture.awaitInputEventsHandled {
          fixture.click(column = 6)
          fixture.click(column = 6, clickCount = 2)
        }

        assertThat(written.joinToString("")).isEqualTo(LEFT_ARROW.repeat(2))
        assertThat(fixture.activeEditor.selectionModel.hasSelection()).isTrue()
      }

    @Test
    fun `click without the shell integration does not move the cursor`(): Unit =
      doPromptClickTest { fixture ->
        fixture.connector.feed("${PROMPT}ls -la")
        fixture.assertOutputModelState(fixture.view.outputModels.regular) {
          it.text.trimEnd() == "${PROMPT}ls -la" && it.cursorOffset == it.startOffset + 8L
        }

        val written = fixture.awaitInputEventsHandled { fixture.click(column = 5) }

        assertThat(written).isEmpty()
      }

    private fun doPromptClickTest(test: suspend (TerminalViewFixture) -> Unit): Unit =
      timeoutRunBlocking(30.seconds, context = Dispatchers.EDT) {
        TerminalViewFixture(project, emulatorType).use { fixture ->
          // The size of the loopback session, so the resize does not change the screen.
          fixture.resize(columns = 80, rows = 24)
          test(fixture)
        }
      }

    /** Prints [PROMPT] between the prompt marks of the shell integration, then [command], as a shell does. */
    private suspend fun TerminalViewFixture.typeCommand(command: String) {
      connector.feed(shellIntegrationInitializedOsc(WORKING_DIRECTORY) + promptStartedOsc() + PROMPT + promptFinishedOsc() + command)
      // The prompt click handling is installed in this job.
      view.shellIntegrationFeaturesInitJob.join()
      val model = view.outputModels.regular
      // JediTerm keeps the printed text in the NFC form.
      val typedText = Normalizer.normalize(PROMPT + command, Normalizer.Form.NFC)
      assertOutputModelState(model) {
        val shownText = it.text.trimEnd('\n')
        Normalizer.normalize(shownText, Normalizer.Form.NFC) == typedText && it.cursorOffset == it.startOffset + shownText.length.toLong()
      }
      assertBlocksModelState(view.shellIntegrationDeferred.await().blocksModel) {
        (it.activeBlock as TerminalCommandBlock).commandStartOffset == model.startOffset + PROMPT.length.toLong()
      }
    }

    /** Moves the cursor to [column] of its line, as the shell does for the arrow keys. */
    private suspend fun TerminalViewFixture.moveCursorToColumn(column: Int) {
      connector.feed("$ESC[${column + 1}G")
      assertOutputModelState(view.outputModels.regular) { it.cursorOffset == it.startOffset + column.toLong() }
    }

    private fun TerminalViewFixture.click(column: Int, xInCell: Float = 1f / 3f, modifiers: Int = 0, clickCount: Int = 1) {
      val point = activeEditor.pointAtCell(column, xInCell = xInCell)
      activeEditor.dispatchMouseEvent(MouseEventKind.PRESSED, point, modifiers or InputEvent.BUTTON1_DOWN_MASK, clickCount)
      activeEditor.dispatchMouseEvent(MouseEventKind.RELEASED, point, modifiers, clickCount)
    }

    private fun TerminalViewFixture.selectByDrag(fromColumn: Int, toColumn: Int) {
      val editor = activeEditor
      editor.dispatchMouseEvent(MouseEventKind.PRESSED, editor.pointAtCell(fromColumn), InputEvent.BUTTON1_DOWN_MASK)
      editor.dispatchMouseEvent(MouseEventKind.DRAGGED, editor.pointAtCell(toColumn), InputEvent.BUTTON1_DOWN_MASK)
      editor.dispatchMouseEvent(MouseEventKind.RELEASED, editor.pointAtCell(toColumn), 0)
    }
  }

  /**
   * A real [TerminalViewImpl] connected to a [RecordingTerminalSession], so the production mouse events handler,
   * hyperlinks logic, and editor logic are exercised as-is.
   */
  private class Fixture(project: Project, private val columns: Int = 20, isMouseReportingEnabled: Boolean = false) : AutoCloseable {
    private val scope = terminalProjectScope(project).childScope("TerminalViewImpl")
    private val session = RecordingTerminalSession(scope, isMouseReportingEnabled)
    private val terminalView: TerminalViewImpl = TerminalViewImpl(
      project = project,
      settings = JBTerminalSystemSettingsProvider(),
      startupFusInfo = null,
      coroutineScope = scope
    )
    private var nextHyperlinkId = 1L

    // The view exposes the applier of the output editor only.
    private val decorationApplier: EditorTextDecorationApplier
      get() = terminalView.outputEditorDecorationApplier

    /** The editor of the active buffer. */
    val editor: EditorImpl
      get() = if (terminalView.isAlternateScreenBuffer) checkNotNull(terminalView.alternateBufferEditorDeferred.getNow()) else terminalView.outputEditor

    val textSelectionModel: TerminalTextSelectionModel
      get() = terminalView.textSelectionModel

    val alternateBufferModel: TerminalOutputModel
      get() = terminalView.outputModels.alternative

    /** Kinds of the mouse events reported to the (fake) terminal process, in order. */
    val reportedEvents: List<MouseEventKind>
      get() = session.reportedEvents

    /** How many times a hyperlink's action actually ran, i.e. the link was really opened. */
    var linkFollowedCount: Int = 0
      private set

    /** What EditorHyperlinkInteraction#calcHoveredLinkAttrs applies to a Ctrl/Cmd-hovered invisible link: a real link color. */
    val ctrlHoveredInvisibleLinkAttributes: TextAttributes
      get() = editor.colorsScheme.getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES)

    init {
      terminalView.connectToSession(session)
      setEditorSize()
    }

    /**
     * Switches the view to the alternate buffer, as a state change from the session does.
     * The session of this fixture has no output flow, so the test changes the state itself.
     */
    suspend fun switchToAlternateScreenBuffer() {
      val sessionModel = terminalView.sessionModel
      sessionModel.updateTerminalState(sessionModel.terminalState.value.copy(isAlternateScreenBuffer = true))
      terminalView.outputModels.active.first { it === terminalView.outputModels.alternative }
      setEditorSize()
    }

    private fun setEditorSize() {
      val characterGrid = editor.characterGrid ?: error("Character grid is not initialized")
      val widthInPixels = ceil(columns * characterGrid.charWidth).toInt()
      EditorTestUtil.setEditorVisibleSizeInPixels(editor, widthInPixels, 3 * editor.lineHeight)
    }

    fun setText(text: String) {
      val outputModel = terminalView.outputModels.active.value as MutableTerminalOutputModel
      outputModel.updateContent(0, text)
    }

    fun resetEffects() {
      session.reportedEvents.clear()
      linkFollowedCount = 0
    }

    /** Adds a hyperlink decoration and returns the point in the middle of its range (on the first line). */
    fun addHyperlink(startOffset: Int, endOffset: Int, isInvisible: Boolean = false): Point {
      decorationApplier.addDecorations(listOf(
        buildHyperlink(
          id = createTextDecorationId(nextHyperlinkId++), startOffset = startOffset, endOffset = endOffset,
          action = { linkFollowedCount++ },
        ) {
          this.isInvisibleLink = isInvisible
          this.hoveredAttributes = TEST_HOVERED_LINK_ATTRIBUTES
        }
      ))
      return pointAt((startOffset + endOffset) / 2)
    }

    /** The text attributes currently in effect for the hyperlink occupying [startOffset]..[endOffset]. */
    fun hyperlinkTextAttributes(startOffset: Int, endOffset: Int): TextAttributes {
      var result: TextAttributes? = null
      editor.markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset) { highlighter ->
        result = highlighter.getTextAttributes(editor.colorsScheme)
        true
      }
      return checkNotNull(result) { "No highlighter found for range $startOffset..$endOffset" }
    }

    /**
     * A point safely inside the given grid [column]'s cell on the given [row] (0-based).
     */
    fun pointAt(column: Int, row: Int = 0): Point = editor.pointAtCell(column, row)

    /** Every caret's selected text, ordered by line - the readable shape of a rectangular (block) selection. */
    fun caretSelectionsByLine(): List<String?> {
      return editor.caretModel.allCarets.sortedBy { it.logicalPosition.line }.map { it.selectedText }
    }

    fun hover(point: Point) {
      move(point, modifiers = 0)
    }

    /** Dispatches a mouse press and returns whether it ended up consumed. */
    fun press(point: Point, modifiers: Int): Boolean {
      return dispatch(MouseEventKind.PRESSED, point, modifiers).isConsumed
    }

    /** Dispatches a mouse release and returns whether it ended up consumed. */
    fun release(point: Point, modifiers: Int): Boolean {
      return dispatch(MouseEventKind.RELEASED, point, modifiers).isConsumed
    }

    /** Dispatches a mouse move and returns whether it ended up consumed. */
    fun move(point: Point, modifiers: Int): Boolean {
      return dispatch(MouseEventKind.MOVED, point, modifiers).isConsumed
    }

    /** Dispatches a mouse drag and returns whether it ended up consumed. */
    fun drag(point: Point, modifiers: Int): Boolean {
      return dispatch(MouseEventKind.DRAGGED, point, modifiers).isConsumed
    }

    private fun dispatch(kind: MouseEventKind, point: Point, modifiers: Int): MouseEvent = editor.dispatchMouseEvent(kind, point, modifiers)

    override fun close() {
      scope.cancel()
    }
  }

  /**
   * Collects the kinds of mouse events reported to the (fake) terminal process, always acknowledging them with
   * a static 1-byte report - unless [isMouseReportingEnabled] is false or the event is Shift-held, mirroring
   * how the real encoder leaves those cases for the editor.
   */
  private class RecordingTerminalSession(
    override val coroutineScope: CoroutineScope,
    private val isMouseReportingEnabled: Boolean,
  ) : TerminalSession {
    val reportedEvents: MutableList<MouseEventKind> = mutableListOf()

    override val eelDescriptor: EelDescriptor get() = LocalEelDescriptor
    override val processId: Long get() = -1
    override val isClosed: Boolean get() = false

    override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> = Channel(capacity = Channel.UNLIMITED)
    override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> = emptyFlow()
    override suspend fun hasRunningCommands(): Boolean = false

    /**
     * TODO: now we mock terminal session responses, so these tests are not really end-to-end
     *  Maybe it is worth using a real terminal session here, but mock on the level of TtyConnector?
     *  Then we will be able to test mouse scenarios end-to-end with different [org.jetbrains.plugins.terminal.TerminalEmulatorType].
     */
    override fun processMouseEvent(e: MouseEvent, x: Int, y: Int): ByteArray? {
      if (!isMouseReportingEnabled || e.isShiftDown) return null
      reportedEvents += when (e.id) {
        MouseEvent.MOUSE_PRESSED -> MouseEventKind.PRESSED
        MouseEvent.MOUSE_RELEASED -> MouseEventKind.RELEASED
        MouseEvent.MOUSE_MOVED -> MouseEventKind.MOVED
        MouseEvent.MOUSE_DRAGGED -> MouseEventKind.DRAGGED
        else -> error("Unexpected mouse event id: ${e.id}")
      }
      return REPORTED_EVENT_BYTES
    }

    override fun processKeyEvent(e: KeyEvent): KeyEventProcessingResultDto = KeyEventProcessingResultDto.Unhandled

    companion object {
      private val REPORTED_EVENT_BYTES = byteArrayOf(1)
    }
  }
}

/** An arbitrary hover-attributes value owned by the test, so it doesn't depend on the default hover formula. */
private val TEST_HOVERED_LINK_ATTRIBUTES = TextAttributes(null, null, Color.RED, EffectType.LINE_UNDERSCORE, Font.PLAIN)

/** The prompt that the prompt click tests print. The command starts at column 2. */
private const val PROMPT = "$ "

private const val WORKING_DIRECTORY = "/home/user"

private val LEFT_ARROW = "$ESC[D"
private val RIGHT_ARROW = "$ESC[C"

/** The Hangul syllable "han", a double-width character. */
private val HANGUL_HAN = Char(0xD55C)

private val COMBINING_ACUTE_ACCENT = Char(0x0301)

/** A point inside the grid cell at [column] and [row] (0-based). [xInCell] is the x of the point in the cell, from 0 to 1. */
private fun EditorImpl.pointAtCell(column: Int, row: Int = 0, xInCell: Float = 1f / 3f): Point {
  val characterGrid = characterGrid ?: error("Character grid is not initialized")
  val x = (characterGrid.charWidth * (column + xInCell)).toInt()
  return Point(x, row * lineHeight + lineHeight / 2)
}

private enum class MouseEventKind {
  PRESSED, RELEASED, MOVED, DRAGGED,
}

/** Dispatches a mouse event to the editor, as AWT does, and returns the event. */
private fun EditorImpl.dispatchMouseEvent(kind: MouseEventKind, point: Point, modifiers: Int, clickCount: Int = 1): MouseEvent {
  val id = when (kind) {
    MouseEventKind.PRESSED -> MouseEvent.MOUSE_PRESSED
    MouseEventKind.RELEASED -> MouseEvent.MOUSE_RELEASED
    MouseEventKind.MOVED -> MouseEvent.MOUSE_MOVED
    MouseEventKind.DRAGGED -> MouseEvent.MOUSE_DRAGGED
  }
  val event =
    MouseEvent(contentComponent, id, System.currentTimeMillis(), modifiers, point.x, point.y, clickCount, false, MouseEvent.BUTTON1)
  when (kind) {
    MouseEventKind.PRESSED -> mouseListener.mousePressed(event)
    MouseEventKind.RELEASED -> mouseListener.mouseReleased(event)
    MouseEventKind.MOVED -> contentComponent.mouseMotionListeners.forEach { it.mouseMoved(event) }
    MouseEventKind.DRAGGED -> contentComponent.mouseMotionListeners.forEach { it.mouseDragged(event) }
  }
  return event
}

@OptIn(LowLevelLocalMachineAccess::class)
private fun ctrlModifierMask(): Int {
  return if (OS.CURRENT == OS.macOS) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
}