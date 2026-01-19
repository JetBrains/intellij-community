package com.intellij.terminal.frontend.view.completion

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellSuggestionType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import org.jetbrains.plugins.terminal.TerminalIcons
import org.jetbrains.plugins.terminal.block.completion.powershell.PowerShellCompletionItem
import org.jetbrains.plugins.terminal.block.completion.powershell.PowerShellCompletionResultType
import org.jetbrains.plugins.terminal.block.completion.powershell.PowerShellCompletionResultWithContext
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.session.ShellName
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellBasedCompletionListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import java.io.File
import javax.swing.Icon
import kotlin.coroutines.resume

internal class PowerShellCompletionContributor : TerminalCommandCompletionContributor {
  override suspend fun getCompletionSuggestions(context: TerminalCommandCompletionContext): TerminalCommandCompletionResult? {
    if (context.isAutoPopup || !ShellName.isPowerShell(context.shellName)) {
      return null
    }

    context.terminalView.sendText(CALL_COMPLETION_SEQUENCE)

    val result: String = awaitCompletionResult(context.shellIntegration)
    LOG.trace { "PowerShell completion result: $result" }

    val json = Json { ignoreUnknownKeys = true }
    val completionResult: PowerShellCompletionResultWithContext = try {
      json.decodeFromString(result)
    }
    catch (ex: Exception) {
      LOG.error("Failed to parse PowerShell completion result: $result", ex)
      return null
    }

    if (completionResult.matches.isEmpty()) {
      return null
    }

    // Prefix for which completion items were provided in PowerShell
    val powerShellPrefix = completionResult.commandText.substring(completionResult.replacementIndex, completionResult.cursorIndex)
    val prefixLength = powerShellPrefix.length

    // Prefix at the moment of completion invocation in the IDE
    val localCursorOffset = (context.initialCursorOffset - context.commandStartOffset).toInt()
    val typedPrefix = context.commandText.substring(localCursorOffset - prefixLength, localCursorOffset)

    if (powerShellPrefix != typedPrefix) {
      // Command text for which completion was requested is outdated or incorrect.
      return null
    }

    val suggestions = completionResult.matches.map { toShellSuggestion(it) }
    // The smallest prefix that should be used for filtering completion popup options
    val prefix = findBestPrefix(typedPrefix, completionResult.matches)
    val beforePrefixReplacementLength = typedPrefix.removeSuffix(prefix).length
    val afterPrefixReplacementLength = completionResult.replacementIndex + completionResult.replacementLength - completionResult.cursorIndex

    return TerminalCommandCompletionResult(
      suggestions,
      prefix,
      beforePrefixReplacementLength,
      afterPrefixReplacementLength,
    )
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
   * Determines the best prefix match between typed text and completion item labels.
   * For example:
   * 1. Typed prefix: `-Pa`, label: `Parameter` -> returns `Pa`
   * 2. Typed prefix: `C:\Users\doc`, label: `Documents` -> returns `doc`
   */
  private fun findBestPrefix(typedPrefix: String, matches: List<PowerShellCompletionItem>): String {
    check(matches.isNotEmpty())

    // Use the first match to determine the relationship between the typed text and the label
    val firstMatch = matches[0]
    val label = firstMatch.presentableText ?: firstMatch.value

    // We look for the longest trailing substring of typedPrefix that matches the start of the label
    for (i in 0 until typedPrefix.length) {
      val candidate = typedPrefix.substring(i)
      if (label.startsWith(candidate, ignoreCase = true)) {
        return candidate
      }
    }

    return ""
  }

  private fun toShellSuggestion(item: PowerShellCompletionItem): ShellCompletionSuggestion {
    var lookupString = item.presentableText ?: item.value
    var insertValue = item.value

    // PROVIDER_CONTAINER type is used for directory names.
    // PowerShell provides them without a trailing file separator, so let's add it.
    if (item.type == PowerShellCompletionResultType.PROVIDER_CONTAINER) {
      val separator = File.separator
      lookupString = if (lookupString.endsWith(separator)) lookupString else lookupString + separator
      insertValue = insertValue.appendQuotesAware(separator)
    }

    // Set the cursor position inside the insert string.
    // If the value is in quotes, the cursor will be placed before the closing quote.
    insertValue = insertValue.appendQuotesAware("{cursor}")

    return ShellCompletionSuggestion(lookupString) {
      insertValue(insertValue)
      type(item.type.toShellSuggestionType())

      val typeIcon = item.type.getIcon()
      if (typeIcon != null) icon(typeIcon)
    }
  }

  private fun PowerShellCompletionResultType.toShellSuggestionType(): ShellSuggestionType {
    return when (this) {
      PowerShellCompletionResultType.PROVIDER_ITEM -> ShellSuggestionType.FILE
      PowerShellCompletionResultType.PROVIDER_CONTAINER -> ShellSuggestionType.FOLDER
      else -> ShellSuggestionType.ARGUMENT
    }
  }

  private fun PowerShellCompletionResultType.getIcon(): Icon? {
    return when (this) {
      PowerShellCompletionResultType.COMMAND, PowerShellCompletionResultType.METHOD, PowerShellCompletionResultType.HISTORY -> {
        TerminalIcons.Command
      }
      PowerShellCompletionResultType.PARAMETER_NAME -> TerminalIcons.Option
      PowerShellCompletionResultType.PROVIDER_CONTAINER, PowerShellCompletionResultType.PROVIDER_ITEM -> {
        null  // Icons for files/directories will be calculated by the core completion logic.
      }
      else -> TerminalIcons.Other
    }
  }

  /**
   * Appends [value] to the string taking quotes into account.
   * Also, it takes the PowerShell invocation prefix into account in cases like `& 'C:\Program Files\'`
   *
   * If it is surrounded by quotes, the value is added before the closing quote.
   * If it is already ends with [value], nothing is appended.
   */
  private fun String.appendQuotesAware(value: String): String {
    var str = this

    val invocationPrefix = "& "
    var shouldAddInvocationPrefix = false
    if (str.startsWith(invocationPrefix)) {
      str = str.removePrefix(invocationPrefix)
      shouldAddInvocationPrefix = true
    }

    str = if (str.isSurroundedByQuotes()) {
      val quote = str.first().toString()
      val withoutQuotes = str.removeSurrounding(quote)
      val withAddedValue = if (withoutQuotes.endsWith(value)) withoutQuotes else withoutQuotes + value
      quote + withAddedValue + quote
    }
    else if (str.endsWith(value)) {
      str
    }
    else {
      str + value
    }

    return if (shouldAddInvocationPrefix) invocationPrefix + str else str
  }

  private fun String.isSurroundedByQuotes(): Boolean {
    return isSurroundedBy("'") || isSurroundedBy("\"")
  }

  private fun String.isSurroundedBy(value: String): Boolean {
    return startsWith(value) && endsWith(value)
  }

  companion object {
    /**
     * Sequence that is sent to the shell when pressing `F12, e`.
     * Our PowerShell integration script binds completion to this sequence.
     */
    private const val CALL_COMPLETION_SEQUENCE = "\u001b[24~e"

    private val LOG = logger<PowerShellCompletionContributor>()
  }
}