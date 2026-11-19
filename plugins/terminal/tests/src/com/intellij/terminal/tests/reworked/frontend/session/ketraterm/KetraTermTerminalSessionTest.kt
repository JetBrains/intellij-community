// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session.ketraterm

import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.ketraterm.KetraTermOutputProjector
import com.intellij.terminal.frontend.session.ketraterm.KetraTermTerminalSession
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import io.github.ketraterm.input.event.TerminalKeyEvent
import io.github.ketraterm.input.event.TerminalPasteEvent
import io.github.ketraterm.session.TerminalInputAdmission
import io.github.ketraterm.session.TerminalSessionState
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.cancel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.impl.TerminalClearBufferEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCommandStartedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalPromptFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalResizeEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSessionTerminatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalStateChangedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalWriteBytesEvent
import org.jetbrains.plugins.terminal.session.TerminalGridSize
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.view.impl.TerminalSendTextOptions
import org.junit.jupiter.api.Test

@TestApplication
@SystemProperty(propertyKey = "ketraterm.experimental.enableDocumentModel", propertyValue = "true")
internal class KetraTermTerminalSessionTest {
  @Test
  fun `fragmented shell commands preserve the prompt boundary`() = runSession { session, connector, events ->
    val osc = "\u001b]1341;"
    val stream = "${osc}initialized;current_directory=2f746d70;history_path=\u0007" +
                 "${osc}prompt_started\u0007PROMPT>${osc}prompt_finished\u001b\\echo typed"
    stream.forEach { connector.feed(it.toString()) }
    var beforePrompt = ""
    while (true) {
      when (val event = events.receive()) {
        TerminalPromptFinishedEvent -> break
        is TerminalContentUpdatedEvent -> beforePrompt = event.text
        else -> Unit
      }
    }
    assertThat(beforePrompt).contains("PROMPT>").doesNotContain("echo typed")
    val command = session.commandLine.first { it?.commandText == "echo typed" }
    assertThat(command!!.cursorOffset).isEqualTo("echo typed".length)
    assertThat(session.shellState.recordCount()).isEqualTo(1)
    assertThat(session.shellState.currentWorkingDirectoryUri()).isEqualTo("file:///tmp")
  }

  @Test
  fun `platform bytes preserve controls and native input order`() = runSession { session, connector, _ ->
    val received = ByteArrayOutputStream()
    val rawWritten = CompletableDeferred<Unit>()
    val finished = CompletableDeferred<Unit>()
    val bytes = byteArrayOf(27, 91, 50, 52, 126, 101, 0, 9, 13, -1)
    connector.responseHandler = { data ->
      synchronized(received) {
        received.write(data)
        if (received.size() >= 15) rawWritten.complete(Unit)
        if (received.size() >= 19) finished.complete(Unit)
      }
    }
    assertThat(session.sendText(TerminalSendTextOptions("first", false, false))).isTrue()
    session.getInputChannel().send(TerminalWriteBytesEvent(bytes))
    rawWritten.await()
    assertThat(session.sendText(TerminalSendTextOptions("last", false, false))).isTrue()
    finished.await()
    assertThat(received.toByteArray()).isEqualTo("first".toByteArray() + bytes + "last".toByteArray())
  }

  @Test
  fun `the host clear operation removes local output without sending input`() = runSession { session, connector, events ->
    connector.feed("retained\r\nvisible")
    while (true) {
      val event = events.receive()
      if (event is TerminalContentUpdatedEvent && event.text.endsWith("visible")) break
    }
    val generation = session.terminalSession.renderGeneration.value
    connector.responseHandler = { error("A local clear must not send bytes") }
    session.getInputChannel().send(TerminalClearBufferEvent())
    session.terminalSession.renderGeneration.first { it > generation }
    val projector = KetraTermOutputProjector()
    session.terminalSession.readRenderFrameForAbsoluteRange(0, Long.MAX_VALUE) { frame ->
      assertThat(projector.project(frame)?.text.orEmpty()).doesNotContain("retained", "visible")
    }
    connector.responseHandler = null
    connector.feed("after-clear")
    while (true) {
      val event = events.receive()
      if (event is TerminalContentUpdatedEvent && event.text.endsWith("after-clear")) break
    }
  }

  @Test
  fun `completion edits reject output that arrives after capture`() = runSession { session, connector, _ ->
    connector.feed("\u001b]1341;prompt_started\u0007>\u001b]1341;prompt_finished\u0007git ch")
    session.commandLine.first { it?.commandText == "git ch" }
    val expected = requireNotNull(session.terminalSession.captureCommandEdit())
    connector.feed("e")
    session.commandLine.first { it?.commandText == "git che" }
    connector.responseHandler = { error("A stale completion must not send bytes") }
    assertThat(session.terminalSession.submitInput(expected, listOf(TerminalKeyEvent.text("checkout"))))
      .isEqualTo(TerminalInputAdmission.STALE_CONTEXT)
    expected.cancel()
  }

