package com.intellij.terminal.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelListener
import java.awt.event.KeyEvent

/**
 * This is a heuristic-based tracker that allows to guess when command is finished in the terminal
 * and call the provided [onCommandFinish] function.
 *
 * This tracker analyzes key events passed to [handleKeyPressed] method to guess if command execution is started.
 * Then it analyzes [outputModel] updates by checking if line with cursor contains the same prompt,
 * that was before command execution started.
 *
 * **Limitation**: the tracker will fail to detect command finish if the prompt text is changed after command finish.
 * For example, if a user changed the directory or Git branch.
 * Also, it doesn't try to track lines longer than [MAX_LINE_LENGTH] for performance reasons.
 *
 * Stops operating once the provided coroutine scope is canceled.
 */
internal class TerminalHeuristicsBasedCommandFinishTracker(
  private val outputModel: TerminalOutputModel,
  coroutineScope: CoroutineScope,
  private val onCommandFinish: () -> Unit,
) {
  // Guarded by EDT
  private var curLineInfo: LineInfo? = null

  private val commandStartRequests = MutableSharedFlow<LineInfo>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val modelUpdatesFlow = createModelUpdatesFlow(coroutineScope.childScope("TerminalOutputModel updates"))

  init {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      commandStartRequests.collectLatest { lineInfo ->
        trackCommandFinish(lineInfo)
      }
    }
  }

  @RequiresEdt
  fun handleKeyPressed(e: KeyEvent) {
    try {
      doHandleKeyPressed(e)
    }
    catch (ex: Exception) {
      LOG.error(ex)
    }
  }

  private fun doHandleKeyPressed(e: KeyEvent) {
    updateCurLineInfo()

    val lineInfo = curLineInfo
    if (e.keyCode == KeyEvent.VK_ENTER && lineInfo != null) {
      val cursorOffset = outputModel.cursorOffsetState.value
      val textBeforeCursor = getTextBeforeCursor(cursorOffset) ?: return
      if (textBeforeCursor.startsWith(lineInfo.promptText) && textBeforeCursor.length > lineInfo.promptText.length) {
        // There is some command to execute
        check(commandStartRequests.tryEmit(lineInfo))
        LOG.debug { "Command start detected" }
      }
    }
  }

  private fun updateCurLineInfo() {
    val lineInfo = curLineInfo
    val cursorOffset = outputModel.cursorOffsetState.value
    val absoluteLineIndex = outputModel.getAbsoluteLineIndex(cursorOffset)
    val textBeforeCursor = getTextBeforeCursor(cursorOffset)

    when {
      textBeforeCursor == null -> {
        curLineInfo = null
      }
      lineInfo?.absoluteIndex != absoluteLineIndex -> {
        curLineInfo = LineInfo(absoluteLineIndex, textBeforeCursor)
      }
      !textBeforeCursor.startsWith(lineInfo.promptText) -> {
        curLineInfo = lineInfo.copy(promptText = textBeforeCursor)
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
      val cursorOffset = outputModel.cursorOffsetState.value
      val absoluteLineIndex = outputModel.getAbsoluteLineIndex(cursorOffset)

      absoluteLineIndex != lineInfo.absoluteIndex && getTextBeforeCursor(cursorOffset) == lineInfo.promptText
    }

    onCommandFinish()

    LOG.debug { "Command finish detected" }
  }

  private fun getTextBeforeCursor(cursorOffset: Int): String? {
    val document = outputModel.document
    val lineNumber = document.getLineNumber(cursorOffset)
    val lineStartOffset = document.getLineStartOffset(lineNumber)

    val length = cursorOffset - lineStartOffset
    return if (length <= MAX_LINE_LENGTH) {
      document.immutableCharSequence.substring(lineStartOffset, lineStartOffset + length)
    }
    else null // Line is too long, let's do not try to track it.
  }

  private fun createModelUpdatesFlow(coroutineScope: CoroutineScope): Flow<Unit> {
    val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    outputModel.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {
      override fun afterContentChanged(model: TerminalOutputModel, startOffset: Int, isTypeAhead: Boolean) {
        check(flow.tryEmit(Unit))
      }
    })

    return flow
  }

  private data class LineInfo(
    val absoluteIndex: Long,
    val promptText: String,
  )

  companion object {
    private const val PROMPT_CHECKING_DELAY = 500L
    private const val MAX_LINE_LENGTH = 1000

    private val LOG = logger<TerminalHeuristicsBasedCommandFinishTracker>()
  }
}