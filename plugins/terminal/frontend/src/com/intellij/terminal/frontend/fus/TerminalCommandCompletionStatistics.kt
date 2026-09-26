package com.intellij.terminal.frontend.fus

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.TerminalKeyEventsListener
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils
import org.jetbrains.plugins.terminal.fus.ReworkedTerminalUsageCollector
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlockId
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandExecutionListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandStartedEvent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import org.jetbrains.plugins.terminal.view.shellIntegration.getTypedCommandText
import java.awt.event.KeyEvent
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
class TerminalCommandCompletionStatistics private constructor(
  private val project: Project,
  private val shellIntegration: TerminalShellIntegration,
) {
  private val metrics = TerminalCommandCompletionMetrics()
  private var commandStartTime: Long? = null
  private var trackedCommandBlockId: TerminalBlockId? = null
  private var previousCommandLength: Int = 0
  private var previousCursorOffset: Long = 0
  private var pendingSlice: PendingSlice? = null

  companion object {
    val KEY: Key<TerminalCommandCompletionStatistics> = Key.create("terminal.command.completion.statistics")
    fun install(
      project: Project,
      shellIntegration: TerminalShellIntegration,
      outputModel: TerminalOutputModel,
      isCursorVisible: () -> Boolean,
      registerKeyEventsListener: (Disposable, TerminalKeyEventsListener) -> Unit,
      coroutineScope: CoroutineScope,
    ): TerminalCommandCompletionStatistics {
      val statistics = TerminalCommandCompletionStatistics(project, shellIntegration)
      statistics.installListeners(outputModel, isCursorVisible, registerKeyEventsListener, coroutineScope)
      return statistics
    }
  }

  private fun installListeners(
    outputModel: TerminalOutputModel,
    isCursorVisible: () -> Boolean,
    registerKeyEventsListener: (Disposable, TerminalKeyEventsListener) -> Unit,
    coroutineScope: CoroutineScope,
  ) {
    var pendingModelUpdate: Job? = null
    TerminalUiUtils.listenOutputModelChanges(outputModel, coroutineScope) {
      pendingModelUpdate?.cancel()
      if (shellIntegration.outputStatus.value != TerminalOutputStatus.TypingCommand) {
        pendingModelUpdate = null
        return@listenOutputModelChanges
      }

      pendingModelUpdate = coroutineScope.launch {
        // PowerShell can report a command redraw and its cursor update in separate output events.
        // Wait for the last update to collect a consistent model state.
        delay(100.milliseconds)
        if (!isCursorVisible()) return@launch

        withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
          recordCommandTextChanged(outputModel)
        }
      }
    }
    val parentDisposable = coroutineScope.asDisposable()
    shellIntegration.addCommandExecutionListener(parentDisposable, object : TerminalCommandExecutionListener {
      override fun commandStarted(event: TerminalCommandStartedEvent) {
        val timeMillis = System.currentTimeMillis()
        val completionMetrics = metrics.takeAndReset(timeMillis)
        val command = event.commandBlock.executedCommand ?: return
        trackedCommandBlockId = null
        previousCommandLength = 0
        previousCursorOffset = 0
        commandStartTime = timeMillis
        ReworkedTerminalUsageCollector.logCommandStarted(
          project,
          command,
          totalCommandInsertedLength = completionMetrics.totalCommandInsertedLength,
          popupCompletionLength = completionMetrics.popupCompletionLength,
          inlineCompletionLength = completionMetrics.inlineCompletionLength,
          typingsCount = completionMetrics.typingsCount,
          backspacesCount = completionMetrics.backspacesCount,
          commandTypingTimeMillis = completionMetrics.commandTypingTimeMillis,
        )
      }

      override fun commandFinished(event: TerminalCommandFinishedEvent) {
        val command = event.commandBlock.executedCommand ?: return
        val exitCode = event.commandBlock.exitCode ?: return
        val startTime = commandStartTime ?: return
        commandStartTime = null
        ReworkedTerminalUsageCollector.logCommandFinished(project, command, exitCode, (System.currentTimeMillis() - startTime).milliseconds)
      }
    })
    registerKeyEventsListener(parentDisposable, object : TerminalKeyEventsListener {
      override fun afterKeyEvent(event: TerminalKeyEvent) {
        recordKeyEvent(event)
      }
    })
  }

  fun recordPopupInserted(length: Int) {
    metrics.recordPopupInserted(length)
  }

  fun recordInlineInserted(length: Int) {
    metrics.recordInlineInserted(length)
  }

  /**
   * Records command text changes after current terminal output updates.
   *
   * PowerShell and zsh completion plugins can draw inline suggestions in the output model before the user accepts them.
   * PendingSlice tracks this unconfirmed range. It prevents suggestion text from increasing the command length until a later
   * cursor or model update confirms that the text was accepted.
   */
  @VisibleForTesting
  fun recordCommandTextChanged(outputModel: TerminalOutputModel) {
    if (shellIntegration.outputStatus.value != TerminalOutputStatus.TypingCommand) return
    val commandBlock = shellIntegration.blocksModel.activeBlock as? TerminalCommandBlock ?: return
    val commandStartOffset = commandBlock.commandStartOffset ?: return
    if (trackedCommandBlockId != commandBlock.id) {
      metrics.reset()
      trackedCommandBlockId = commandBlock.id
      previousCommandLength = 0
      previousCursorOffset = commandStartOffset.toAbsolute()
      pendingSlice = null
    }

    val cursorOffset = outputModel.cursorOffset.toAbsolute()
    val modelCommandLength = commandBlock.getTypedCommandText(outputModel)?.length ?: return
    val cursorCommandLength = (cursorOffset - commandStartOffset.toAbsolute()).coerceAtLeast(0).toInt()
    val commandLength = maxOf(modelCommandLength, cursorCommandLength)
    if (commandLength == 0) {
      metrics.reset()
      previousCommandLength = 0
      previousCursorOffset = cursorOffset
      pendingSlice = null
      return
    }

    val modelDelta = commandLength - previousCommandLength
    val cursorDelta = cursorOffset - previousCursorOffset
    val modelEndOffset = commandStartOffset.toAbsolute() + modelCommandLength
    val currentPendingSlice = pendingSlice
    val timeMillis = System.currentTimeMillis()

    if (currentPendingSlice != null && modelDelta < 0) {
      pendingSlice = null
      metrics.recordCommandTextInserted(cursorDelta.coerceAtLeast(0).toInt(), timeMillis)
    }
    else if (currentPendingSlice != null && modelDelta > 0) {
      val pendingLength = currentPendingSlice.endOffset - currentPendingSlice.startOffset
      if (modelDelta + pendingLength > cursorDelta) {
        pendingSlice = PendingSlice(cursorOffset, modelEndOffset)
        metrics.recordCommandTextInserted(cursorDelta.coerceAtLeast(0).toInt(), timeMillis)
      }
      else {
        pendingSlice = null
        metrics.recordCommandTextInserted(cursorDelta.coerceAtLeast(0).toInt(), timeMillis)
      }
    }
    else if (currentPendingSlice != null && modelDelta == 0 && cursorDelta > 0) {
      if (cursorOffset > currentPendingSlice.startOffset) {
        val newStartOffset = cursorOffset.coerceAtMost(currentPendingSlice.endOffset)
        pendingSlice = if (newStartOffset < currentPendingSlice.endOffset) {
          PendingSlice(newStartOffset, currentPendingSlice.endOffset)
        }
        else {
          null
        }
        metrics.recordCommandTextInserted(cursorDelta.coerceAtLeast(0).toInt(), timeMillis)
      }
    }
    else {
      if (modelDelta > 0 && modelDelta > cursorDelta) {
        pendingSlice = PendingSlice(cursorOffset, modelEndOffset)
      }
      val delta = minOf(modelDelta.toLong(), cursorDelta).coerceAtLeast(0).toInt()
      metrics.recordCommandTextInserted(delta, timeMillis)
    }

    previousCommandLength = commandLength
    previousCursorOffset = cursorOffset
  }

  private fun recordKeyEvent(event: TerminalKeyEvent) {
    if (shellIntegration.outputStatus.value != TerminalOutputStatus.TypingCommand) return

    val awtEvent = event.awtEvent
    when {
      awtEvent.id == KeyEvent.KEY_TYPED -> {
        metrics.recordTyping()
      }
      awtEvent.id == KeyEvent.KEY_PRESSED && awtEvent.keyCode == KeyEvent.VK_BACK_SPACE -> {
        metrics.recordBackspace()
      }
    }
  }

  private data class PendingSlice(
    val startOffset: Long,
    val endOffset: Long,
  )
}
