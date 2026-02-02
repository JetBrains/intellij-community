package com.intellij.terminal.tests.reworked.util

import com.google.common.base.Ascii
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.frontend.view.completion.PowerShellCompletionContributor
import com.intellij.util.containers.DisposableWrapperList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.terminal.block.completion.powershell.PowerShellCompletionResultWithContext
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.session.impl.TerminalBeepEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCloseEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCompletionFinishedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalCursorPositionChangedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInitialStateEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalWriteBytesEvent
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalBlocksModelStateDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalCommandBlockDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalHyperlinksModelStateDto
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalOutputModelStateDto
import org.jetbrains.plugins.terminal.session.impl.dto.toDto
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlockIdImpl
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Simple implementation of [TerminalSession] that supports basic [TerminalWriteBytesEvent]'s:
 * - backspace
 * - new line
 * - left/right arrow
 *
 * Other bytes are interpreted as plain text.
 *
 * These events update the imaginary screen state and echo it into the session's output flow.
 *
 * Can be useful to test typing in the [com.intellij.terminal.frontend.view.TerminalView]
 * without starting a real shell process.
 *
 * @param startupOptions options to be returned as a part of [TerminalInitialStateEvent].
 */
internal class EchoingTerminalSession(
  private val startupOptions: TerminalStartupOptions,
  coroutineScope: CoroutineScope,
) : TerminalSession {
  private val inputChannel = Channel<TerminalInputEvent>(Channel.UNLIMITED)

  /**
   * Can be used only for sending one-shot events, like [TerminalBeepEvent] or [TerminalCompletionFinishedEvent].
   * Shouldn't be used for incremental updates.
   */
  private val outputChannel = Channel<TerminalOutputEvent>(Channel.UNLIMITED)

  private val screenState = ScreenState()
  private val screenLock = ReentrantLock()

  init {
    coroutineScope.launch {
      for (event in inputChannel) {
        try {
          handleInputEvent(event)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Exception) {
          LOG.error("Exception during handling $event", e)
        }
      }
    }
  }

  private fun handleInputEvent(event: TerminalInputEvent) {
    when (event) {
      is TerminalCloseEvent -> {
        isClosed = true
      }
      is TerminalWriteBytesEvent -> {
        if (String(event.bytes) == PowerShellCompletionContributor.CALL_COMPLETION_SEQUENCE) {
          handleCompletionRequest()
        }
        else {
          screenLock.withLock {
            decodeBytesAndUpdateScreen(event.bytes)
          }
        }
      }
      else -> {
        // other events are not supported
      }
    }
  }

  private fun handleCompletionRequest() {
    // Just send the empty response
    val emptyResult = PowerShellCompletionResultWithContext(
      commandText = "",
      cursorIndex = 0,
      replacementIndex = 0,
      replacementLength = 0,
      matches = emptyList()
    )
    val emptyResultJson = Json.encodeToString(emptyResult)
    val event = TerminalCompletionFinishedEvent(emptyResultJson)
    outputChannel.trySend(event)
  }

  private fun decodeBytesAndUpdateScreen(bytes: ByteArray) {
    val bytesList = bytes.toList()
    when {
      LEFT_ARROW_VARIANTS.contains(bytesList) -> {
        screenState.moveCursorLeft()
      }
      RIGHT_ARROW_VARIANTS.contains(bytesList) -> {
        screenState.moveCursorRight()
      }
      else -> {
        val text = String(bytes).replace('\r', '\n')
        for (char in text) {
          if (BACKSPACE_VARIANTS.contains(char.code.toByte())) {
            screenState.backspace()
          }
          else {
            screenState.type(char.toString())
          }
        }
      }
    }
  }

  override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> {
    return inputChannel
  }

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> {
    val screenEventsFlow = channelFlow {
      val listenerDisposable = Disposer.newDisposable()

      screenLock.withLock {
        val screen = screenState.getSnapshot()
        val initialState = createInitialStateEvent(screen)
        trySend(listOf(initialState))

        screenState.addListener(listenerDisposable) {
          val screen = screenState.getSnapshot()
          val contentChangeEvent = TerminalContentUpdatedEvent(screen.text, emptyList(), 0)
          val cursorChangeEvent = TerminalCursorPositionChangedEvent(screen.cursorLine.toLong(), screen.cursorColumn)
          trySend(listOf(contentChangeEvent, cursorChangeEvent))
        }
      }

      awaitClose {
        Disposer.dispose(listenerDisposable)
      }
    }.buffer(Channel.UNLIMITED)

    return merge(
      screenEventsFlow,
      outputChannel.receiveAsFlow().map { listOf(it) }
    )
  }

  private fun createInitialStateEvent(screen: ScreenStateSnapshot): TerminalInitialStateEvent {
    val outputModelState = TerminalOutputModelStateDto(
      text = screen.text,
      trimmedLinesCount = 0,
      trimmedCharsCount = 0,
      firstLineTrimmedCharsCount = 0,
      cursorOffset = screen.cursorOffset,
      highlightings = emptyList()
    )
    val commandBlock = TerminalCommandBlockDto(
      id = TerminalBlockIdImpl(0),
      startOffset = 0,
      endOffset = 0,
      commandStartOffset = 0,
      outputStartOffset = null,
      workingDirectory = null,
      executedCommand = null,
      exitCode = null,
    )
    val sessionState = TerminalSessionModelImpl.getInitialState().copy(
      isShellIntegrationEnabled = true,
      currentDirectory = startupOptions.workingDirectory,
    )
    return TerminalInitialStateEvent(
      startupOptions = startupOptions.toDto(),
      sessionState = sessionState.toDto(),
      outputModelState = outputModelState,
      alternateBufferState = TerminalOutputModelStateDto("", 0, 0, 0, 0, emptyList()),
      blocksModelState = TerminalBlocksModelStateDto(listOf(commandBlock), 1),
      outputHyperlinksState = TerminalHyperlinksModelStateDto(emptyList()),
      alternateBufferHyperlinksState = TerminalHyperlinksModelStateDto(emptyList()),
    )
  }

  override suspend fun hasRunningCommands(): Boolean {
    return false
  }

  @Volatile
  override var isClosed: Boolean = false

  private class ScreenState {
    private val document: Document = DocumentImpl("", true)
    private var cursorOffset = 0
    private val listeners = DisposableWrapperList<() -> Unit>()

    fun type(text: String) {
      document.insertString(cursorOffset, text)
      cursorOffset += text.length
      fireListeners()
    }

    fun backspace() {
      if (cursorOffset > 0) {
        document.deleteString(cursorOffset - 1, cursorOffset)
        cursorOffset--
        fireListeners()
      }
    }

    fun moveCursorLeft() {
      if (cursorOffset > 0) {
        cursorOffset--
        fireListeners()
      }
    }

    fun moveCursorRight() {
      if (cursorOffset < document.textLength) {
        cursorOffset++
        fireListeners()
      }
    }

    fun getSnapshot(): ScreenStateSnapshot {
      val lineNumber = document.getLineNumber(cursorOffset)
      return ScreenStateSnapshot(
        text = document.text,
        cursorOffset = cursorOffset,
        cursorLine = lineNumber,
        cursorColumn = cursorOffset - document.getLineStartOffset(lineNumber)
      )
    }

    fun addListener(parentDisposable: Disposable, listener: () -> Unit) {
      listeners.add(listener, parentDisposable)
    }

    private fun fireListeners() {
      for (listener in listeners) {
        listener()
      }
    }
  }

  private data class ScreenStateSnapshot(
    val text: String,
    val cursorOffset: Int,
    val cursorLine: Int,
    val cursorColumn: Int,
  )

  companion object {
    private val LOG = logger<EchoingTerminalSession>()

    private val BACKSPACE_VARIANTS = listOf(Ascii.BS, Ascii.DEL)
    private val LEFT_ARROW_VARIANTS = listOf(listOf(Ascii.ESC, 79, 68), listOf(Ascii.ESC, 91, 68))
    private val RIGHT_ARROW_VARIANTS = listOf(listOf(Ascii.ESC, 79, 67), listOf(Ascii.ESC, 91, 67))
  }
}