package com.intellij.terminal.frontend.view.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.frontend.view.TerminalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.awt.event.KeyEvent

/**
 * Detects command completion when the cursor returns to the previous prompt on a different line.
 * The bounded cursor reader supports both document and native views.
 * A changed prompt can prevent detection. The owning scope stops observation.
 */
internal class TerminalHeuristicsBasedCommandFinishTracker private constructor(
  terminalView: TerminalView,
  coroutineScope: CoroutineScope,
  private val onCommandFinish: () -> Unit,
) {
  private val observation = TerminalCursorObservation(terminalView, coroutineScope)

  // Guarded by EDT
  private var curLineInfo: LineInfo? = null

  private val commandStartRequests = MutableSharedFlow<LineInfo>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val modelUpdatesFlow = observation.changes

  init {
    coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      commandStartRequests.collectLatest { lineInfo ->
        trackCommandFinish(lineInfo)
      }
    }

    coroutineScope.launch(Dispatchers.UI) {
      observation.keys.collect {
        handleKeyPressed(it)
      }
    }
  }

  private fun handleKeyPressed(e: KeyEvent) {
    try {
      doHandleKeyPressed(e)
    }
    catch (ex: Exception) {
      rethrowControlFlowException(ex)
      LOG.error(ex)
    }
  }

  private fun doHandleKeyPressed(e: KeyEvent) {
    updateCurLineInfo()

    val lineInfo = curLineInfo
    if (e.keyCode == KeyEvent.VK_ENTER && lineInfo != null) {
      val textBeforeCursor = observation.readLine()?.prefix ?: return
      if (textBeforeCursor.startsWith(lineInfo.promptText) && textBeforeCursor.length > lineInfo.promptText.length) {
        // There is some command to execute
        check(commandStartRequests.tryEmit(lineInfo))
        LOG.debug { "Command start detected" }
      }
    }
  }

  private fun updateCurLineInfo() {
    val lineInfo = curLineInfo
    val snapshot = observation.readLine()
    val cursorLine = snapshot?.id
    val textBeforeCursor = snapshot?.prefix

    when {
      textBeforeCursor == null -> {
        curLineInfo = null
      }
      lineInfo == null || lineInfo.line != cursorLine -> {
        curLineInfo = LineInfo(requireNotNull(cursorLine), textBeforeCursor)
      }
      !textBeforeCursor.startsWith(lineInfo.promptText) -> {
        curLineInfo = lineInfo.copy(promptText = textBeforeCursor.toString())
      }
    }

    if (curLineInfo != lineInfo) {
      LOG.debug { "Current line info updated: $curLineInfo" }
    }
  }

  @OptIn(FlowPreview::class)
  private suspend fun trackCommandFinish(lineInfo: LineInfo) {
    // Heuristic: suspend until we detect the current line has the same prompt as in the provided LineInfo
    // Then we can consider that command is finished
    modelUpdatesFlow.debounce(PROMPT_CHECKING_DELAY).first {
      val line = observation.readLine()
      line != null && line.id != lineInfo.line && line.prefix == lineInfo.promptText
    }

    onCommandFinish()

    LOG.debug { "Command finish detected" }
  }

  private data class LineInfo(
    val line: Long,
    val promptText: String,
  )

  companion object {
    private const val PROMPT_CHECKING_DELAY = 500L

    private val LOG = logger<TerminalHeuristicsBasedCommandFinishTracker>()

    fun install(
      terminalView: TerminalView,
      coroutineScope: CoroutineScope,
      onCommandFinish: () -> Unit,
    ) {
      TerminalHeuristicsBasedCommandFinishTracker(terminalView, coroutineScope, onCommandFinish)
    }
  }
}