  @Test
  fun `character transport preserves a split supplementary scalar`() = runSession { _, connector, events ->
    connector.feed("\uD83D")
    connector.feed("\uDE00done")
    while (true) {
      val event = events.receive()
      if (event is TerminalContentUpdatedEvent && event.text.contains("done")) {
        assertThat(event.text).contains("😀done").doesNotContain("�")
        break
      }
    }
  }

  @Test
  fun `typed text and paste retain submission order`() = runSession { session, connector, _ ->
    val received = ByteArrayOutputStream()
    val completed = CompletableDeferred<Unit>()
    connector.responseHandler = { bytes ->
      synchronized(received) {
        received.write(bytes)
        if (received.size() >= 15) completed.complete(Unit)
      }
    }
    assertThat(session.sendText(TerminalSendTextOptions("first", false, false))).isTrue()
    session.terminalSession.encodePaste(TerminalPasteEvent("middle"))
    assertThat(session.sendText(TerminalSendTextOptions("last", false, false))).isTrue()
    completed.await()
    assertThat(received.toString(Charsets.UTF_8)).isEqualTo("firstmiddlelast")
  }

  @Test
  fun `multiline paste follows the active paste mode`() = runSession { session, connector, events ->
    val text = "first\r\nsecond\nthird\rfourth"
    suspend fun checkPaste(expected: String) {
      val received = ByteArrayOutputStream()
      val completed = CompletableDeferred<String>()
      connector.responseHandler = { bytes ->
        received.write(bytes)
        if (received.size() >= expected.length) completed.complete(received.toString(Charsets.UTF_8))
      }
      assertThat(session.sendText(TerminalSendTextOptions(text, false, true))).isTrue()
      assertThat(completed.await()).isEqualTo(expected)
    }
    checkPaste("first\rsecond\rthird\rfourth")
    connector.feed("\u001b[?2004h")
    while (true) {
      val event = events.receive()
      if (event is TerminalStateChangedEvent && event.state.isBracketedPasteMode) break
    }
    checkPaste("\u001b[200~${text}\u001b[201~")
  }

  @Test
  fun `unsupported controls fail before any bytes are submitted`() = runSession { session, connector, _ ->
    val received = ByteArrayOutputStream()
    val completed = CompletableDeferred<Unit>()
    connector.responseHandler = { bytes ->
      synchronized(received) {
        received.write(bytes)
        if (received.toString(Charsets.UTF_8).endsWith("accepted")) completed.complete(Unit)
      }
    }
    for (text in listOf("prefix\b", "prefix\u007f", "prefix\u001b", "prefix\t", "prefix\uD83D")) {
      val options = TerminalSendTextOptions(text, false, false, sendEndKeyBeforeText = true)
      assertThat(session.sendText(options)).describedAs(text).isFalse()
    }
    assertThat(session.sendText(TerminalSendTextOptions("accepted", false, false))).isTrue()
    completed.await()
    assertThat(received.toString(Charsets.UTF_8)).isEqualTo("accepted")
  }

  @Test
  fun `unchanged wrapped rows do not create another content event`() = runSession { session, connector, events ->
    connector.feed("w".repeat(200) + "END")
    while (true) {
      val event = events.receive()
      if (event is TerminalContentUpdatedEvent && event.text.endsWith("END")) break
    }
    val projector = KetraTermOutputProjector()
    session.terminalSession.readRenderFrameForAbsoluteRange(0, Long.MAX_VALUE) { frame ->
      assertThat(projector.project(frame)!!.text).isEqualTo("w".repeat(200) + "END")
    }
    session.terminalSession.readRenderFrameForAbsoluteRange(0, Long.MAX_VALUE) { frame ->
      assertThat(projector.project(frame)).isNull()
    }
  }

  @Test
  fun `the projected cursor includes retained history`() = runSession { session, connector, events ->
    connector.feed((0 until 50).joinToString("") { "line$it\r\n" } + "END")
    while (true) {
      val event = events.receive()
      if (event is TerminalContentUpdatedEvent && event.text.endsWith("END")) {
        assertThat(event.cursorLogicalLineIndex).isEqualTo(50)
        assertThat(event.cursorColumnIndex).isEqualTo(3)
        assertThat(event.screenTopLogicalLineIndex).isEqualTo(27)
        break
      }
    }
  }

