package com.intellij.terminal.tests.reworked.frontend.typing

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.TerminalKeyEventImpl
import com.intellij.terminal.frontend.view.impl.TerminalTypingEvent
import com.intellij.terminal.frontend.view.impl.TerminalTypingListener
import com.intellij.terminal.frontend.view.impl.TerminalTypingTrackerImpl
import com.intellij.terminal.frontend.view.impl.TerminalTypingTrackerImpl.StateForTest
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.outputPattern
import com.intellij.terminal.tests.reworked.util.updateContent
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.asDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalShellIntegrationImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import kotlin.time.Duration.Companion.milliseconds

@RunWith(JUnit4::class)
internal class TerminalTypingTrackerTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `typing is confirmed after matching output confirmation`(): Unit = doTest { fixture ->
    fixture.type('a', TerminalOffset.ZERO)
    fixture.assertTrackerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)
    assertThat(fixture.events.tryReceive().getOrNull()).isNull()

    fixture.updateOutput("a<cursor>")

    assertThat(fixture.events.receive()).isEqualTo(RecordedConfirmedTyping('a', 0))
    assertThat(fixture.events.tryReceive().getOrNull()).isNull()
    fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
  }

  @Test
  fun `output changing command text with no pending typing produces a mismatch`(): Unit = doTest { fixture ->
    fixture.updateOutput("a<cursor>")

    assertThat(fixture.events.receive()).isEqualTo(RecordedMismatch)
    fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
  }

  @Test
  fun `output text mismatch clears pending input and produces a single mismatch`(): Unit = doTest { fixture ->
    fixture.type('a', TerminalOffset.ZERO)
    fixture.assertTrackerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)

    fixture.updateOutput("x<cursor>")

    assertThat(fixture.events.receive()).isEqualTo(RecordedMismatch)
    assertThat(fixture.events.tryReceive().getOrNull()).isNull()
    fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
  }

  @Test
  fun `partial output confirmation confirms only the matched prefix`(): Unit = doTest { fixture ->
    fixture.type('a', TerminalOffset.ZERO)
    fixture.type('b', TerminalOffset.ZERO)
    fixture.assertTrackerState(hasInputSession = true, pendingEventsCount = 2, predictionsCount = 2)

    fixture.updateOutput("a<cursor>")

    assertThat(fixture.events.receive()).isEqualTo(RecordedConfirmedTyping('a', 0))
    assertThat(fixture.events.tryReceive().getOrNull()).isNull()
    fixture.assertTrackerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)

    fixture.updateOutput("ab<cursor>")

    assertThat(fixture.events.receive()).isEqualTo(RecordedConfirmedTyping('b', 1))
    assertThat(fixture.events.tryReceive().getOrNull()).isNull()
    fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
  }

  @Test
  fun `mismatch after partial confirmation produces a single mismatch and clears every pending input`(): Unit = doTest { fixture ->
    fixture.type('a', TerminalOffset.ZERO)
    fixture.type('b', TerminalOffset.ZERO)
    fixture.assertTrackerState(hasInputSession = true, pendingEventsCount = 2, predictionsCount = 2)

    fixture.updateOutput("ax<cursor>")

    assertThat(fixture.events.receive()).isEqualTo(RecordedMismatch)
    assertThat(fixture.events.tryReceive().getOrNull()).isNull()
    fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
  }

  @Test
  fun `output received before any input is not treated as that input's confirmation`(): Unit = doTest { fixture ->
    fixture.updateOutput("a<cursor>")
    assertThat(fixture.events.receive()).isEqualTo(RecordedMismatch)

    fixture.type('b', TerminalOffset.of(1))

    assertThat(fixture.events.tryReceive().getOrNull()).isNull()
    fixture.assertTrackerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)

    fixture.updateOutput("ab<cursor>")

    assertThat(fixture.events.receive()).isEqualTo(RecordedConfirmedTyping('b', 1))
    assertThat(fixture.events.tryReceive().getOrNull()).isNull()
    fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
  }

  @Test
  fun `confirmed inputs are dispatched in their original order, with offsets corrected for earlier pending predictions`(): Unit =
    doTest { fixture ->
      // All three keys carry the same raw cursor offset because none of them was confirmed by real output yet.
      fixture.type('a', TerminalOffset.ZERO)
      fixture.type('b', TerminalOffset.ZERO)
      fixture.type('c', TerminalOffset.ZERO)
      fixture.assertTrackerState(hasInputSession = true, pendingEventsCount = 3, predictionsCount = 3)

      fixture.updateOutput("a<cursor>")

      assertThat(fixture.events.receive()).isEqualTo(RecordedConfirmedTyping('a', 0))
      fixture.assertTrackerState(hasInputSession = true, pendingEventsCount = 2, predictionsCount = 2)

      fixture.updateOutput("ab<cursor>")

      assertThat(fixture.events.receive()).isEqualTo(RecordedConfirmedTyping('b', 1))
      fixture.assertTrackerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)

      fixture.updateOutput("abc<cursor>")

      assertThat(fixture.events.receive()).isEqualTo(RecordedConfirmedTyping('c', 2))
      assertThat(fixture.events.tryReceive().getOrNull()).isNull()
      fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
    }

  @Test
  fun `typing and backspace events are confirmed in order`(): Unit = doTest { fixture ->
    fixture.type('a', TerminalOffset.ZERO)
    fixture.updateOutput("a<cursor>")
    assertThat(fixture.events.receive()).isEqualTo(RecordedConfirmedTyping('a', 0))

    fixture.press(KeyEvent.VK_BACK_SPACE, TerminalOffset.of(1))
    fixture.updateOutput("<cursor>")
    assertThat(fixture.events.receive()).isEqualTo(RecordedConfirmedBackspace)
    assertThat(fixture.events.tryReceive().getOrNull()).isNull()
  }

  @Test
  fun `navigation keys invalidate pending input with a mismatch`(): Unit = doTest { fixture ->
    for (keyCode in listOf(
      KeyEvent.VK_TAB,
      KeyEvent.VK_LEFT,
      KeyEvent.VK_RIGHT,
      KeyEvent.VK_UP,
      KeyEvent.VK_DOWN,
      KeyEvent.VK_HOME,
      KeyEvent.VK_END,
    )) {
      fixture.type('a', TerminalOffset.ZERO)
      fixture.assertTrackerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)

      fixture.press(keyCode, TerminalOffset.ZERO)

      assertThat(fixture.events.receive()).isEqualTo(RecordedMismatch)
      fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
    }

    fixture.updateOutput("a<cursor>")
    assertThat(fixture.events.receive()).isEqualTo(RecordedMismatch) // the leftover "a" no longer matches any pending typing
    assertThat(fixture.events.tryReceive().getOrNull()).isNull()
  }

  @Test
  fun `navigation key produces a mismatch even with nothing pending`(): Unit = doTest { fixture ->
    fixture.press(KeyEvent.VK_TAB, TerminalOffset.ZERO)

    assertThat(fixture.events.receive()).isEqualTo(RecordedMismatch)
    fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
  }

  @Test
  fun `cursor movement confirms typing after output content was updated`(): Unit = doTest { fixture ->
    fixture.type('a', TerminalOffset.ZERO)
    fixture.setOutput("a<cursor>")

    fixture.moveCursor(cursorColumn = 1)

    assertThat(fixture.events.receive()).isEqualTo(RecordedConfirmedTyping('a', 0))
    fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
  }

  @Test
  fun `backspace is confirmed after matching output confirmation`(): Unit = doTest { fixture ->
    fixture.updateOutput("a<cursor>")
    assertThat(fixture.events.receive()).isEqualTo(RecordedMismatch)

    fixture.press(KeyEvent.VK_BACK_SPACE, TerminalOffset.of(1))
    fixture.assertTrackerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)

    fixture.updateOutput("<cursor>")

    assertThat(fixture.events.receive()).isEqualTo(RecordedConfirmedBackspace)
    fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
  }

  @Test
  fun `backspace at line start is ignored without creating a session`(): Unit = doTest { fixture ->
    fixture.press(KeyEvent.VK_BACK_SPACE, TerminalOffset.ZERO)

    assertThat(fixture.events.tryReceive().getOrNull()).isNull()
    fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
  }

  @Test
  fun `control typed keys and modified backspace are ignored`(): Unit = doTest { fixture ->
    fixture.type('\n', TerminalOffset.ZERO)
    for (modifiersEx in listOf(
      InputEvent.ALT_DOWN_MASK,
      InputEvent.ALT_GRAPH_DOWN_MASK,
      InputEvent.CTRL_DOWN_MASK,
      InputEvent.META_DOWN_MASK,
      InputEvent.SHIFT_DOWN_MASK,
    )) {
      fixture.press(KeyEvent.VK_BACK_SPACE, TerminalOffset.ZERO, modifiersEx)
    }

    assertThat(fixture.events.tryReceive().getOrNull()).isNull()
    fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
  }

  @Test
  fun `unrelated key events are ignored`(): Unit = doTest { fixture ->
    fixture.keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_A, 'a', TerminalOffset.ZERO)
    fixture.keyEvent(KeyEvent.KEY_RELEASED, KeyEvent.VK_A, 'a', TerminalOffset.ZERO)

    assertThat(fixture.events.tryReceive().getOrNull()).isNull()
    fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
  }

  @Test
  fun `key events and output updates are ignored while not typing a command`(): Unit = doTest { fixture ->
    fixture.startCommandExecution()

    fixture.type('a', TerminalOffset.ZERO)
    fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)

    fixture.updateOutput("a<cursor>")

    assertThat(fixture.events.tryReceive().getOrNull()).isNull()
  }

  @Test
  fun `no confirmation within the timeout produces a mismatch`(): Unit = doTest { fixture ->
    fixture.type('a', TerminalOffset.ZERO)
    fixture.assertTrackerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)

    delay(1500.milliseconds)

    assertThat(fixture.events.receive()).isEqualTo(RecordedMismatch)
    fixture.assertTrackerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
  }

  @Test
  fun `multiple listeners all receive events, and a disposed listener stops receiving them`(): Unit = doTest { fixture ->
    val secondListenerScope = fixture.scope.childScope("SecondListener")
    val secondListenerEvents = Channel<TerminalTypingEvent>(Channel.UNLIMITED)
    fixture.tracker.addTypingListener(secondListenerScope.asDisposable(), object : TerminalTypingListener {
      override fun onTypingEvent(event: TerminalTypingEvent) {
        secondListenerEvents.trySend(event)
      }
    })

    fixture.press(KeyEvent.VK_TAB, TerminalOffset.ZERO)

    assertThat(fixture.events.receive()).isEqualTo(RecordedMismatch)
    assertThat(secondListenerEvents.receive()).isEqualTo(TerminalTypingEvent.Mismatch)

    secondListenerScope.cancel()

    fixture.press(KeyEvent.VK_TAB, TerminalOffset.ZERO)

    assertThat(fixture.events.receive()).isEqualTo(RecordedMismatch)
    assertThat(secondListenerEvents.tryReceive().getOrNull()).isNull()
  }

  @Test
  fun `a throwing listener does not prevent other listeners from being notified`(): Unit = doTest { fixture ->
    fixture.tracker.addTypingListener(fixture.scope.asDisposable(), object : TerminalTypingListener {
      override fun onTypingEvent(event: TerminalTypingEvent) {
        throw RuntimeException("boom")
      }
    })
    val laterEvents = Channel<TerminalTypingEvent>(Channel.UNLIMITED)
    fixture.tracker.addTypingListener(fixture.scope.asDisposable(), object : TerminalTypingListener {
      override fun onTypingEvent(event: TerminalTypingEvent) {
        laterEvents.trySend(event)
      }
    })

    val loggedError = LoggedErrorProcessor.executeAndReturnLoggedError {
      fixture.press(KeyEvent.VK_TAB, TerminalOffset.ZERO)
    }
    assertThat(loggedError.cause?.message).isEqualTo("boom")

    // The fixture's own listener, registered before the throwing one, still received the event.
    assertThat(fixture.events.receive()).isEqualTo(RecordedMismatch)
    // The listener registered after the throwing one also still received it.
    assertThat(laterEvents.receive()).isEqualTo(TerminalTypingEvent.Mismatch)
  }

  private fun doTest(test: suspend (Fixture) -> Unit): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    Fixture(project).use { fixture -> test(fixture) }
  }

  private class Fixture(project: Project) : AutoCloseable {
    val scope = terminalProjectScope(project).childScope("TerminalTypingTrackerTest")
    val model = TerminalTestUtil.createOutputModel()
    private val shellIntegration = TerminalShellIntegrationImpl(
      model, TerminalSessionModelImpl(), scope, LocalEelDescriptor, ShellName.of("unknown")
    )

    init {
      shellIntegration.onPromptStarted(TerminalOffset.ZERO)
      shellIntegration.onPromptFinished(TerminalOffset.ZERO)
    }

    val tracker = TerminalTypingTrackerImpl(project, model, shellIntegration, scope)
    val events = Channel<RecordedEvent>(Channel.UNLIMITED)

    init {
      tracker.addTypingListener(scope.asDisposable(), object : TerminalTypingListener {
        override fun onTypingEvent(event: TerminalTypingEvent) {
          events.trySend(toRecordedEvent(event))
        }
      })
    }

    private fun toRecordedEvent(event: TerminalTypingEvent): RecordedEvent = when (event) {
      is TerminalTypingEvent.Confirmed -> {
        val relativeOffset = (event.keyEvent.cursorOffset - model.startOffset).toInt()
        when (event.keyEvent.awtEvent.id) {
          KeyEvent.KEY_TYPED -> RecordedConfirmedTyping(event.keyEvent.awtEvent.keyChar, relativeOffset)
          else -> RecordedConfirmedBackspace
        }
      }
      TerminalTypingEvent.Mismatch -> RecordedMismatch
    }

    fun updateOutput(pattern: String) {
      model.updateContent(0, outputPattern(pattern))
      tracker.handleContentChanged()
    }

    fun setOutput(pattern: String) {
      model.updateContent(0, outputPattern(pattern))
    }

    fun moveCursor(cursorColumn: Int) {
      model.updateCursorPosition(0, cursorColumn)
      tracker.handleCursorOffsetChanged()
    }

    fun startCommandExecution() {
      shellIntegration.onCommandStarted(model.cursorOffset, "test-command")
    }

    fun type(char: Char, cursorOffset: TerminalOffset) {
      tracker.handleKeyEvent(
        TerminalKeyEventImpl(KeyEvent(Canvas(), KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, char), cursorOffset)
      )
    }

    fun press(keyCode: Int, cursorOffset: TerminalOffset, modifiersEx: Int = 0) {
      keyEvent(KeyEvent.KEY_PRESSED, keyCode, KeyEvent.CHAR_UNDEFINED, cursorOffset, modifiersEx)
    }

    fun keyEvent(id: Int, keyCode: Int, keyChar: Char, cursorOffset: TerminalOffset, modifiersEx: Int = 0) {
      tracker.handleKeyEvent(TerminalKeyEventImpl(KeyEvent(Canvas(), id, 0, modifiersEx, keyCode, keyChar), cursorOffset))
    }

    fun assertTrackerState(hasInputSession: Boolean, pendingEventsCount: Int, predictionsCount: Int) {
      assertThat(tracker.stateForTest()).isEqualTo(
        StateForTest(hasInputSession, pendingEventsCount, predictionsCount)
      )
    }

    override fun close() {
      scope.cancel()
    }
  }

  private sealed interface RecordedEvent

  private data class RecordedConfirmedTyping(val char: Char, val offset: Int) : RecordedEvent

  private data object RecordedConfirmedBackspace : RecordedEvent

  private data object RecordedMismatch : RecordedEvent
}
