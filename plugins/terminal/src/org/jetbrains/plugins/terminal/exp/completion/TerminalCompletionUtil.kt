// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.terminal.completion.CommandSpecCompletionUtil.isFilePath
import com.intellij.terminal.completion.CommandSpecCompletionUtil.isFolder
import com.intellij.terminal.completion.ShellArgumentSuggestion
import org.jetbrains.plugins.terminal.TerminalIcons
import org.jetbrains.terminal.completion.BaseSuggestion
import org.jetbrains.terminal.completion.ShellArgument
import org.jetbrains.terminal.completion.ShellCommand
import org.jetbrains.terminal.completion.ShellOption
import javax.swing.Icon

internal object TerminalCompletionUtil {
  fun getNextSuggestionsString(suggestion: BaseSuggestion): String {
    val result = when (suggestion) {
      is ShellCommand -> getNextOptionsAndArgumentsString(suggestion)
      is ShellOption -> getNextArgumentsString(suggestion.args)
      else -> ""
    }
    return if (result.isNotEmpty()) " $result" else ""
  }

  /** Returns required options and all arguments */
  private fun getNextOptionsAndArgumentsString(command: ShellCommand): String {
    val nextOptions = command.options.filter { it.isRequired }
    return buildString {
      for (option in nextOptions) {
        append(option.names.first())
        val arguments = getNextArgumentsString(option.args)
        if (arguments.isNotEmpty()) {
          append(' ')
          append(arguments)
        }
        append(' ')
      }
      append(getNextArgumentsString(command.args))
    }.trim()
  }

  private fun getNextArgumentsString(args: List<ShellArgument>): String {
    val argStrings = args.mapIndexed { index, arg -> arg.asSuggestionString(index) }
    return argStrings.joinToString(" ")
  }

  private fun ShellArgument.asSuggestionString(index: Int): String {
    val name = displayName ?: "arg${index + 1}"
    return if (isOptional) "[$name]" else "<$name>"
  }

  fun findIconForSuggestion(suggestion: BaseSuggestion): Icon? {
    return when (suggestion) {
      is ShellCommand -> TerminalIcons.Command
      is ShellOption -> TerminalIcons.Option
      is ShellArgumentSuggestion -> suggestion.findIcon()
      else -> null
    }
  }

  private fun ShellArgumentSuggestion.findIcon(): Icon {
    return if (argument.isFilePath() || argument.isFolder()) {
      getFileIcon(names.first())
    }
    else TerminalIcons.Other
  }

  private fun getFileIcon(fileName: String): Icon {
    return if (fileName.endsWith("/") || fileName == "~" || fileName == "-") {
      AllIcons.Nodes.Folder
    }
    else {
      val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName)
      val fileIcon = fileType.icon ?: TerminalIcons.OtherFile
      if (fileType is UnknownFileType) TerminalIcons.OtherFile else fileIcon
    }
  }
}