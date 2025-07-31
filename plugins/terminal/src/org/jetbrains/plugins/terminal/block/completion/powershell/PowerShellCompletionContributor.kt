// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.powershell

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.DocumentUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner.Companion.BLOCK_TERMINAL_AUTOCOMPLETION
import org.jetbrains.plugins.terminal.TerminalIcons
import org.jetbrains.plugins.terminal.block.completion.TerminalCompletionUtil
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellDataGeneratorsExecutorImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeContextProviderImpl
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalPromptModel
import org.jetbrains.plugins.terminal.util.ShellType
import java.io.File
import javax.swing.Icon
import kotlin.math.min

@ApiStatus.Internal
class PowerShellCompletionContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(BlockTerminalSession.KEY) ?: return
    val runtimeContextProvider = parameters.editor.getUserData(ShellRuntimeContextProviderImpl.KEY) ?: return
    val generatorsExecutor = parameters.editor.getUserData(ShellDataGeneratorsExecutorImpl.KEY) ?: return
    val promptModel = parameters.editor.terminalPromptModel ?: return

    if (session.model.isCommandRunning || parameters.completionType != CompletionType.BASIC) {
      return
    }

    if (parameters.isAutoPopup && !Registry.`is`(BLOCK_TERMINAL_AUTOCOMPLETION)) {
      result.stopHere()
      return
    }

    // stop even if we can't suggest something to not execute contributors from the ShellScript plugin
    result.stopHere()
    if (session.shellIntegration.shellType != ShellType.POWERSHELL) {
      return
    }

    val command = promptModel.commandText
    val caretPosition = parameters.editor.caretModel.offset - promptModel.commandStartOffset  // relative to command start
    // PowerShell's completion generator receives typed prefix directly, so we can create dummy context
    val runtimeContext = runtimeContextProvider.getContext("")

    val completionResult: CompletionResult = runBlockingCancellable {
      generatorsExecutor.execute(runtimeContext, powerShellCompletionGenerator(command, caretPosition))
    }

    if (completionResult.matches.isEmpty()) {
      return
    }

    val replacementIndex = completionResult.replacementIndex
    val replacementLength = completionResult.replacementLength
    if (replacementIndex < 0 || replacementLength < 0 || replacementIndex + replacementLength > command.length) {
      LOG.error("""Incorrect completion replacement indexes.
        |Command: '$command'
        |CaretPosition: $caretPosition
        |Completion Result: $completionResult""".trimMargin())
      return
    }

    val endIndex = min(caretPosition, replacementIndex + replacementLength)
    val initialPrefix = command.substring(replacementIndex, endIndex)
    val actualReplaceIndex = promptModel.commandStartOffset + replacementIndex  // relative to document start
    // Heuristic: if the initial prefix contains file separator, then we're probably completing the file names.
    // And since powershell provides an absolute file path as the completion item,
    // we need to shorten the prefix to show completion popup near the last file path part.
    // Also, we need to adjust the values of completion items, leaving only the filename part.
    // And remember the initial value to properly replace the prefix on completion.
    val separatorIndex = initialPrefix.lastIndexOfAny(charArrayOf('/', '\\'))  // User can type any path separator
    val (prefix, completionItems) = if (separatorIndex != -1) {
      val shortenedPrefix = initialPrefix.substring(separatorIndex + 1)
      shortenedPrefix to completionResult.matches.map {
        // Completion items should have path separator of the OS, so use it here.
        val lookupString = it.value.removeSurrounding("'").removeSurrounding("\"").substringAfterLast(File.separatorChar)
        CompletionItemInfo(lookupString, it.presentableText, it.type, actualReplaceIndex, replacementString = it.value)
      }
    }
    else {
      initialPrefix to completionResult.matches.map {
        CompletionItemInfo(lookupString = it.value, it.presentableText, it.type, actualReplaceIndex, replacementString = it.value)
      }
    }

    val resultSet = result.withPrefixMatcher(PlainPrefixMatcher(prefix))
    val elements = completionItems.map { it.toLookupElement() }
    resultSet.addAllElements(elements)
  }

  private fun CompletionItemInfo.toLookupElement(): LookupElement {
    var text = presentableText ?: lookupString
    // Add file separator to directories to make it consistent with command spec completion and other shells
    if (type == CompletionResultType.PROVIDER_CONTAINER && !text.endsWith(File.separatorChar)) {
      text += File.separatorChar
    }
    return LookupElementBuilder.create(lookupString)
      .withPresentableText(text)
      .withIcon(getIconForItem(this))
      .replacementStringAware(this) // first insert the correct completion string
      .surroundingQuotesAware(this) // then correct the quotes
      .insertFileSeparatorIfNeeded(this)
  }

  private fun getIconForItem(item: CompletionItemInfo): Icon {
    return when (item.type) {
      CompletionResultType.COMMAND, CompletionResultType.METHOD -> TerminalIcons.Command
      CompletionResultType.PARAMETER_NAME -> TerminalIcons.Option
      CompletionResultType.PROVIDER_CONTAINER -> AllIcons.Nodes.Folder
      CompletionResultType.PROVIDER_ITEM -> TerminalCompletionUtil.getFileIcon(item.lookupString)
      else -> TerminalIcons.Other
    }
  }

  /**
   * For example, when completing file names with spaces PowerShell surround the full path with single quotes, like '.\Documents\My Videos'.
   * In this case, we need to place the caret before the closing quote instead of after it.
   * For example,
   * Before completion: .\Documents\<caret>
   * After completion: '.\Documents\My Videos'<caret>
   * With fix: '.\Documents\My Videos<caret>'
   *
   * Also, if there is already a quote after the inserted quoted string, we need to remove one of the quotes.
   * For example,
   * Before completion: '.\Documents\<caret>'
   * After completion: '.\Documents\My Videos<caret>''
   * With fix: '.\Documents\My Videos<caret>'
   *
   * @return the [LookupElementBuilder] with insert handler that takes into account the cases described above
   */
  private fun LookupElementBuilder.surroundingQuotesAware(itemInfo: CompletionItemInfo): LookupElementBuilder {
    val replacementString = itemInfo.replacementString
    if (replacementString.length < 2) return this
    val delimiters = listOf('\'', '\"')
    val delimiter = delimiters.firstOrNull { replacementString.first() == it && replacementString.last() == it }
                    ?: return this
    return withInsertHandler { context, item ->
      insertHandler?.handleInsert(context, item) // call existing insert handler first
      val document = context.editor.document
      val endOffset = context.tailOffset
      context.editor.caretModel.moveToOffset(endOffset - 1)
      if (endOffset < document.textLength && document.getText(TextRange.create(endOffset, endOffset + 1)) == delimiter.toString()) {
        DocumentUtil.writeInRunUndoTransparentAction {
          document.deleteString(endOffset, endOffset + 1)
        }
      }
    }
  }

  /**
   * The lookup string can differ from the replacement string proposed by PowerShell. For example, in the case of file names completion.
   * The platform completion logic will insert the lookup string, but the result can be not fully correct.
   * So, we need to replace the inserted text with the original replacement string.
   */
  private fun LookupElementBuilder.replacementStringAware(itemInfo: CompletionItemInfo): LookupElementBuilder {
    if (lookupString != itemInfo.replacementString) {
      return withInsertHandler { context, item ->
        insertHandler?.handleInsert(context, item) // call existing insert handler first
        DocumentUtil.writeInRunUndoTransparentAction {
          val endIndex = context.editor.caretModel.offset // end of the inserted lookup string
          context.document.replaceString(itemInfo.replacementIndex, endIndex, itemInfo.replacementString)
        }
      }
    }
    return this
  }

  /**
   * Inserts the file separator after the completed item if it is a directory.
   */
  private fun LookupElementBuilder.insertFileSeparatorIfNeeded(itemInfo: CompletionItemInfo): LookupElementBuilder {
    if (itemInfo.type == CompletionResultType.PROVIDER_CONTAINER) {
      return withInsertHandler { context, item ->
        insertHandler?.handleInsert(context, item) // call existing insert handler first
        DocumentUtil.writeInRunUndoTransparentAction {
          val offset = context.editor.caretModel.offset
          context.document.insertString(offset, File.separator)
          context.editor.caretModel.moveToOffset(offset + 1)
        }
      }
    }
    return this
  }

  private data class CompletionItemInfo(
    /** String to pass to the Lookup. It will be inserted initially by the platform completion logic. */
    val lookupString: String,
    val presentableText: String?,
    val type: CompletionResultType,
    val replacementIndex: Int,
    /** The initial completion string proposed by PowerShell */
    val replacementString: String
  )

  companion object {
    private val LOG: Logger = logger<PowerShellCompletionContributor>()
  }
}
