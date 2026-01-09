package com.intellij.terminal.frontend.view.completion

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.terminal.block.completion.powershell.PowerShellCompletionResult
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellBasedCompletionListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import org.jetbrains.plugins.terminal.view.shellIntegration.getTypedCommandText
import kotlin.coroutines.resume

internal suspend fun getPowerShellCompletionSuggestions(context: TerminalCommandCompletionContext): TerminalCompletionResult? {
  context.terminalView.sendText(CALL_COMPLETION_SEQUENCE)

  val result: String = awaitCompletionResult(context.shellIntegration)
  LOG.trace { "PowerShell completion result: $result" }

  val json = Json { ignoreUnknownKeys = true }
  val completionResult: PowerShellCompletionResult = try {
    json.decodeFromString(result)
  }
  catch (ex: Exception) {
    LOG.error("Failed to parse PowerShell completion result: $result", ex)
    return null
  }

  if (completionResult.matches.isEmpty()) {
    return null
  }

  val activeBlock = context.shellIntegration.blocksModel.activeBlock as TerminalCommandBlock
  val commandText = activeBlock.getTypedCommandText(context.outputModel) ?: return null
  val absCursorOffset = context.outputModel.cursorOffset
  val localCursorOffset = (absCursorOffset - activeBlock.commandStartOffset!!).toInt()

  val replacementIndex = completionResult.replacementIndex
  val replacementLength = completionResult.replacementLength
  if (replacementIndex < 0 || replacementLength < 0 || replacementIndex + replacementLength > commandText.length) {
    LOG.error("""Incorrect completion replacement indexes.
        |Command: '$commandText'
        |CursorOffset: $localCursorOffset
        |Completion Result: $completionResult""".trimMargin())
    return null
  }

  val prefix = commandText.substring(replacementIndex, localCursorOffset)
  val suggestions = completionResult.matches.map { item ->
    ShellCompletionSuggestion(item.value) {
      displayName(item.presentableText ?: item.value)
    }
  }

  return TerminalCompletionResult(suggestions, prefix)
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

private val LOG = fileLogger()