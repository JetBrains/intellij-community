package com.intellij.terminal.frontend.view.completion

import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellBasedCompletionListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import kotlin.coroutines.resume

internal suspend fun getPowerShellCompletionSuggestions(context: TerminalCommandCompletionContext): TerminalCompletionResult {
  context.terminalView.sendText(CALL_COMPLETION_SEQUENCE)

  val result: String = awaitCompletionResult(context.shellIntegration)
  // TODO: parse
  println(result)

  return TerminalCompletionResult(emptyList(), "")
}

private suspend fun awaitCompletionResult(shellIntegration: TerminalShellIntegration): String {
  return suspendCancellableCoroutine { continuation ->
    val disposable = Disposer.newDisposable()
    continuation.invokeOnCancellation { Disposer.dispose(disposable) }
    shellIntegration.addShellBasedCompletionListener(disposable, object : TerminalShellBasedCompletionListener {
      override fun completionFinished(result: String) {
        Disposer.dispose(disposable)
        continuation.resume(result)
      }
    })
  }
}

/**
 * Sequence that is sent to the shell when pressing `F12, e`.
 * Our PowerShell integration script binds completion to this sequence.
 */
private const val CALL_COMPLETION_SEQUENCE = "\u001b[24~e"