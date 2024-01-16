// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion.powershell

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.util.DocumentUtil
import com.intellij.util.PathUtil
import org.jetbrains.plugins.terminal.TerminalIcons
import org.jetbrains.plugins.terminal.exp.BlockTerminalSession
import org.jetbrains.plugins.terminal.exp.completion.ShellCommandExecutor
import org.jetbrains.plugins.terminal.exp.completion.TerminalCompletionUtil
import org.jetbrains.plugins.terminal.util.ShellType
import javax.swing.Icon
import kotlin.math.min

internal class PowerShellCompletionContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val session = parameters.editor.getUserData(BlockTerminalSession.KEY)
    val shellCommandExecutor = parameters.editor.getUserData(ShellCommandExecutor.KEY)
    if (session == null
        || shellCommandExecutor == null
        || session.shellIntegration.shellType != ShellType.POWERSHELL
        || parameters.completionType != CompletionType.BASIC) {
      return
    }

    val command = parameters.editor.document.text
    val caretPosition = parameters.editor.caretModel.offset
    val completionResult: CompletionResult? = runBlockingCancellable {
      shellCommandExecutor.executeCommand(GetShellCompletionsCommand(command, caretPosition))
    }
    if (completionResult?.matches?.isNotEmpty() != true) {
      return
    }

    val replacementIndex = completionResult.replacementIndex
    val replacementLength = completionResult.replacementLength
    val prefix = if (replacementIndex >= 0 && replacementLength >= 0 && replacementIndex + replacementLength <= command.length) {
      val endIndex = min(caretPosition, replacementIndex + replacementLength)
      command.substring(replacementIndex, endIndex)
    }
    else {
      LOG.error("""Incorrect completion replacement indexes.
        |Command: '$command'
        |CaretPosition: $caretPosition
        |Completion Result: $completionResult""".trimMargin())
      result.prefixMatcher.prefix
    }

    val resultSet = result.withPrefixMatcher(PlainPrefixMatcher(prefix))
    val elements = completionResult.matches.map { it.toLookupElement() }
    resultSet.addAllElements(elements)
  }

  private fun CompletionItem.toLookupElement(): LookupElement {
    return LookupElementBuilder.create(value)
      .withPresentableText(presentableText ?: value)
      .withIcon(getIconForItem(this))
      .surroundingQuotesAware()
  }

  private fun getIconForItem(item: CompletionItem): Icon {
    return when (item.type) {
      CompletionResultType.COMMAND, CompletionResultType.METHOD -> TerminalIcons.Command
      CompletionResultType.PARAMETER_NAME -> TerminalIcons.Option
      CompletionResultType.PROVIDER_CONTAINER -> AllIcons.Nodes.Folder
      CompletionResultType.PROVIDER_ITEM -> {
        val fileName = PathUtil.getFileName(item.value)
        TerminalCompletionUtil.getFileIcon(fileName)
      }
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
  private fun LookupElementBuilder.surroundingQuotesAware(): LookupElementBuilder {
    if (lookupString.length < 2) return this
    val delimiters = listOf('\'', '\"')
    val delimiter = delimiters.firstOrNull { lookupString.first() == it && lookupString.last() == it }
                    ?: return this
    return withInsertHandler { context, item ->
      insertHandler?.handleInsert(context, item)
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

  companion object {
    private val LOG: Logger = logger<PowerShellCompletionContributor>()
  }
}