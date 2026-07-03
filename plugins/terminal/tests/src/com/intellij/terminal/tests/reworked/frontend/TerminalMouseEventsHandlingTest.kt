package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.frontend.view.impl.TerminalEditorFactory
import com.intellij.terminal.frontend.view.impl.TerminalMouseEventsHandler
import com.intellij.terminal.frontend.view.impl.setupMouseEventsHandling
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jediterm.terminal.emulator.mouse.MouseMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.math.ceil

@RunWith(JUnit4::class)
internal class TerminalMouseEventsHandlingTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `setupMouseEventsHandling forwards mouse press and release with grid coordinates`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(columns = 20)
    val events = mutableListOf<MouseGridEvent>()
    setupMouseEventsHandling(
      editor = editor,
      sessionModel = TerminalSessionModelImpl().apply {
        updateTerminalState(terminalState.value.copy(mouseMode = MouseMode.MOUSE_REPORTING_NORMAL))
      },
      settings = object : JBTerminalSystemSettingsProviderBase() {
        override fun enableMouseReporting(): Boolean = true
      },
      eventsHandler = RecordingTerminalMouseEventsHandler(events),
      disposable = testRootDisposable,
    )
    editor.document.setText("abc")

    val characterGrid = (editor as EditorImpl).characterGrid ?: error("Character grid is not initialized")
    val mouseListener = (editor as EditorImpl).mouseListener
    val cell0Point = Point((characterGrid.charWidth * 0.5).toInt(), editor.lineHeight / 2)
    val cell1Point = Point((characterGrid.charWidth * 1.5).toInt(), editor.lineHeight / 2)
    mouseListener.mousePressed(MouseEvent(editor.contentComponent, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, cell0Point.x, cell0Point.y, 1, false, MouseEvent.BUTTON1))
    mouseListener.mouseReleased(MouseEvent(editor.contentComponent, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, cell0Point.x, cell0Point.y, 1, false, MouseEvent.BUTTON1))
    mouseListener.mousePressed(MouseEvent(editor.contentComponent, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, cell1Point.x, cell1Point.y, 1, false, MouseEvent.BUTTON1))
    mouseListener.mouseReleased(MouseEvent(editor.contentComponent, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, cell1Point.x, cell1Point.y, 1, false, MouseEvent.BUTTON1))

    assertThat(events).containsExactly(
      MouseGridEvent.Pressed(0, 0),
      MouseGridEvent.Released(0, 0),
      MouseGridEvent.Pressed(1, 0),
      MouseGridEvent.Released(1, 0),
    )
  }

  @Test
  fun `setupMouseEventsHandling forwards mouse wheel with grid coordinates`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(columns = 20)
    val events = mutableListOf<MouseGridEvent>()
    setupMouseEventsHandling(
      editor = editor,
      sessionModel = TerminalSessionModelImpl().apply {
        updateTerminalState(terminalState.value.copy(mouseMode = MouseMode.MOUSE_REPORTING_NORMAL))
      },
      settings = object : JBTerminalSystemSettingsProviderBase() {
        override fun enableMouseReporting(): Boolean = true
      },
      eventsHandler = RecordingTerminalMouseEventsHandler(events),
      disposable = testRootDisposable,
    )
    editor.document.setText("abc")

    val characterGrid = (editor as EditorImpl).characterGrid ?: error("Character grid is not initialized")
    val point = Point((characterGrid.charWidth * 2.5).toInt(), editor.lineHeight / 2)
    val wheelEvent = MouseWheelEvent(
      editor.scrollPane,
      MouseEvent.MOUSE_WHEEL,
      System.currentTimeMillis(),
      0,
      point.x,
      point.y,
      0,
      false,
      MouseWheelEvent.WHEEL_UNIT_SCROLL,
      1,
      1,
    )
    editor.scrollPane.mouseWheelListeners.forEach { it.mouseWheelMoved(wheelEvent) }

    assertThat(events).containsExactly(
      MouseGridEvent.WheelMoved(2, 0),
    )
  }

  @Test
  fun `setupMouseEventsHandling forwards mouse move with grid coordinates`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(columns = 20)
    val events = mutableListOf<MouseGridEvent>()
    setupMouseEventsHandling(
      editor = editor,
      sessionModel = TerminalSessionModelImpl().apply {
        updateTerminalState(terminalState.value.copy(mouseMode = MouseMode.MOUSE_REPORTING_NORMAL))
      },
      settings = object : JBTerminalSystemSettingsProviderBase() {
        override fun enableMouseReporting(): Boolean = true
      },
      eventsHandler = RecordingTerminalMouseEventsHandler(events),
      disposable = testRootDisposable,
    )
    editor.document.setText("abc")

    val characterGrid = (editor as EditorImpl).characterGrid ?: error("Character grid is not initialized")
    val point = Point((characterGrid.charWidth * 1.5).toInt(), editor.lineHeight / 2)
    val mouseEvent = MouseEvent(
      editor.contentComponent,
      MouseEvent.MOUSE_MOVED,
      System.currentTimeMillis(),
      0,
      point.x,
      point.y,
      0,
      false,
      MouseEvent.NOBUTTON,
    )
    editor.contentComponent.mouseMotionListeners.forEach { it.mouseMoved(mouseEvent) }

    assertThat(events).containsExactly(
      MouseGridEvent.Moved(1, 0),
    )
  }

  @Test
  fun `setupMouseEventsHandling forwards mouse drag with grid coordinates`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(columns = 20)
    val events = mutableListOf<MouseGridEvent>()
    setupMouseEventsHandling(
      editor = editor,
      sessionModel = TerminalSessionModelImpl().apply {
        updateTerminalState(terminalState.value.copy(mouseMode = MouseMode.MOUSE_REPORTING_NORMAL))
      },
      settings = object : JBTerminalSystemSettingsProviderBase() {
        override fun enableMouseReporting(): Boolean = true
      },
      eventsHandler = RecordingTerminalMouseEventsHandler(events),
      disposable = testRootDisposable,
    )
    editor.document.setText("abc")

    val characterGrid = (editor as EditorImpl).characterGrid ?: error("Character grid is not initialized")
    val point = Point((characterGrid.charWidth * 2.5).toInt(), editor.lineHeight / 2)
    val mouseEvent = MouseEvent(
      editor.contentComponent,
      MouseEvent.MOUSE_DRAGGED,
      System.currentTimeMillis(),
      InputEvent.BUTTON1_DOWN_MASK,
      point.x,
      point.y,
      0,
      false,
      MouseEvent.BUTTON1,
    )
    editor.contentComponent.mouseMotionListeners.forEach { it.mouseDragged(mouseEvent) }

    assertThat(events).containsExactly(
      MouseGridEvent.Dragged(2, 0),
    )
  }

  @Test
  fun `setupMouseEventsHandling forwards mouse move over double-width character with grid coordinates`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val editor = createEditor(columns = 20)
    val events = mutableListOf<MouseGridEvent>()
    setupMouseEventsHandling(
      editor = editor,
      sessionModel = TerminalSessionModelImpl().apply {
        updateTerminalState(terminalState.value.copy(mouseMode = MouseMode.MOUSE_REPORTING_NORMAL))
      },
      settings = object : JBTerminalSystemSettingsProviderBase() {
        override fun enableMouseReporting(): Boolean = true
      },
      eventsHandler = RecordingTerminalMouseEventsHandler(events),
      disposable = testRootDisposable,
    )
    editor.document.setText("a한b")

    val characterGrid = (editor as EditorImpl).characterGrid ?: error("Character grid is not initialized")
    val leftHalfPoint = Point((characterGrid.charWidth * 1.5).toInt(), editor.lineHeight / 2)
    val rightHalfPoint = Point((characterGrid.charWidth * 2.5).toInt(), editor.lineHeight / 2)

    editor.contentComponent.mouseMotionListeners.forEach { it.mouseMoved(
      MouseEvent(
        editor.contentComponent,
        MouseEvent.MOUSE_MOVED,
        System.currentTimeMillis(),
        0,
        leftHalfPoint.x,
        leftHalfPoint.y,
        0,
        false,
        MouseEvent.NOBUTTON,
      )
    ) }
    editor.contentComponent.mouseMotionListeners.forEach { it.mouseMoved(
      MouseEvent(
        editor.contentComponent,
        MouseEvent.MOUSE_MOVED,
        System.currentTimeMillis(),
        0,
        rightHalfPoint.x,
        rightHalfPoint.y,
        0,
        false,
        MouseEvent.NOBUTTON,
      )
    ) }

    assertThat(events).containsExactly(
      MouseGridEvent.Moved(1, 0),
      MouseGridEvent.Moved(2, 0),
    )
  }

  private class RecordingTerminalMouseEventsHandler(
    private val events: MutableList<MouseGridEvent>,
  ) : TerminalMouseEventsHandler {
    override fun mousePressed(x: Int, y: Int, event: MouseEvent) {
      events += MouseGridEvent.Pressed(x, y)
    }

    override fun mouseReleased(x: Int, y: Int, event: MouseEvent) {
      events += MouseGridEvent.Released(x, y)
    }

    override fun mouseMoved(x: Int, y: Int, event: MouseEvent) {
      events += MouseGridEvent.Moved(x, y)
    }

    override fun mouseDragged(x: Int, y: Int, event: MouseEvent) {
      events += MouseGridEvent.Dragged(x, y)
    }

    override fun mouseWheelMoved(x: Int, y: Int, event: MouseWheelEvent) {
      events += MouseGridEvent.WheelMoved(x, y)
    }
  }

  private sealed class MouseGridEvent {
    data class Pressed(val x: Int, val y: Int) : MouseGridEvent()
    data class Released(val x: Int, val y: Int) : MouseGridEvent()
    data class Moved(val x: Int, val y: Int) : MouseGridEvent()
    data class Dragged(val x: Int, val y: Int) : MouseGridEvent()
    data class WheelMoved(val x: Int, val y: Int) : MouseGridEvent()
  }

  private fun createEditor(columns: Int): EditorEx {
    val scope = terminalProjectScope(project).childScope("TerminalOutputEditor")
    Disposer.register(testRootDisposable) { scope.cancel() }
    val editor = TerminalEditorFactory.createOutputEditor(project, JBTerminalSystemSettingsProvider(), scope)
    val characterGrid = (editor as EditorImpl).characterGrid ?: error("Character grid is not initialized")
    val widthInPixels = ceil(columns * characterGrid.charWidth).toInt()
    EditorTestUtil.setEditorVisibleSizeInPixels(editor, widthInPixels, 3 * editor.lineHeight)
    assertThat(characterGrid.columns).isEqualTo(columns)
    return editor
  }
}
