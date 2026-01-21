package com.intellij.terminal.frontend.view.completion

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.NlsSafe
import com.intellij.terminal.frontend.view.TerminalView
import kotlinx.coroutines.Deferred
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.session.TerminalStartupOptions
import org.jetbrains.plugins.terminal.session.guessShellName
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration

internal class TerminalCommandCompletionTypingListener(
  private val terminalView: TerminalView,
  private val editor: EditorEx,
  private val outputModel: TerminalOutputModel,
  private val shellIntegrationDeferred: Deferred<TerminalShellIntegration>,
  private val startupOptionsDeferred: Deferred<TerminalStartupOptions>,
) {
  fun onCharTyped(
    beforeCursorOffset: TerminalOffset,
    char: Char,
  ) {
    val project = editor.project ?: return
    val shellName = startupOptionsDeferred.getNow()?.guessShellName() ?: return
    val shellIntegration = shellIntegrationDeferred.getNow() ?: return
    if (TerminalCommandCompletion.isEnabled(project)
        && TerminalCommandCompletion.isSupportedForShell(shellName)
        && TerminalOptionsProvider.instance.showCompletionPopupAutomatically
        && shellIntegration.outputStatus.value == TerminalOutputStatus.TypingCommand
        && canTriggerCompletion(char)
        && LookupManager.getActiveLookup(editor) == null
        && outputModel.getTextAfterCursor().isBlank()
    ) {
      TerminalCommandCompletionService.getInstance(project).invokeCompletion(
        terminalView,
        editor,
        outputModel,
        shellIntegration,
        isAutoPopup = true
      )
    }
  }

  private fun canTriggerCompletion(char: Char): Boolean {
    return Character.isLetterOrDigit(char)
  }

  private fun TerminalOutputModel.getTextAfterCursor(): @NlsSafe CharSequence = getText(cursorOffset, endOffset)
}