  @Test
  fun `resize reaches the transport and native grid`() = runSession { session, connector, _ ->
    withContext(Dispatchers.IO) { connector.awaitResize(1000) }
    session.getInputChannel().send(TerminalResizeEvent(TerminalGridSize(113, 37)))
    val size = withContext(Dispatchers.IO) { connector.awaitResize(1000) }
    assertThat(size!!.columns).isEqualTo(113)
    assertThat(size.rows).isEqualTo(37)
    session.terminalSession.readRenderFrame { frame ->
      assertThat(frame.columns).isEqualTo(113)
      assertThat(frame.rows).isEqualTo(37)
    }
  }

  @Test
  fun `scope cancellation closes the session and connector`() = runSession { session, connector, _ ->
    session.coroutineScope.cancel()
    session.terminalSession.state.first { it is TerminalSessionState.Closed }
    assertThat(connector.isConnected).isFalse()
  }

  @Test
  fun `a second collector fails without taking events from the view`() = runSession { session, connector, events ->
    events.receive()
    var failure: IllegalStateException? = null
    try {
      session.getOutputFlow().first()
    }
    catch (error: IllegalStateException) {
      failure = error
    }
    assertThat(failure).isNotNull()
    connector.feed("survives")
    while (true) {
      val event = events.receive()
      if (event is TerminalContentUpdatedEvent && event.text.endsWith("survives")) break
    }
    assertThat(session.isClosed).isFalse()
  }

