package com.intellij.terminal.frontend.view.inlineCompletion

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent.Backspace
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent.DocumentChange
import com.intellij.codeInsight.inline.completion.TypingEvent.OneSymbol
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.terminal.frontend.view.TerminalKeyEvent
import com.intellij.terminal.frontend.view.impl.TerminalTypingEvent
import com.intellij.terminal.frontend.view.impl.TerminalTypingListener
import com.intellij.terminal.frontend.view.impl.TerminalTypingTracker
import com.intellij.terminal.frontend.view.impl.syncEditorCaretWithModel
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.asDisposable
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import java.awt.event.KeyEvent

/**
 * Connects terminal input to inline completion.
 *
 * Relies on [TerminalTypingTracker] to determine whether typed input was confirmed by the real shell output
 * or must be treated as a mismatch and turns that into inline-completion-specific actions on [editor].
 */
@ApiStatus.Internal
class TerminalInlineCompletionController(
  private val editor: EditorEx,
  private val model: TerminalOutputModel,
  private val typingTracker: TerminalTypingTracker,
  private val coroutineScope: CoroutineScope,
) {

  @OptIn(AwaitCancellationAndInvoke::class)
  fun install() {
    InlineCompletion.install(editor, coroutineScope)
    typingTracker.addTypingListener(coroutineScope.asDisposable(), object : TerminalTypingListener {
      override fun onTypingEvent(event: TerminalTypingEvent) {
        when (event) {
          is TerminalTypingEvent.Confirmed -> handleConfirmed(event.keyEvent)
          TerminalTypingEvent.Mismatch -> cancelActiveInlineCompletion()
        }
      }
    })
    coroutineScope.awaitCancellationAndInvoke(Dispatchers.UI) {
      InlineCompletion.remove(editor)
    }
  }

  private fun handleConfirmed(keyEvent: TerminalKeyEvent) {
    val documentOffset = (keyEvent.cursorOffset - model.startOffset).toInt()
    when (keyEvent.awtEvent.id) {
      KeyEvent.KEY_TYPED -> {
        invokeTyping(keyEvent.awtEvent.keyChar, documentOffset)
      }
      KeyEvent.KEY_PRESSED -> {
        if (keyEvent.awtEvent.keyCode == KeyEvent.VK_BACK_SPACE) {
          invokeBackspace()
        }
      }
    }
  }

  private fun cancelActiveInlineCompletion() {
    // Avoid scheduling an EDT action that acquires WIL when there is no session to cancel.
    if (InlineCompletionSession.getOrNull(editor) == null) return

    launchInlineCompletionAction {
      InlineCompletion.getHandlerOrNull(editor)?.cancel(FinishType.KEY_PRESSED)
    }
  }

  private fun invokeTyping(char: Char, documentOffset: Int) {
    LOG.trace { "Inline completion dispatched typing: char='$char', offset=$documentOffset" }
    launchInlineCompletionAction {
      syncEditorCaretWithModel(editor, model)
      InlineCompletion.getHandlerOrNull(editor)?.invokeEvent(
        DocumentChange(OneSymbol(char, documentOffset), editor)
      )
    }
  }

  private fun invokeBackspace() {
    LOG.trace("Inline completion dispatched backspace")
    launchInlineCompletionAction {
      syncEditorCaretWithModel(editor, model)
      InlineCompletion.getHandlerOrNull(editor)?.invokeEvent(Backspace(editor))
    }
  }

  private fun launchInlineCompletionAction(action: () -> Unit) {
    // Inline Completion may require the platform read lock, so execute actions later on EDT.
    coroutineScope.launch(Dispatchers.EDT) {
      action()
    }
  }

  companion object {
    private val LOG = logger<TerminalInlineCompletionController>()
  }
}
