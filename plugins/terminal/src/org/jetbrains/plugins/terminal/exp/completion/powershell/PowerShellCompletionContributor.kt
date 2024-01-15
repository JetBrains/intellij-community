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

  companion object {
    private val LOG: Logger = logger<PowerShellCompletionContributor>()
  }
}