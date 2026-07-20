@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.TerminalInputInterceptor
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.impl.TerminalEditorFactory
import com.intellij.terminal.frontend.view.impl.TerminalInput
import com.intellij.terminal.frontend.view.impl.TerminalKeyEncodingManager
import com.intellij.terminal.frontend.view.impl.TerminalKeyEventsHandler
import com.intellij.terminal.frontend.view.impl.TerminalKeyEventsHandlerImpl
import com.intellij.terminal.frontend.view.impl.TerminalOutputScrollingModel
import com.intellij.terminal.frontend.view.impl.TimedKeyEvent
import com.intellij.terminal.frontend.view.typeahead.TerminalTypeAhead
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalWriteBytesEvent
import org.jetbrains.plugins.terminal.session.impl.dto.KeyEventProcessingResultDto
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModelImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Component
import java.awt.event.KeyEvent
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.seconds

@RunWith(JUnit4::class)
internal class TerminalKeyEventsHandlerTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `keyTyped handled string result sends string and updates typeAhead`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      createFixture().use { fixture ->
        fixture.outputModel.updateContent(0, "abc", emptyList())
        fixture.outputModel.updateCursorPosition(0, 2)
        val event = typedKeyEvent(fixture.editor.contentComponent, 'x')
        fixture.session.enqueueResult(KeyEventProcessingResultDto.StringResult("x", shouldScrollToBottom = false))

        fixture.handler.keyTyped(event)

