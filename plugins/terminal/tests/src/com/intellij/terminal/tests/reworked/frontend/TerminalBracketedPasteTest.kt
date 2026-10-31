// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.impl.TerminalInput
import com.intellij.terminal.frontend.view.impl.TerminalKeyEncodingManager
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalWriteBytesEvent
import org.jetbrains.plugins.terminal.session.impl.dto.KeyEventProcessingResultDto
import org.jetbrains.plugins.terminal.util.wrapInBracketedPaste
import org.jetbrains.plugins.terminal.view.impl.TerminalSendTextOptions
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.seconds

private const val START = "\u001b[200~"
private const val END = "\u001b[201~"

/**
 * Tests that a text cannot end the bracketed paste early with its own paste markers.
 */
internal class TerminalBracketedPasteTest {
  @Test
  fun `a text without markers is wrapped as is`() {
    assertThat(wrapInBracketedPaste("echo hi\r\u001b[A")).isEqualTo("${START}echo hi\r\u001b[A$END")
  }

  @Test
  fun `the start and end markers inside the text are removed before the wrap`() {
    assertThat(wrapInBracketedPaste("a${END}rm -rf ~\r${START}b")).isEqualTo("${START}arm -rf ~\rb$END")
  }

  @Test
  fun `a marker that appears after the removal of an inner marker is removed too`() {
    assertThat(wrapInBracketedPaste("x\u001b[20${END}1~y")).isEqualTo("${START}xy$END")
    assertThat(wrapInBracketedPaste("\u001b[\u001b[2${START}01~201~")).isEqualTo("$START$END")
  }

  @Test
  fun `a text of markers only becomes an empty paste`() {
    assertThat(wrapInBracketedPaste("$END$END$START")).isEqualTo("$START$END")
  }

  @Test
  fun `sendText in bracketed paste mode removes the markers of the text`(): Unit = timeoutRunBlocking {
    val scope = childScope("TerminalBracketedPasteTest")
    try {
      val session = RecordingTerminalSession(scope)
      val sessionModel = TerminalSessionModelImpl()
      sessionModel.updateTerminalState(sessionModel.terminalState.value.copy(isBracketedPasteMode = true))
      val input = TerminalInput(
        terminalSessionDeferred = CompletableDeferred(session),
        sessionModel = sessionModel,
        startupFusInfo = null,
        coroutineScope = scope,
        encodingManager = TerminalKeyEncodingManager(sessionModel),
      )

      val options = TerminalSendTextOptions(text = "safe${END}touch pwned\n", shouldExecute = false, useBracketedPasteMode = true)
      val sent = input.sendText(options)

      assertThat(sent).isTrue()
      val bytes = withTimeout(5.seconds) { (session.inputEvents.receive() as TerminalWriteBytesEvent).bytes }
      assertThat(String(bytes, StandardCharsets.UTF_8)).isEqualTo("${START}safetouch pwned\r$END")
    }
    finally {
      scope.cancel()
    }
  }

  private class RecordingTerminalSession(override val coroutineScope: CoroutineScope) : TerminalSession {
    val inputEvents = Channel<TerminalInputEvent>(Channel.UNLIMITED)

    override fun processMouseEvent(e: MouseEvent, x: Int, y: Int): ByteArray? = error("Unexpected processMouseEvent call")

    override fun processKeyEvent(e: KeyEvent): KeyEventProcessingResultDto = error("Unexpected processKeyEvent call")

    override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> = inputEvents

    override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> = emptyFlow()

    override suspend fun hasRunningCommands(): Boolean = false

    override val isClosed: Boolean = false
    override val eelDescriptor: EelDescriptor = LocalEelDescriptor
    override val processId: Long = 42L
  }
}
