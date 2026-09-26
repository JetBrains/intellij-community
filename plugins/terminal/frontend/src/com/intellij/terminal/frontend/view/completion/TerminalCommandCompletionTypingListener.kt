package com.intellij.terminal.frontend.view.completion

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.syncEditorCaretWithModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.session.guessShellName
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalCursorOffsetChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import java.awt.event.KeyEvent
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * Decides on whether to invoke command completion based on typing events.
 */
internal class TerminalCommandCompletionTypingListener private constructor(
  private val terminalView: TerminalView,
  private val editor: EditorEx,
  coroutineScope: CoroutineScope,
) {
  private val outputModel: TerminalOutputModel
    get() = terminalView.outputModels.regular

  private val typingEventsChannel = Channel<TypingEvent>(capacity = Channel.CONFLATED)

  init {
    coroutineScope.launch(Dispatchers.UI) {
      terminalView.keyEventsFlow.collect {
        if (it.awtEvent.id == KeyEvent.KEY_TYPED) {
          try {
            onCharTyped(it.cursorOffset, it.awtEvent.keyChar)
          }
          catch (e: CancellationException) {
            throw e
          }
          catch (e: Exception) {
            LOG.error("Exception during handling $it", e)
          }
        }
      }
    }

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
          LOG.error("Exception during completion scheduling", e)
        }
      }
    }
  }

  private fun onCharTyped(
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

  /**
   * Heuristically checks that typing of the given [char] happened.
   * Actually, [beforeTypingCursorOffset] is not really reliable -
   * the char will be typed on this offset only if model was up to date at the moment of typing.
   * But if there were 2 typing events, and we didn't receive an update from the process between them,
   * then both typings will be registered with the same [beforeTypingCursorOffset].
   * So, instead of checking that char appeared at [beforeTypingCursorOffset],
   * we check that the char before the cursor became [char].
   */
  private fun isTypingHappened(beforeTypingCursorOffset: TerminalOffset, char: Char): Boolean {
    val startOffset = beforeTypingCursorOffset.coerceIn(outputModel.startOffset, outputModel.endOffset)
    val endOffset = outputModel.cursorOffset.coerceIn(outputModel.startOffset, outputModel.endOffset)
    if (startOffset >= endOffset) {
      return false
    }
    val typedText = outputModel.getText(startOffset, endOffset).toString()
    return typedText.endsWith(char)
  }

  private fun canInvokeCompletion(char: Char): Boolean {
    val project = editor.project ?: return false
    val shellName = terminalView.startupOptionsDeferred.getNow()?.guessShellName() ?: return false
    val shellIntegration = terminalView.shellIntegrationDeferred.getNow() ?: return false
    return TerminalCommandCompletion.isEnabled(project)
           && TerminalCommandCompletion.isSupportedForShell(shellName)
           && TerminalOptionsProvider.instance.showCompletionPopupAutomatically
           && shellIntegration.outputStatus.value == TerminalOutputStatus.TypingCommand
           && LookupManager.getActiveLookup(editor) == null
           && canTriggerCompletionForChar(char)
  }

  private fun invokeCompletion() {
    syncEditorCaretWithModel(editor, outputModel)

    val project = editor.project!!
    val shellIntegration = terminalView.shellIntegrationDeferred.getNow()!!
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

  companion object {
    private val LOG = logger<TerminalCommandCompletionTypingListener>()

    fun install(
      terminalView: TerminalView,
      editor: EditorEx,
      coroutineScope: CoroutineScope,
    ) {
      TerminalCommandCompletionTypingListener(terminalView, editor, coroutineScope)
    }
  }
}