        assertThat(event.original.isConsumed).isTrue()
        assertThat(fixture.session.processedEvents).containsExactly(event.original)
        assertThat(awaitWrittenString(fixture.session)).isEqualTo("x")
        assertThat(fixture.typeAhead!!.typedStrings).containsExactly("x")
        assertThat(fixture.keyEventsFlow.replayCache.map { it.awtEvent }).containsExactly(event.original)
        assertThat(fixture.keyEventsFlow.replayCache.map { it.cursorOffset }).containsOnly(TerminalOffset.of(2))
      }
    }

  // String and byte processing results.

  @Test
  fun `keyPressed handled string result sends string without updating typeAhead`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      createFixture().use { fixture ->
        val event = pressedKeyEvent(fixture.editor.contentComponent, KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED)
        fixture.session.enqueueResult(KeyEventProcessingResultDto.StringResult("\u001B[D", shouldScrollToBottom = false))

        fixture.handler.keyPressed(event)

        assertThat(event.original.isConsumed).isTrue()
        assertThat(fixture.session.processedEvents).containsExactly(event.original)
        assertThat(awaitWrittenString(fixture.session)).isEqualTo("\u001B[D")
        assertThat(fixture.typeAhead!!.typedStrings).isEmpty()
      }
    }

  @Test
  fun `keyTyped handled bytes result sends exact bytes without updating typeAhead`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      createFixture().use { fixture ->
        val bytes = byteArrayOf('x'.code.toByte())
        val event = typedKeyEvent(fixture.editor.contentComponent, 'x')
        fixture.session.enqueueResult(KeyEventProcessingResultDto.BytesResult(bytes, shouldScrollToBottom = false))

        fixture.handler.keyTyped(event)

        assertThat(event.original.isConsumed).isTrue()
        assertThat(awaitWrittenBytes(fixture.session)).containsExactly('x'.code.toByte())
        assertThat(fixture.typeAhead!!.typedStrings).isEmpty()
      }
    }

  @Test
  fun `handled result scrolls terminal output to cursor`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val scrollingModel = RecordingScrollingModel()
      createFixture(scrollingModel = scrollingModel).use { fixture ->
        val event = typedKeyEvent(fixture.editor.contentComponent, 'x')
        fixture.session.enqueueResult(KeyEventProcessingResultDto.StringResult("x", shouldScrollToBottom = true))

        fixture.handler.keyTyped(event)

        assertThat(scrollingModel.scrollRequests).containsExactly(true)
      }
    }

  // A handled pressed event owns the following typed event.

  @Test
  fun `handled keyPressed suppresses following keyTyped event`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      createFixture().use { fixture ->
        val pressed = pressedKeyEvent(fixture.editor.contentComponent, KeyEvent.VK_A, 'a')
        val typed = typedKeyEvent(fixture.editor.contentComponent, 'b')
        fixture.session.enqueueResult(KeyEventProcessingResultDto.StringResult("a", shouldScrollToBottom = false))

        fixture.handler.keyPressed(pressed)
        fixture.handler.keyTyped(typed)

        assertThat(pressed.original.isConsumed).isTrue()
        assertThat(typed.original.isConsumed).isTrue()
        assertThat(fixture.session.processedEvents).containsExactly(pressed.original)
        assertThat(awaitWrittenString(fixture.session)).isEqualTo("a")
        assertThat(fixture.keyEventsFlow.replayCache.map { it.awtEvent }).containsExactly(pressed.original)
      }
    }

  @Test
  fun `unhandled keyPressed does not suppress following keyTyped event`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      createFixture().use { fixture ->
        val pressed = pressedKeyEvent(fixture.editor.contentComponent, KeyEvent.VK_A, 'a')
        val typed = typedKeyEvent(fixture.editor.contentComponent, 'b')
        fixture.session.enqueueResult(KeyEventProcessingResultDto.Unhandled)
        fixture.session.enqueueResult(KeyEventProcessingResultDto.StringResult("b", shouldScrollToBottom = false))

        fixture.handler.keyPressed(pressed)
        fixture.handler.keyTyped(typed)

        assertThat(pressed.original.isConsumed).isFalse()
        assertThat(typed.original.isConsumed).isTrue()
        assertThat(fixture.session.processedEvents).containsExactly(pressed.original, typed.original)
        assertThat(awaitWrittenString(fixture.session)).isEqualTo("b")
        assertThat(fixture.keyEventsFlow.replayCache.map { it.awtEvent }).containsExactly(pressed.original, typed.original)
      }
    }

  // Special pressed keys update type-ahead in addition to sending terminal input.

  @Test
  fun `unmodified backspace updates typeAhead`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      createFixture().use { fixture ->
        val event = pressedKeyEvent(fixture.editor.contentComponent, KeyEvent.VK_BACK_SPACE, '\b')
        fixture.session.enqueueResult(KeyEventProcessingResultDto.BytesResult(byteArrayOf(0x7F), shouldScrollToBottom = false))

        fixture.handler.keyPressed(event)

        assertThat(awaitWrittenBytes(fixture.session)).containsExactly(0x7F)
        assertThat(fixture.typeAhead!!.backspaceCalls).isEqualTo(1)
      }
    }

  @Test
  fun `modified backspace does not update typeAhead`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      createFixture().use { fixture ->
        val event = pressedKeyEvent(fixture.editor.contentComponent, KeyEvent.VK_BACK_SPACE, '\b', KeyEvent.CTRL_DOWN_MASK)
        fixture.session.enqueueResult(KeyEventProcessingResultDto.BytesResult(byteArrayOf(0x08), shouldScrollToBottom = false))

        fixture.handler.keyPressed(event)

        assertThat(awaitWrittenBytes(fixture.session)).containsExactly(0x08)
        assertThat(fixture.typeAhead!!.backspaceCalls).isZero()
      }
    }

  @Test
  fun `enter updates typeAhead with newline`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      createFixture().use { fixture ->
        val event = pressedKeyEvent(fixture.editor.contentComponent, KeyEvent.VK_ENTER, '\n')
        fixture.session.enqueueResult(KeyEventProcessingResultDto.StringResult("\r", shouldScrollToBottom = false))

        fixture.handler.keyPressed(event)

        assertThat(awaitWrittenString(fixture.session)).isEqualTo("\r")
        assertThat(fixture.typeAhead!!.typedStrings).containsExactly("\n")
      }
    }

  // Events received before the terminal session is available are replayed in order.

  @Test
  fun `keyTyped before session is ready is buffered and sent after session activation`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      createFixture(completeSessionImmediately = false).use { fixture ->
        val event = typedKeyEvent(fixture.editor.contentComponent, 'x')

        fixture.handler.keyTyped(event)

        assertThat(event.original.isConsumed).isTrue()
        assertThat(fixture.session.processedEvents).isEmpty()
        assertThat(fixture.session.inputEvents.tryReceive().getOrNull()).isNull()

        fixture.session.enqueueResult(KeyEventProcessingResultDto.StringResult("x", shouldScrollToBottom = false))
        fixture.activateSession()

        assertThat(awaitWrittenString(fixture.session)).isEqualTo("x")
        assertThat(fixture.session.processedEvents).containsExactly(event.original)
        assertThat(fixture.typeAhead!!.typedStrings).containsExactly("x")
      }
    }

  @Test
  fun `buffered handled keyPressed suppresses buffered keyTyped`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      createFixture(completeSessionImmediately = false).use { fixture ->
        val pressed = pressedKeyEvent(fixture.editor.contentComponent, KeyEvent.VK_A, 'a')
        val typed = typedKeyEvent(fixture.editor.contentComponent, 'b')

        fixture.handler.keyPressed(pressed)
        fixture.handler.keyTyped(typed)
        fixture.session.enqueueResult(KeyEventProcessingResultDto.StringResult("a", shouldScrollToBottom = false))
        fixture.activateSession()

        assertThat(pressed.original.isConsumed).isTrue()
        assertThat(typed.original.isConsumed).isTrue()
        assertThat(awaitWrittenString(fixture.session)).isEqualTo("a")
        assertThat(fixture.session.processedEvents).containsExactly(pressed.original)
      }
    }

  @Test
  fun `buffered keyTyped events are replayed in order`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      createFixture(completeSessionImmediately = false).use { fixture ->
        val first = typedKeyEvent(fixture.editor.contentComponent, 'a')
        val second = typedKeyEvent(fixture.editor.contentComponent, 'b')

        fixture.handler.keyTyped(first)
        fixture.handler.keyTyped(second)
        fixture.session.enqueueResult(KeyEventProcessingResultDto.StringResult("a", shouldScrollToBottom = false))
        fixture.session.enqueueResult(KeyEventProcessingResultDto.StringResult("b", shouldScrollToBottom = false))
        fixture.activateSession()

        assertThat(awaitWrittenString(fixture.session)).isEqualTo("a")
        assertThat(awaitWrittenString(fixture.session)).isEqualTo("b")
        assertThat(fixture.session.processedEvents).containsExactly(first.original, second.original)
        assertThat(fixture.typeAhead!!.typedStrings).containsExactly("a", "b")
        assertThat(fixture.keyEventsFlow.replayCache.map { it.awtEvent }).containsExactly(first.original, second.original)
      }
    }

  @Test
  fun `buffered unhandled keyPressed does not suppress buffered keyTyped`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      createFixture(completeSessionImmediately = false).use { fixture ->
        val pressed = pressedKeyEvent(fixture.editor.contentComponent, KeyEvent.VK_A, 'a')
        val typed = typedKeyEvent(fixture.editor.contentComponent, 'a')

        fixture.handler.keyPressed(pressed)
        fixture.handler.keyTyped(typed)
        fixture.session.enqueueResult(KeyEventProcessingResultDto.Unhandled)
        fixture.session.enqueueResult(KeyEventProcessingResultDto.StringResult("a", shouldScrollToBottom = false))
        fixture.activateSession()

        assertThat(awaitWrittenString(fixture.session)).isEqualTo("a")
        assertThat(fixture.session.processedEvents).containsExactly(pressed.original, typed.original)
        assertThat(pressed.original.isConsumed).isTrue()
        assertThat(typed.original.isConsumed).isTrue()
      }
    }

  @Test
  fun `event received after session activation follows buffered events`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      createFixture(completeSessionImmediately = false).use { fixture ->
        val buffered = typedKeyEvent(fixture.editor.contentComponent, 'a')
        val afterActivation = typedKeyEvent(fixture.editor.contentComponent, 'b')

        fixture.handler.keyTyped(buffered)
        fixture.session.enqueueResult(KeyEventProcessingResultDto.StringResult("a", shouldScrollToBottom = false))
        fixture.session.enqueueResult(KeyEventProcessingResultDto.StringResult("b", shouldScrollToBottom = false))
        fixture.activateSession()
        fixture.handler.keyTyped(afterActivation)

        assertThat(awaitWrittenString(fixture.session)).isEqualTo("a")
        assertThat(awaitWrittenString(fixture.session)).isEqualTo("b")
        assertThat(fixture.session.processedEvents).containsExactly(buffered.original, afterActivation.original)
        assertThat(buffered.original.isConsumed).isTrue()
        assertThat(afterActivation.original.isConsumed).isTrue()
        assertThat(fixture.keyEventsFlow.replayCache.map { it.awtEvent }).containsExactly(buffered.original, afterActivation.original)
      }
    }

  // Interceptors can reserve an event before it reaches the terminal session.

  @Test
  fun `intercepted keyTyped is consumed and not sent to session`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      var interceptedEvent: KeyEvent? = null
      val interceptor = TerminalInputInterceptor { event ->
        interceptedEvent = event
        true
      }
      createFixture(inputInterceptors = listOf(interceptor)).use { fixture ->
        val event = typedKeyEvent(fixture.editor.contentComponent, 'x')

        fixture.handler.keyTyped(event)

        assertThat(event.original.isConsumed).isTrue()
        assertThat(interceptedEvent).isSameAs(event.original)
        assertThat(fixture.session.processedEvents).isEmpty()
        assertThat(fixture.session.inputEvents.tryReceive().getOrNull()).isNull()
        assertThat(fixture.keyEventsFlow.replayCache.map { it.awtEvent }).containsExactly(event.original)
      }
    }

  // Optional collaborators must not be required to send terminal input.

  @Test
  fun `keyTyped sends string when typeAhead is absent`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      createFixture(withTypeAhead = false).use { fixture ->
        val event = typedKeyEvent(fixture.editor.contentComponent, 'x')
        fixture.session.enqueueResult(KeyEventProcessingResultDto.StringResult("x", shouldScrollToBottom = false))

        fixture.handler.keyTyped(event)

        assertThat(event.original.isConsumed).isTrue()
        assertThat(awaitWrittenString(fixture.session)).isEqualTo("x")
      }
    }

  private fun createFixture(
    completeSessionImmediately: Boolean = true,
    inputInterceptors: List<TerminalInputInterceptor> = emptyList(),
    scrollingModel: TerminalOutputScrollingModel? = null,
    withTypeAhead: Boolean = true,
  ): Fixture {
    val scope = terminalProjectScope(project).childScope("TerminalKeyEventsHandlerTest")
    val session = RecordingTerminalSession(scope)
    val sessionDeferred =
      if (completeSessionImmediately) CompletableDeferred<TerminalSession>(session) else CompletableDeferred()
    val keyEventsFlow = MutableSharedFlow<TerminalKeyEvent>(replay = 16, extraBufferCapacity = 16)
    val editor = TerminalEditorFactory.createOutputEditor(project, JBTerminalSystemSettingsProvider(), scope)
    val outputModel = MutableTerminalOutputModelImpl(editor.document, maxOutputLength = 0)
    val typeAhead = if (withTypeAhead) RecordingTypeAhead() else null
    val sessionModel = TerminalSessionModelImpl()
    val handler = TerminalKeyEventsHandlerImpl(
      keyEventsFlow = keyEventsFlow,
      editor = editor,
      terminalInput = TerminalInput(
        terminalSessionDeferred = sessionDeferred,
        sessionModel = sessionModel,
        startupFusInfo = null,
        coroutineScope = scope,
        encodingManager = TerminalKeyEncodingManager(sessionModel, scope),
      ),
      scrollingModel = scrollingModel,
      outputModel = outputModel,
      typeAhead = typeAhead,
      inputInterceptors = { inputInterceptors },
      sessionDeferred = sessionDeferred,
      coroutineScope = scope,
    )
    return Fixture(
      scope = scope,
      session = session,
      sessionDeferred = sessionDeferred,
      keyEventsFlow = keyEventsFlow,
      editor = editor,
      outputModel = outputModel,
      typeAhead = typeAhead,
      handler = handler,
    )
  }

  private class Fixture(
    private val scope: CoroutineScope,
    val session: RecordingTerminalSession,
    val sessionDeferred: CompletableDeferred<TerminalSession>,
    val keyEventsFlow: MutableSharedFlow<TerminalKeyEvent>,
    val editor: EditorEx,
    val outputModel: MutableTerminalOutputModelImpl,
    val typeAhead: RecordingTypeAhead?,
    val handler: TerminalKeyEventsHandler,
  ) : AutoCloseable {
    fun activateSession() {
      check(sessionDeferred.complete(session))
    }
    override fun close() {
      scope.cancel()
    }
  }

  private class RecordingTerminalSession(
    override val coroutineScope: CoroutineScope,
  ) : TerminalSession {
    private val processResults = ArrayDeque<KeyEventProcessingResultDto>()
    val processedEvents = mutableListOf<KeyEvent>()
    val inputEvents = Channel<TerminalInputEvent>(Channel.UNLIMITED)

    fun enqueueResult(result: KeyEventProcessingResultDto) {
      processResults.addLast(result)
    }

    override fun processMouseEvent(e: java.awt.event.MouseEvent, x: Int, y: Int): ByteArray? {
      error("Unexpected processMouseEvent call")
    }

    override fun processKeyEvent(e: KeyEvent): KeyEventProcessingResultDto {
      processedEvents += e
      return processResults.removeFirstOrNull() ?: KeyEventProcessingResultDto.Unhandled
    }

    override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> = inputEvents

    override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> = emptyFlow()

    override suspend fun hasRunningCommands(): Boolean = false

    override val isClosed: Boolean = false
    override val eelDescriptor: EelDescriptor = LocalEelDescriptor
    override val processId: Long = 42L
  }

  private class RecordingTypeAhead : TerminalTypeAhead {
    val typedStrings = mutableListOf<String>()
    var backspaceCalls = 0

    override fun type(string: String) {
      typedStrings += string
    }

    override fun backspace() {
      backspaceCalls++
    }
  }

  private class RecordingScrollingModel : TerminalOutputScrollingModel {
    val scrollRequests = mutableListOf<Boolean>()

    override fun scrollToCursor(force: Boolean) {
      scrollRequests += force
    }
  }

  companion object {
    private fun pressedKeyEvent(source: Component, keyCode: Int, keyChar: Char, modifiersEx: Int = 0): TimedKeyEvent {
      return TimedKeyEvent(KeyEvent(source, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiersEx, keyCode, keyChar))
    }

    private fun typedKeyEvent(source: Component, keyChar: Char): TimedKeyEvent {
      return TimedKeyEvent(KeyEvent(source, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, keyChar))
    }

    private suspend fun awaitWrittenBytes(session: RecordingTerminalSession): ByteArray {
      return withTimeout(5.seconds) {
        (session.inputEvents.receive() as TerminalWriteBytesEvent).bytes
      }
    }

    private suspend fun awaitWrittenString(session: RecordingTerminalSession): String {
      return String(awaitWrittenBytes(session), StandardCharsets.UTF_8)
    }
  }
}
