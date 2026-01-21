package com.intellij.terminal.frontend.view.completion

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.syncEditorCaretWithModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.session.guessShellName
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.*
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * Decides on whether to invoke command completion based on typing events.
 */
internal class TerminalCommandCompletionTypingListener(
  private val terminalView: TerminalView,
  private val editor: EditorEx,
  private val outputModel: TerminalOutputModel,
  private val shellIntegrationDeferred: Deferred<TerminalShellIntegration>,
  private val startupOptionsDeferred: Deferred<TerminalStartupOptions>,
) {
  private val coroutineScope = terminalView.coroutineScope.childScope("TerminalCommandCompletionTypingListener")
  private val typingEventsChannel = Channel<TypingEvent>(capacity = Channel.CONFLATED)

  init {
    coroutineScope.launch(Dispatchers.UI) {
      typingEventsChannel.consumeAsFlow().collectLatest {
        try {
          withTimeoutOrNull(1000.milliseconds) {
            awaitTypingHappenedAndInvokeCompletion(it.beforeTypingCursorOffset, it.char)
          }
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Exception) {
          thisLogger().error("Exception during completion scheduling", e)
        }
      }
    }
  }

  fun onCharTyped(
    beforeTypingCursorOffset: TerminalOffset,
    char: Char,
  ) {
    if (!canInvokeCompletion(char)) {
      return
    }
    if (isTypingHappened(beforeTypingCursorOffset, char)) {
      // If typing already happened, then probably typeahead logic inserted the prediction.
      invokeCompletion()
    }
    else {
      // Let's wait for typing to happen in the shell and then invoke completion
      typingEventsChannel.trySend(TypingEvent(beforeTypingCursorOffset, char))
    }
  }

  private fun isTypingHappened(beforeTypingCursorOffset: TerminalOffset, char: Char): Boolean {
    val newCursorOffset = beforeTypingCursorOffset + 1
    val startOffset = beforeTypingCursorOffset.coerceIn(outputModel.startOffset, outputModel.endOffset)
    val endOffset = newCursorOffset.coerceIn(outputModel.startOffset, outputModel.endOffset)
    val typedText = outputModel.getText(startOffset, endOffset).toString()
    return typedText.singleOrNull() == char && outputModel.cursorOffset >= newCursorOffset
  }

  private fun canInvokeCompletion(char: Char): Boolean {
    val project = editor.project ?: return false
    val shellName = startupOptionsDeferred.getNow()?.guessShellName() ?: return false
    val shellIntegration = shellIntegrationDeferred.getNow() ?: return false
    return TerminalCommandCompletion.isEnabled(project)
           && TerminalCommandCompletion.isSupportedForShell(shellName)
           && TerminalOptionsProvider.instance.showCompletionPopupAutomatically
           && shellIntegration.outputStatus.value == TerminalOutputStatus.TypingCommand
           && LookupManager.getActiveLookup(editor) == null
           && canTriggerCompletionForChar(char)
  }

  private fun invokeCompletion() {
    val project = editor.project!!
    val shellIntegration = shellIntegrationDeferred.getNow()!!
    TerminalCommandCompletionService.getInstance(project).invokeCompletion(
      terminalView,
      editor,
      outputModel,
      shellIntegration,
      isAutoPopup = true
    )
  }

  private suspend fun awaitTypingHappenedAndInvokeCompletion(beforeTypingCursorOffset: TerminalOffset, char: Char) {
    awaitTypingHappened(beforeTypingCursorOffset, char)

    if (canInvokeCompletion(char)) {
      syncEditorCaretWithModel(editor, outputModel)
      invokeCompletion()
    }
  }

  private suspend fun awaitTypingHappened(beforeTypingCursorOffset: TerminalOffset, char: Char) {
    suspendCancellableCoroutine { continuation ->
      if (isTypingHappened(beforeTypingCursorOffset, char)) {
        continuation.resume(Unit)
        return@suspendCancellableCoroutine
      }

      val disposable = Disposer.newDisposable()
      continuation.invokeOnCancellation { Disposer.dispose(disposable) }
      outputModel.addListener(disposable, object : TerminalOutputModelListener {
        override fun afterContentChanged(event: TerminalContentChangeEvent) {
          check()
        }

        override fun cursorOffsetChanged(event: TerminalCursorOffsetChangeEvent) {
          check()
        }

        private fun check() {
          if (isTypingHappened(beforeTypingCursorOffset, char)) {
            Disposer.dispose(disposable)
            continuation.resume(Unit)
          }
        }
      })
    }
  }

  private fun canTriggerCompletionForChar(char: Char): Boolean {
    return Character.isLetterOrDigit(char)
  }

  private data class TypingEvent(val beforeTypingCursorOffset: TerminalOffset, val char: Char)
}
