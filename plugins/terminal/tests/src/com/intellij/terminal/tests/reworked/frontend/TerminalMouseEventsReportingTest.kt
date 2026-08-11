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
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Color
import java.awt.Font
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import kotlin.math.ceil

/**
 * Checks the full effect of mouse events over hyperlinks: whether the hyperlink is followed,
 * whether the event is reported to the [TerminalSession], and whether it ends up consumed.
 */
@RunWith(JUnit4::class)
internal class TerminalMouseEventsReportingTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `click outside hyperlink causes nothing and is reported`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    fixture.addHyperlink(startOffset = 5, endOffset = 9)

    val outsidePoint = fixture.pointAtColumn(0) // over "o" of "open", not the link
    fixture.hover(outsidePoint)
    fixture.resetEffects() // the hover move above is expected to report; only count effects after

    val pressConsumed = fixture.press(outsidePoint, InputEvent.BUTTON1_DOWN_MASK)
    val releaseConsumed = fixture.release(outsidePoint, 0)

    // Both consumed by mouse reporting
    assertThat(pressConsumed).isTrue()
    assertThat(releaseConsumed).isTrue()
    assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.RELEASED)
    assertThat(fixture.linkFollowedCount).isZero()
  }

  @Test
  fun `ctrl click outside hyperlink causes nothing and is reported`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    fixture.addHyperlink(startOffset = 5, endOffset = 9)

    val outsidePoint = fixture.pointAtColumn(0) // over "o" of "open", not the link
    fixture.hover(outsidePoint)
    fixture.resetEffects()

    val pressConsumed = fixture.press(outsidePoint, ctrlModifierMask())
    val releaseConsumed = fixture.release(outsidePoint, ctrlModifierMask())

    // No hyperlink here, so the modifier is irrelevant: the click is reported like any other.
    assertThat(pressConsumed).isTrue()
    assertThat(releaseConsumed).isTrue()
    assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.PRESSED, MouseEventKind.RELEASED)
    assertThat(fixture.linkFollowedCount).isZero()
  }

  @Test
  fun `click on visible hyperlink opens it and is reported`(): Unit = doTest { fixture ->
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
  fun `click on invisible hyperlink does not open it and is reported`(): Unit = doTest { fixture ->
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
  fun `ctrl click on visible hyperlink opens it and is not reported`(): Unit = doTest { fixture ->
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
  fun `ctrl click on invisible hyperlink opens it and is not reported`(): Unit = doTest { fixture ->
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
  fun `move over visible hyperlink causes nothing and is reported`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)

    val consumed = fixture.move(linkPoint, 0)

    assertThat(consumed).isTrue()
    assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.MOVED)
    assertThat(fixture.linkFollowedCount).isZero()
  }

  @Test
  fun `move over invisible hyperlink underlines it and is reported`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)

    val consumed = fixture.move(linkPoint, 0)

    assertThat(consumed).isTrue()
    assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.MOVED)
    assertThat(fixture.linkFollowedCount).isZero()
    assertThat(fixture.hyperlinkTextAttributes(startOffset = 5, endOffset = 9)).isEqualTo(TEST_HOVERED_LINK_ATTRIBUTES)
  }

  @Test
  fun `ctrl move over visible hyperlink causes nothing and is reported`(): Unit = doTest { fixture ->
    fixture.setText("open link here")
    val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)

    val consumed = fixture.move(linkPoint, ctrlModifierMask())

    assertThat(consumed).isTrue()
    assertThat(fixture.reportedEvents).containsExactly(MouseEventKind.MOVED)
    assertThat(fixture.linkFollowedCount).isZero()
  }

  @Test
  fun `ctrl move over invisible hyperlink underlines it and is reported`(): Unit = doTest { fixture ->
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
  fun `press-drag-release from visible hyperlink does not open it and is reported`(): Unit =
    doTest { fixture ->
      // A plain press/drag/release must be reported in full even when it starts on a hyperlink,
      // or drag-selecting text that happens to start on a link would be invisible to the shell.
      fixture.setText("open link here")
      val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)
      val awayPoint = fixture.pointAtColumn(12) // over "here", not the link
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
  fun `press-drag-release from invisible hyperlink does not open it and is reported`(): Unit =
    doTest { fixture ->
      fixture.setText("open link here")
      val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)
      val awayPoint = fixture.pointAtColumn(12) // over "here", not the link
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
  fun `ctrl press-drag-release from visible hyperlink does not open it and is not reported`(): Unit =
    doTest { fixture ->
      // Hover freezes on the link while a button is held (it only updates on mouseMoved), so every
      // event of the gesture is consumed pre-emptively - yet release lands away from press, so the
      // link is never actually opened.
      fixture.setText("open link here")
      val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9)
      val awayPoint = fixture.pointAtColumn(12) // over "here", not the link
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

  @Test
  fun `ctrl press-drag-release from invisible hyperlink does not open it and is not reported`(): Unit =
    doTest { fixture ->
      fixture.setText("open link here")
      val linkPoint = fixture.addHyperlink(startOffset = 5, endOffset = 9, isInvisible = true)
      val awayPoint = fixture.pointAtColumn(12) // over "here", not the link
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

  companion object {
    /** An arbitrary hover-attributes value owned by the test, so it doesn't depend on the default hover formula. */
    private val TEST_HOVERED_LINK_ATTRIBUTES = TextAttributes(null, null, Color.RED, EffectType.LINE_UNDERSCORE, Font.PLAIN)
  }

  /**
   * A real [TerminalViewImpl] connected to a [RecordingTerminalSession], so the production mouse events handler and
   * hyperlinks logic (registered on [TerminalViewImpl.outputEditor]) are exercised as-is.
   */
  private class Fixture(project: Project, columns: Int = 20) : AutoCloseable {
    private val scope = terminalProjectScope(project).childScope("TerminalViewImpl")
    private val session = RecordingTerminalSession(scope)
    private val terminalView: TerminalViewImpl = TerminalViewImpl(
      project = project,
      settings = JBTerminalSystemSettingsProvider(),
      startupFusInfo = null,
      coroutineScope = scope
    )
    private var nextHyperlinkId = 1L

    private val decorationApplier: EditorTextDecorationApplier
      get() = terminalView.outputEditorDecorationApplier

    private val editor: EditorImpl
      get() = terminalView.outputEditor

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

      val characterGrid = editor.characterGrid ?: error("Character grid is not initialized")
      val widthInPixels = ceil(columns * characterGrid.charWidth).toInt()
      EditorTestUtil.setEditorVisibleSizeInPixels(editor, widthInPixels, 3 * editor.lineHeight)
    }

    fun setText(text: String) {
      val outputModel = terminalView.outputModels.regular as MutableTerminalOutputModel
      outputModel.updateContent(0, text)
    }

    fun resetEffects() {
      session.reportedEvents.clear()
      linkFollowedCount = 0
    }

    /** Adds a hyperlink decoration and returns the point in the middle of its range. */
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
      return pointAtColumn((startOffset + endOffset) / 2)
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

    /** The point in the middle of the given [column]'s cell on the first line. */
    fun pointAtColumn(column: Int): Point {
      val characterGrid = editor.characterGrid ?: error("Character grid is not initialized")
      return Point((characterGrid.charWidth * (column + 0.5)).toInt(), editor.lineHeight / 2)
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
