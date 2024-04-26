// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.terminal.block.completion.ShellDataGeneratorsExecutor
import com.intellij.terminal.block.completion.spec.*
import com.intellij.terminal.block.completion.spec.ShellSuggestionType.*
import org.jetbrains.plugins.terminal.TerminalIcons
import javax.swing.Icon

internal object TerminalCompletionUtil {
  suspend fun getNextSuggestionsString(
    suggestion: ShellCompletionSuggestion,
    context: ShellRuntimeContext,
    generatorsExecutor: ShellDataGeneratorsExecutor
  ): String {
    val result = when (suggestion) {
      is ShellCommandSpec -> getNextOptionsAndArgumentsString(suggestion, context, generatorsExecutor)
      is ShellOptionSpec -> getNextArgumentsString(suggestion.arguments)
      else -> ""
    }
    return if (result.isNotEmpty()) " $result" else ""
  }

  /** Returns required options and all arguments */
  private suspend fun getNextOptionsAndArgumentsString(
    spec: ShellCommandSpec,
    context: ShellRuntimeContext,
    generatorsExecutor: ShellDataGeneratorsExecutor
  ): String {
    val nextOptions = generatorsExecutor.execute(context, spec.optionsGenerator).filter { it.isRequired }
    val nextArguments = generatorsExecutor.execute(context, spec.argumentsGenerator)
    return buildString {
      for (option in nextOptions) {
        append(option.names.first())
        val arguments = getNextArgumentsString(option.arguments)
        if (arguments.isNotEmpty()) {
          append(' ')
          append(arguments)
        }
        append(' ')
      }
      append(getNextArgumentsString(nextArguments))
    }.trim()
  }

  private fun getNextArgumentsString(args: List<ShellArgumentSpec>): String {
    val argStrings = args.mapIndexed { index, arg -> arg.asSuggestionString(index) }
    return argStrings.joinToString(" ")
  }

  private fun ShellArgumentSpec.asSuggestionString(index: Int): String {
    val name = displayName ?: "arg${index + 1}"
    return if (isOptional) "[$name]" else "<$name>"
  }

  fun findIconForSuggestion(name: String, type: ShellSuggestionType): Icon? {
    return when (type) {
      COMMAND -> TerminalIcons.Command
      OPTION -> TerminalIcons.Option
      FOLDER -> AllIcons.Nodes.Folder
      FILE -> getFileIcon(name)
      ARGUMENT -> TerminalIcons.Other
      else -> null
    }
  }

  fun getFileIcon(fileName: String): Icon {
    val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName)
    val fileIcon = fileType.icon ?: TerminalIcons.OtherFile
    return if (fileType is UnknownFileType) TerminalIcons.OtherFile else fileIcon
  }
}