  @Test
  fun `closed sessions retain the final output for a late collector`(): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm late collector", Dispatchers.Default)
    val (base, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      projectFixture.get(), scope, emulatorType = TerminalEmulatorType.KetraTerm, isLowLevelSession = true,
    )
    val session = base as KetraTermTerminalSession
    try {
      connector.feed("final")
      val codes = IntArray(80)
      val attrs = LongArray(80)
      val flags = IntArray(80)
      session.terminalSession.renderGeneration.first {
        var received = false
        session.terminalSession.readRenderFrame { frame ->
          frame.copyLine(0, codes, attrWords = attrs, flags = flags)
          received = codes[0] == 'f'.code && codes[4] == 'l'.code
        }
        received
      }
      session.terminalSession.close()
      session.coroutineScope.coroutineContext.job.join()
      val events = session.getOutputFlow().toList().flatten()
      assertThat(events.filterIsInstance<TerminalContentUpdatedEvent>().last().text).isEqualTo("final")
      assertThat(events.last()).isEqualTo(TerminalSessionTerminatedEvent)
    }
    finally {
      connector.close()
      scope.cancel()
    }
  }

  @Test
  fun `document output waits for the synchronized timeout`(): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm synchronized document", Dispatchers.Default)
    val (base, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      projectFixture.get(), scope, emulatorType = TerminalEmulatorType.KetraTerm, useDocumentRenderer = true,
      isLowLevelSession = true,
    )
    val session = base as KetraTermTerminalSession
    val completed = CompletableDeferred<Unit>()
    scope.launch(Dispatchers.Unconfined) {
      session.getOutputFlow().collect { batch ->
        for (event in batch) {
          if (event is TerminalContentUpdatedEvent && event.text.endsWith("hidden-until-timeout")) {
            assertThat(session.terminalSession.modeSnapshot.isSynchronizedOutput).isFalse()
            completed.complete(Unit)
          }
        }
      }
    }
    try {
      connector.feed("\u001b[?2026hhidden-until-timeout")
      completed.await()
    }
    finally {
      connector.close()
      scope.cancel()
    }
  }

  @Test
  fun `document shell events retain synchronized boundaries`() = runSession(useDocumentRenderer = true) { _, connector, events ->
    connector.feed("\u001b[?2026h\u001b]1341;prompt_started\u0007PROMPT>" +
                   "\u001b]1341;prompt_finished\u0007following-output\u001b[?2026l")
    var beforePrompt = ""
    while (true) {
      when (val event = events.receive()) {
        TerminalPromptFinishedEvent -> break
        is TerminalContentUpdatedEvent -> beforePrompt = event.text
        else -> Unit
      }
    }
    assertThat(beforePrompt).endsWith("PROMPT>").doesNotContain("following-output")
    while (true) {
      val event = events.receive()
      if (event is TerminalContentUpdatedEvent && event.text.endsWith("following-output")) break
    }
  }

  @Test
  fun `document termination includes pending history`() = runSession(useDocumentRenderer = true) { _, connector, events ->
    val text = (0 until 600).joinToString("") { "line$it\r\n" } + "END"
    connector.feed("\u001b[?2026h$text")
    connector.close()
    var lastContent: TerminalContentUpdatedEvent? = null
    while (true) {
      when (val event = events.receive()) {
        TerminalSessionTerminatedEvent -> break
        is TerminalContentUpdatedEvent -> lastContent = event
        else -> Unit
      }
    }
    assertThat(requireNotNull(lastContent).text).isEqualTo(text.replace("\r\n", "\n"))
  }

  @Test
  fun `document palette changes publish without output`() = runSession(useDocumentRenderer = true) { session, connector, events ->
    connector.feed("\u001b[38;5;17mPALETTE")
    while (true) {
      val event = events.receive()
      if (event is TerminalContentUpdatedEvent && event.text.endsWith("PALETTE")) break
    }
    val colors = session.terminalSession.palette.toIndexedColorsArray()
    colors[17] = 0xFF123456.toInt()
    session.terminalSession.setThemePalette(session.terminalSession.palette.copy(indexedColors = colors))
    while (true) {
      val event = events.receive()
      if (event is TerminalContentUpdatedEvent && event.text.endsWith("PALETTE")) {
        assertThat(event.styles.single().style.foreground!!.rgb).isEqualTo(0x123456)
        break
      }
    }
  }

  @Test
  fun `document resize retains both buffer contents`() = runSession(useDocumentRenderer = true) { session, connector, events ->
    connector.feed("PRIMARY")
    while (true) {
      val event = events.receive()
      if (event is TerminalContentUpdatedEvent && event.text.endsWith("PRIMARY")) break
    }
    session.getInputChannel().send(TerminalResizeEvent(TerminalGridSize(40, 12)))
    session.terminalSession.renderGeneration.first {
      var resized = false
      session.terminalSession.readRenderFrame { frame -> resized = frame.columns == 40 && frame.rows == 12 }
      resized
    }
    connector.feed("\u001b[?1049hALTERNATE")
    while (true) {
      val event = events.receive()
      if (event is TerminalContentUpdatedEvent && event.text.endsWith("ALTERNATE")) {
        assertThat(event.text).doesNotContain("PRIMARY")
        break
      }
    }
    connector.feed("\u001b[?1049l")
    while (true) {
      val event = events.receive()
      if (event is TerminalContentUpdatedEvent && event.text.endsWith("PRIMARY")) {
        assertThat(event.text).doesNotContain("ALTERNATE")
        break
      }
    }
  }

  @Test
  fun `document state changes publish without text changes`() = runSession(useDocumentRenderer = true) { _, connector, events ->
    connector.feed("unchanged")
    while (true) {
      val event = events.receive()
      if (event is TerminalContentUpdatedEvent && event.text == "unchanged") break
    }
    connector.feed("\u001b]2;changed-title\u0007\u001b[?25l")
    while (true) {
      val event = events.receive()
      if (event is TerminalStateChangedEvent && event.state.windowTitle == "changed-title") {
        assertThat(event.state.isCursorVisible).isFalse()
        break
      }
    }
  }

  @Test
  fun `document cancellation releases a full output queue`(): Unit = timeoutRunBlocking {
    val scope = childScope("KetraTerm document backpressure", Dispatchers.Default)
    val (base, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      projectFixture.get(), scope, emulatorType = TerminalEmulatorType.KetraTerm, useDocumentRenderer = true,
      isLowLevelSession = true,
    )
    val session = base as KetraTermTerminalSession
    val collectorBlocked = CompletableDeferred<Unit>()
    scope.launch {
      session.getOutputFlow().collect { batch ->
        if (batch.any { it is TerminalCommandStartedEvent && it.command == "gate" }) {
          collectorBlocked.complete(Unit)
          awaitCancellation()
        }
      }
    }
    try {
      connector.feed("\u001b]1341;command_started;command=67617465\u0007")
      collectorBlocked.await()
      val initialRecords = session.shellState.recordCount()
      connector.feed("\u001b]1341;prompt_started\u0007".repeat(80))
      while (session.shellState.recordCount() < initialRecords + 65) yield()
      session.coroutineScope.cancel()
      session.terminalSession.state.first { it is TerminalSessionState.Closed }
      assertThat(connector.isConnected).isFalse()
    }
    finally {
      connector.close()
      scope.cancel()
    }
  }

  private fun runSession(
    useDocumentRenderer: Boolean = false,
    action: suspend CoroutineScope.(KetraTermTerminalSession, LoopbackTtyConnector, Channel<TerminalOutputEvent>) -> Unit,
  ) = timeoutRunBlocking {
    val scope = childScope("KetraTerm adapter test", Dispatchers.Default)
    val (base, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      projectFixture.get(), scope, emulatorType = TerminalEmulatorType.KetraTerm, isLowLevelSession = true,
      useDocumentRenderer = useDocumentRenderer,
    )
    val session = base as KetraTermTerminalSession
    val events = Channel<TerminalOutputEvent>(Channel.UNLIMITED)
    scope.launch {
      session.getOutputFlow().collect { batch -> for (event in batch) events.send(event) }
    }
    try {
      action(session, connector, events)
    }
    finally {
      connector.close()
      scope.cancel()
      events.close()
    }
  }

  companion object {
    private val projectFixture = projectFixture()
  }
}
