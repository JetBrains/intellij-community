package com.intellij.terminal.tests.reworked.frontend.inlineCompletion

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.codeInsight.inline.completion.editor.InlineCompletionEditorType
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.TerminalKeyEventImpl
import com.intellij.terminal.frontend.view.impl.TerminalEditorFactory
import com.intellij.terminal.frontend.view.inlineCompletion.TerminalInlineCompletionController
import com.intellij.terminal.frontend.view.inlineCompletion.TerminalInlineCompletionController.StateForTest
import com.intellij.terminal.tests.reworked.util.outputPattern
import com.intellij.terminal.tests.reworked.util.updateContent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.asDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalShellIntegrationImpl
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModelImpl
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.session.ShellName
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

@RunWith(JUnit4::class)
internal class TerminalInlineCompletionControllerTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `typing is forwarded after matching output confirmation`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.type('a', TerminalOffset.ZERO)
      fixture.assertControllerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)
      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()

      fixture.updateOutput("a<cursor>")

      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedTyping('a', 0))
      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
      fixture.assertControllerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
    }
  }

  @Test
  fun `mismatching output clears pending input`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.type('a', TerminalOffset.ZERO)
      fixture.assertControllerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)
      fixture.updateOutput("x<cursor>")

      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
      fixture.assertControllerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)

      fixture.type('y', TerminalOffset.of(1))
      fixture.assertControllerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)
      fixture.updateOutput("xy<cursor>")

      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedTyping('y', 1))
      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
      fixture.assertControllerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
    }
  }

  @Test
  fun `partial output confirmation dispatches only confirmed input`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.type('a', TerminalOffset.ZERO)
      fixture.type('b', TerminalOffset.ZERO)
      fixture.assertControllerState(hasInputSession = true, pendingEventsCount = 2, predictionsCount = 2)

      fixture.updateOutput("a<cursor>")

      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedTyping('a', 0))
      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
      fixture.assertControllerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)

      fixture.updateOutput("ab<cursor>")

      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedTyping('b', 1))
      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
      fixture.assertControllerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
    }
  }

  @Test
  fun `mismatch after partial confirmation clears every pending input`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.type('a', TerminalOffset.ZERO)
      fixture.type('b', TerminalOffset.ZERO)
      fixture.assertControllerState(hasInputSession = true, pendingEventsCount = 2, predictionsCount = 2)

      fixture.updateOutput("ax<cursor>")

      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
      fixture.assertControllerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
    }
  }

  @Test
  fun `output received before input is not used as its confirmation`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.updateOutput("a<cursor>")

      fixture.type('b', TerminalOffset.of(1))

      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
      fixture.assertControllerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)

      fixture.updateOutput("ab<cursor>")

      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedTyping('b', 1))
      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
      fixture.assertControllerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
    }
  }

  @Test
  fun `confirmed inputs are dispatched in their original order`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.type('a', TerminalOffset.ZERO)
      fixture.type('b', TerminalOffset.ZERO)
      fixture.type('c', TerminalOffset.ZERO)
      fixture.assertControllerState(hasInputSession = true, pendingEventsCount = 3, predictionsCount = 3)

      fixture.updateOutput("a<cursor>")

      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedTyping('a', 0))
      fixture.assertControllerState(hasInputSession = true, pendingEventsCount = 2, predictionsCount = 2)

      fixture.updateOutput("ab<cursor>")

      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedTyping('b', 1))
      fixture.assertControllerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)

      fixture.updateOutput("abc<cursor>")

      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedTyping('c', 2))
      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
      fixture.assertControllerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
    }
  }

  @Test
  fun `typing and backspace events are forwarded in order`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.type('a', TerminalOffset.ZERO)
      fixture.updateOutput("a<cursor>")
      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedTyping('a', 0))

      fixture.press(KeyEvent.VK_BACK_SPACE, TerminalOffset.of(1))
      fixture.updateOutput("<cursor>")
      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedBackspace)
      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
    }
  }

  @Test
  fun `tab and navigation keys invalidate pending input`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
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
        fixture.assertControllerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)

        fixture.press(keyCode, TerminalOffset.ZERO)

        fixture.assertControllerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
      }

      fixture.updateOutput("a<cursor>")
      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
    }
  }

  @Test
  fun `tab cancels active inline completion`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.type('a', TerminalOffset.ZERO)
      fixture.updateOutput("a<cursor>")
      val event = fixture.provider.events.receive()

      fixture.press(KeyEvent.VK_TAB, TerminalOffset.of(1))

      assertThat(fixture.provider.cancellations.receive()).isEqualTo(event)
    }
  }

  @Test
  fun `unexpected output update cancels active inline completion`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.type('a', TerminalOffset.ZERO)
      fixture.updateOutput("a<cursor>")
      val event = fixture.provider.events.receive()

      fixture.updateOutput("ax<cursor>")

      assertThat(fixture.provider.cancellations.receive()).isEqualTo(event)
    }
  }

  @Test
  fun `cursor movement confirms typing after output content was updated`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.type('a', TerminalOffset.ZERO)
      fixture.setOutput("a<cursor>")

      fixture.moveCursor(cursorColumn = 1)

      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedTyping('a', 0))
      fixture.assertControllerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
    }
  }

  @Test
  fun `typing uses cursor offset from input event`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.updateOutput("foo<cursor>")

      fixture.type('a', TerminalOffset.of(3))
      fixture.updateOutput("fooa<cursor>")

      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedTyping('a', 3))
      fixture.assertControllerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
    }
  }

  @Test
  fun `backspace is forwarded after matching output confirmation`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.updateOutput("a<cursor>")

      fixture.press(KeyEvent.VK_BACK_SPACE, TerminalOffset.of(1))
      fixture.assertControllerState(hasInputSession = true, pendingEventsCount = 1, predictionsCount = 1)

      fixture.updateOutput("<cursor>")

      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedBackspace)
      fixture.assertControllerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
    }
  }

  @Test
  fun `backspace at line start is ignored without creating a session`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.press(KeyEvent.VK_BACK_SPACE, TerminalOffset.ZERO)

      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
      fixture.assertControllerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
    }
  }

  @Test
  fun `control typed keys and modified backspace are ignored`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
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

      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
      fixture.assertControllerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
    }
  }

  @Test
  fun `unrelated key events are ignored`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.keyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_A, 'a', TerminalOffset.ZERO)
      fixture.keyEvent(KeyEvent.KEY_RELEASED, KeyEvent.VK_A, 'a', TerminalOffset.ZERO)

      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
      fixture.assertControllerState(hasInputSession = false, pendingEventsCount = 0, predictionsCount = 0)
    }
  }

  private fun createFixture(): Fixture = Fixture(project)

  private class Fixture(project: Project) : AutoCloseable {
    private val scope = terminalProjectScope(project).childScope("TerminalInlineCompletionControllerTest")
    val provider = RecordingInlineCompletionProvider()
    val editor = TerminalEditorFactory.createOutputEditor(project, JBTerminalSystemSettingsProvider(), scope)
    val model = MutableTerminalOutputModelImpl(editor.document, maxOutputLength = 0)
    private val shellIntegration = TerminalShellIntegrationImpl(
      model, TerminalSessionModelImpl(), scope, LocalEelDescriptor, ShellName.of("unknown")
    )
    val controller = TerminalInlineCompletionController(project, editor, model, shellIntegration, scope)

    init {
      shellIntegration.onPromptStarted(TerminalOffset.ZERO)
      shellIntegration.onPromptFinished(TerminalOffset.ZERO)
      InlineCompletionHandler.registerTestHandler(provider, scope.asDisposable())
      controller.install()
    }

    fun updateOutput(pattern: String) {
      model.updateContent(0, outputPattern(pattern))
      controller.handleContentChanged()
    }

    fun setOutput(pattern: String) {
      model.updateContent(0, outputPattern(pattern))
    }

    fun moveCursor(cursorColumn: Int) {
      model.updateCursorPosition(0, cursorColumn)
      controller.handleCursorOffsetChanged()
    }

    fun type(char: Char, cursorOffset: TerminalOffset) {
      controller.handleKeyEvent(
        TerminalKeyEventImpl(KeyEvent(Canvas(), KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, char), cursorOffset)
      )
    }

    fun press(keyCode: Int, cursorOffset: TerminalOffset, modifiersEx: Int = 0) {
      keyEvent(KeyEvent.KEY_PRESSED, keyCode, KeyEvent.CHAR_UNDEFINED, cursorOffset, modifiersEx)
    }

    fun keyEvent(id: Int, keyCode: Int, keyChar: Char, cursorOffset: TerminalOffset, modifiersEx: Int = 0) {
      controller.handleKeyEvent(TerminalKeyEventImpl(KeyEvent(Canvas(), id, 0, modifiersEx, keyCode, keyChar), cursorOffset))
    }

    fun assertControllerState(hasInputSession: Boolean, pendingEventsCount: Int, predictionsCount: Int) {
      assertThat(controller.stateForTest()).isEqualTo(
        StateForTest(hasInputSession, pendingEventsCount, predictionsCount)
      )
    }

    override fun close() {
      scope.cancel()
    }
  }

  private class RecordingInlineCompletionProvider : InlineCompletionProvider {
    val events = Channel<RecordedEvent>(Channel.UNLIMITED)
    val cancellations = Channel<RecordedEvent>(Channel.UNLIMITED)

    override val id = InlineCompletionProviderID("TerminalInlineCompletionControllerTest")

    override fun isEditorTypeSupported(editorType: InlineCompletionEditorType): Boolean = true

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
      return event is InlineCompletionEvent.DocumentChange || event is InlineCompletionEvent.Backspace
    }

    override suspend fun getSuggestion(request: com.intellij.codeInsight.inline.completion.InlineCompletionRequest): InlineCompletionSuggestion {
      val recordedEvent = when (val event = request.event) {
        is InlineCompletionEvent.DocumentChange -> {
          val typing = event.typing as TypingEvent.OneSymbol
          RecordedTyping(typing.typed.single(), typing.range.startOffset)
        }
        is InlineCompletionEvent.Backspace -> RecordedBackspace
        else -> error("Unexpected event: $event")
      }
      events.send(recordedEvent)
      try {
        awaitCancellation()
      }
      finally {
        cancellations.trySend(recordedEvent)
      }
    }
  }

  private sealed interface RecordedEvent

  private data class RecordedTyping(val char: Char, val offset: Int) : RecordedEvent

  private data object RecordedBackspace : RecordedEvent
}
