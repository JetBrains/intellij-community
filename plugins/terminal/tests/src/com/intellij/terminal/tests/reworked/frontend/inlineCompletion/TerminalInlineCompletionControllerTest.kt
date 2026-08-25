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
import com.intellij.terminal.frontend.view.impl.TerminalTypingTrackerImpl
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
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModelImpl
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalShellIntegrationImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Canvas
import java.awt.event.KeyEvent

/**
 * Covers only the controller's own responsibility: translating [com.intellij.terminal.frontend.view.impl.TerminalTypingTracker]
 * events into actual
 * [com.intellij.codeInsight.inline.completion.InlineCompletion] actions on the editor.
 * The typing/output matching logic itself is covered by
 * `com.intellij.terminal.tests.reworked.frontend.typing.TerminalTypingTrackerTest`.
 */
@RunWith(JUnit4::class)
internal class TerminalInlineCompletionControllerTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `typing is forwarded after matching output confirmation`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.type('a', TerminalOffset.ZERO)
      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()

      fixture.updateOutput("a<cursor>")

      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedTyping('a', 0))
      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
    }
  }

  @Test
  fun `typing uses the cursor offset carried by the confirmed key event`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.updateOutput("foo<cursor>")

      fixture.type('a', TerminalOffset.of(3))
      fixture.updateOutput("fooa<cursor>")

      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedTyping('a', 3))
    }
  }

  @Test
  fun `backspace is forwarded after matching output confirmation`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.updateOutput("a<cursor>")

      fixture.press(KeyEvent.VK_BACK_SPACE, TerminalOffset.of(1))
      fixture.updateOutput("<cursor>")

      assertThat(fixture.provider.events.receive()).isEqualTo(RecordedBackspace)
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
  fun `mismatch cancels an active inline completion session`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.type('a', TerminalOffset.ZERO)
      fixture.updateOutput("a<cursor>")
      val event = fixture.provider.events.receive()

      fixture.press(KeyEvent.VK_TAB, TerminalOffset.of(1))

      assertThat(fixture.provider.cancellations.receive()).isEqualTo(event)
    }
  }

  @Test
  fun `mismatch without an active inline completion session is a no-op`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    createFixture().use { fixture ->
      fixture.press(KeyEvent.VK_TAB, TerminalOffset.ZERO)

      assertThat(fixture.provider.events.tryReceive().getOrNull()).isNull()
      assertThat(fixture.provider.cancellations.tryReceive().getOrNull()).isNull()
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

    init {
      shellIntegration.onPromptStarted(TerminalOffset.ZERO)
      shellIntegration.onPromptFinished(TerminalOffset.ZERO)
    }

    private val typingTracker = TerminalTypingTrackerImpl(project, model, shellIntegration, scope)
    val controller = TerminalInlineCompletionController(editor, model, typingTracker, scope)

    init {
      InlineCompletionHandler.registerTestHandler(provider, scope.asDisposable())
      controller.install()
    }

    fun updateOutput(pattern: String) {
      model.updateContent(0, outputPattern(pattern))
      typingTracker.handleContentChanged()
    }

    fun type(char: Char, cursorOffset: TerminalOffset) {
      typingTracker.handleKeyEvent(
        TerminalKeyEventImpl(KeyEvent(Canvas(), KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, char), cursorOffset)
      )
    }

    fun press(keyCode: Int, cursorOffset: TerminalOffset, modifiersEx: Int = 0) {
      typingTracker.handleKeyEvent(TerminalKeyEventImpl(KeyEvent(Canvas(), KeyEvent.KEY_PRESSED, 0, modifiersEx, keyCode, KeyEvent.CHAR_UNDEFINED), cursorOffset))
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
