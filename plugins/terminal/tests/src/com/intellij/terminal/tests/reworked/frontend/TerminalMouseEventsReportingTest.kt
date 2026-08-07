package com.intellij.terminal.tests.reworked.frontend

import com.intellij.execution.impl.EditorTextDecorationApplier
import com.intellij.execution.impl.buildHyperlink
import com.intellij.execution.impl.createTextDecorationId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.dto.KeyEventProcessingResultDto
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import kotlin.math.ceil

/**
 * Checks how mouse events are reported to the [TerminalSession] in various scenarios.
 * Now covers only cases with mouse events over hyperlinks.
 */
@RunWith(JUnit4::class)
internal class TerminalMouseEventsReportingTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `plain click outside any hyperlink is reported`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    fixture.addHyperlink(startOffset = 5, endOffset = 9)

    val outsidePoint = fixture.pointAtColumn(0) // over "o" of "open", not the link
    fixture.hover(outsidePoint)
    fixture.clearReportedEvents() // the hover move above is expected to report; only count what happens after

    fixture.press(outsidePoint, InputEvent.BUTTON1_DOWN_MASK)
    val consumed = fixture.release(outsidePoint, 0)

    assertThat(consumed).isFalse()
    assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.RELEASED)
  }

  @Test
  fun `ctrl click outside any hyperlink is reported`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    fixture.addHyperlink(startOffset = 5, endOffset = 9)

    val outsidePoint = fixture.pointAtColumn(0) // over "o" of "open", not the link
    fixture.hover(outsidePoint)
    fixture.clearReportedEvents() // the hover move above is expected to report; only count what happens after

    fixture.press(outsidePoint, ctrlModifierMask())
    fixture.release(outsidePoint, ctrlModifierMask())

    // No hyperlink here, so the modifier has nothing to do with terminal-side hyperlink
    // handling: the click must still be reported, preserving whatever the shell app itself
    // does with Cmd/Ctrl-clicks unrelated to links.
    assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.RELEASED)
  }

  @Test
  fun `plain click on a visible hyperlink is still reported (dual action)`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)
    fixture.hover(linkPoint)
    fixture.clearReportedEvents() // the hover move above is expected to report; only count what happens after

    fixture.press(linkPoint, InputEvent.BUTTON1_DOWN_MASK)
    val consumed = fixture.release(linkPoint, 0)

    // A plain click is never consumed by hyperlink handling, not even on a visible link, so the
    // shell's own mouse-tracking state (e.g. for text selection) always sees a fully paired
    // click, even though it also opens the link locally (a deliberate dual action).
    assertThat(consumed).isFalse()
    assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.RELEASED)
  }

  @Test
  fun `plain click on an invisible hyperlink is still reported (dual action)`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)
    fixture.hover(linkPoint)
    fixture.clearReportedEvents() // the hover move above is expected to report; only count what happens after

    fixture.press(linkPoint, InputEvent.BUTTON1_DOWN_MASK)
    fixture.release(linkPoint, 0)

    assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.RELEASED)
  }

  @Test
  fun `ctrl click on a visible hyperlink opens it once and is not reported`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)
    fixture.hover(linkPoint)
    fixture.clearReportedEvents() // the hover move above is expected to report; only count what happens after

    fixture.press(linkPoint, ctrlModifierMask())
    val consumed = fixture.release(linkPoint, ctrlModifierMask())

    assertThat(consumed).isTrue() // followLink opened it
    assertThat(fixture.reportedEvents).isEmpty() // and it wasn't also reported to the shell
  }

  @Test
  fun `ctrl click on an invisible hyperlink is not reported`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)
    fixture.hover(linkPoint)
    fixture.clearReportedEvents() // the hover move above is expected to report; only count what happens after

    fixture.press(linkPoint, ctrlModifierMask())
    val consumed = fixture.release(linkPoint, ctrlModifierMask())

    assertThat(consumed).isTrue() // followLink opened it (ctrl bypasses the hint)
    assertThat(fixture.reportedEvents).isEmpty()
  }

  @Test
  fun `plain mouse move over a visible hyperlink is reported`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)

    fixture.move(linkPoint, 0)

    assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.MOVED)
  }

  @Test
  fun `plain mouse move over an invisible hyperlink is reported`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)

    fixture.move(linkPoint, 0)

    assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.MOVED)
  }

  @Test
  fun `ctrl mouse move over a hyperlink is still reported`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)

    fixture.move(linkPoint, ctrlModifierMask())

    assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.MOVED)
  }

  @Test
  fun `ctrl mouse move over an invisible hyperlink is still reported`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)

    fixture.move(linkPoint, ctrlModifierMask())

    assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.MOVED)
  }

  @Test
  fun `pressing on a hyperlink, dragging away and releasing elsewhere is fully reported (drag-select over a link works)`(): Unit =
    doTest { fixture ->
      // A plain press/drag/release must be reported in full even when the gesture starts on a
      // hyperlink, or drag-selecting text that happens to start on a link would be invisible to the shell.
      fixture.setText("open link here")
      val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)
      val awayPoint = fixture.pointAtColumn(12) // over "here", not the link
      fixture.hover(linkPoint)
      fixture.clearReportedEvents() // the hover move above is expected to report; only count what happens after

      fixture.press(linkPoint, InputEvent.BUTTON1_DOWN_MASK)
      fixture.drag(awayPoint, InputEvent.BUTTON1_DOWN_MASK)
      fixture.release(awayPoint, 0)

      assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.DRAGGED, MouseEventKind.RELEASED)
    }

  @Test
  fun `pressing on an invisible hyperlink, dragging away and releasing elsewhere is fully reported (drag-select over a link works)`(): Unit =
    doTest { fixture ->
      fixture.setText("open link here")
      val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)
      val awayPoint = fixture.pointAtColumn(12) // over "here", not the link
      fixture.hover(linkPoint)
      fixture.clearReportedEvents() // the hover move above is expected to report; only count what happens after

      fixture.press(linkPoint, InputEvent.BUTTON1_DOWN_MASK)
      fixture.drag(awayPoint, InputEvent.BUTTON1_DOWN_MASK)
      fixture.release(awayPoint, 0)

      assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.DRAGGED, MouseEventKind.RELEASED)
    }

  @Test
  fun `ctrl press on a hyperlink then dragging away and releasing elsewhere reports neither`(): Unit =
    doTest { fixture ->
      // Hover tracking only updates on mouseMoved, which isn't fired while a button is held, so it
      // stays frozen at the link for the whole gesture below even though the mouse moves away -
      // keeping the suppression decision (and therefore press/release pairing) consistent without
      // needing to track gesture state explicitly.
      fixture.setText("open link here")
      val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)
      val awayPoint = fixture.pointAtColumn(12) // over "here", not the link
      fixture.hover(linkPoint)
      fixture.clearReportedEvents() // the hover move above is expected to report; only count what happens after

      fixture.press(linkPoint, ctrlModifierMask())
      fixture.drag(awayPoint, ctrlModifierMask())
      fixture.release(awayPoint, ctrlModifierMask())

      assertThat(fixture.reportedEvents).isEmpty()
    }

  @Test
  fun `ctrl press on an invisible hyperlink then dragging away and releasing elsewhere reports neither`(): Unit =
    doTest { fixture ->
      fixture.setText("open link here")
      val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)
      val awayPoint = fixture.pointAtColumn(12) // over "here", not the link
      fixture.hover(linkPoint)
      fixture.clearReportedEvents() // the hover move above is expected to report; only count what happens after

      fixture.press(linkPoint, ctrlModifierMask())
      fixture.drag(awayPoint, ctrlModifierMask())
      fixture.release(awayPoint, ctrlModifierMask())

      assertThat(fixture.reportedEvents).isEmpty()
    }

  private fun doTest(block: suspend (Fixture) -> Unit): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      Fixture(project).use { fixture ->
        block(fixture)
      }
    }

  @OptIn(LowLevelLocalMachineAccess::class)
  private fun ctrlModifierMask(): Int {
    return if (OS.CURRENT == OS.macOS) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
  }

  private enum class MouseEventKind {
    PRESSED, RELEASED, MOVED, DRAGGED,
  }

  /**
   * A real [TerminalViewImpl] connected to a [RecordingTerminalSession], so the production mouse events handler and
   * hyperlinks logic (registered on [TerminalViewImpl.outputEditor]) are exercised as-is.
   */
  private class Fixture(project: Project, columns: Int = 20) : AutoCloseable {
    private val scope = terminalProjectScope(project).childScope("TerminalViewImpl")
    private val session = RecordingTerminalSession(scope)
    private val decorationApplier: EditorTextDecorationApplier
    private var nextHyperlinkId = 1L

    val editor: EditorImpl

    /** Kinds of the mouse events reported to the (fake) terminal process, in order. */
    val reportedEvents: List<MouseEventKind>
      get() = session.reportedEvents

    init {
      val terminalView = TerminalViewImpl(project, JBTerminalSystemSettingsProvider(), null, scope)
      terminalView.connectToSession(session)
      decorationApplier = terminalView.outputEditorDecorationApplier
      editor = terminalView.outputEditor as EditorImpl

      val characterGrid = editor.characterGrid ?: error("Character grid is not initialized")
      val widthInPixels = ceil(columns * characterGrid.charWidth).toInt()
      EditorTestUtil.setEditorVisibleSizeInPixels(editor, widthInPixels, 3 * editor.lineHeight)
    }

    fun setText(text: String) {
      editor.document.setText(text)
    }

    fun clearReportedEvents() {
      session.reportedEvents.clear()
    }

    /** Adds a hyperlink decoration and returns the point in the middle of its range. */
    fun addHyperlink(startOffset: Int, endOffset: Int, isInvisible: Boolean = false): Point {
      decorationApplier.addDecorations(listOf(
        buildHyperlink(
          id = createTextDecorationId(nextHyperlinkId++), startOffset = startOffset, endOffset = endOffset,
          action = { },
        ) {
          this.isInvisibleLink = isInvisible
        }
      ))
      return pointAtColumn((startOffset + endOffset) / 2)
    }

    /** The point in the middle of the given [column]'s cell on the first line. */
    fun pointAtColumn(column: Int): Point {
      val characterGrid = editor.characterGrid ?: error("Character grid is not initialized")
      return Point((characterGrid.charWidth * (column + 0.5)).toInt(), editor.lineHeight / 2)
    }

    fun hover(point: Point) {
      move(point, modifiers = 0)
    }

    fun press(point: Point, modifiers: Int) {
      dispatch(MouseEventKind.PRESSED, point, modifiers)
    }

    /** Dispatches a mouse release and returns whether the editor consumed it. */
    fun release(point: Point, modifiers: Int): Boolean =
      dispatch(MouseEventKind.RELEASED, point, modifiers).isConsumed

    fun move(point: Point, modifiers: Int) {
      dispatch(MouseEventKind.MOVED, point, modifiers)
    }

    fun drag(point: Point, modifiers: Int) {
      dispatch(MouseEventKind.DRAGGED, point, modifiers)
    }

    private fun dispatch(kind: MouseEventKind, point: Point, modifiers: Int): MouseEvent {
      val id = when (kind) {
        MouseEventKind.PRESSED -> MouseEvent.MOUSE_PRESSED
        MouseEventKind.RELEASED -> MouseEvent.MOUSE_RELEASED
        MouseEventKind.MOVED -> MouseEvent.MOUSE_MOVED
        MouseEventKind.DRAGGED -> MouseEvent.MOUSE_DRAGGED
      }
      val event =
        MouseEvent(editor.contentComponent, id, System.currentTimeMillis(), modifiers, point.x, point.y, 1, false, MouseEvent.BUTTON1)
      when (kind) {
        MouseEventKind.PRESSED -> editor.mouseListener.mousePressed(event)
        MouseEventKind.RELEASED -> editor.mouseListener.mouseReleased(event)
        MouseEventKind.MOVED -> editor.contentComponent.mouseMotionListeners.forEach { it.mouseMoved(event) }
        MouseEventKind.DRAGGED -> editor.contentComponent.mouseMotionListeners.forEach { it.mouseDragged(event) }
      }
      return event
    }

    override fun close() {
      scope.cancel()
    }
  }

  /** Collects the kinds of mouse events reported to the (fake) terminal process, always acknowledging them with a static 1-byte report. */
  private class RecordingTerminalSession(
    override val coroutineScope: CoroutineScope,
  ) : TerminalSession {
    val reportedEvents: MutableList<MouseEventKind> = mutableListOf()

    override val eelDescriptor: EelDescriptor get() = LocalEelDescriptor
    override val processId: Long get() = -1
    override val isClosed: Boolean get() = false

    override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> = Channel(capacity = Channel.UNLIMITED)
    override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> = emptyFlow()
    override suspend fun hasRunningCommands(): Boolean = false

    override fun processMouseEvent(e: MouseEvent, x: Int, y: Int): ByteArray {
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
