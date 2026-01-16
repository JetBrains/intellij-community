package com.intellij.terminal.frontend.view.completion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.frontend.view.TerminalView
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.session.guessShellName
import org.jetbrains.plugins.terminal.util.getNow
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration

internal interface TerminalCommandCompletionContributor {
  suspend fun getCompletionSuggestions(context: TerminalCommandCompletionContext): TerminalCommandCompletionResult?
}

internal data class TerminalCommandCompletionContext(
  val project: Project,
  val terminalView: TerminalView,
  val editor: Editor,
  val outputModel: TerminalOutputModel,
  val shellIntegration: TerminalShellIntegration,
  val commandStartOffset: TerminalOffset,
  val initialCursorOffset: TerminalOffset,
  /** Full command text at the moment of completion request. May include trailing new lines and spaces. */
  val commandText: String,
  val isAutoPopup: Boolean,
) {
  val shellName: ShellName
    get() = terminalView.startupOptionsDeferred.getNow()?.guessShellName() ?: ShellName.of("unknown")
}

internal data class TerminalCommandCompletionResult(
  val suggestions: List<ShellCompletionSuggestion>,
  val prefix: String,
  /** The length of the text to be replaced before the prefix when a completion item is inserted. */
  val beforePrefixReplacementLength: Int = 0,
  /** The length of the text to be replaced after the prefix when a completion item is inserted. */
  val afterPrefixReplacementLength: Int = 0,
)