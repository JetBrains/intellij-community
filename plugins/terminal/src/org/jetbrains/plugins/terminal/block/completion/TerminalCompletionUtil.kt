// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.terminal.completion.spec.*
import com.intellij.terminal.completion.spec.ShellSuggestionType.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalIcons
import org.jetbrains.plugins.terminal.util.ShellType
import javax.swing.Icon

@ApiStatus.Internal
object TerminalCompletionUtil {
  fun getNextSuggestionsString(suggestion: ShellCompletionSuggestion): String {
    var separator: String? = null
    val result = when (suggestion) {
      is ShellCommandSpec -> getNextOptionsAndArgumentsString(suggestion)
      is ShellOptionSpec -> {
        separator = suggestion.separator
        getNextArgumentsString(suggestion.arguments)
      }
      else -> ""
    }
    return if (result.isNotEmpty()) "${separator ?: " "}$result" else ""
  }

  /** Returns required options and all arguments */
  private fun getNextOptionsAndArgumentsString(spec: ShellCommandSpec): String {
    return buildString {
      // Search required options only from static options.
      // Searching all the options requires running ShellRuntimeDataGenerator, that can be slow.
      for (option in spec.options.filter { it.isRequired }) {
        append(option.name)
        val arguments = getNextArgumentsString(option.arguments)
        if (arguments.isNotEmpty()) {
          append(' ')
          append(arguments)
        }
        append(' ')
      }
      append(getNextArgumentsString(spec.arguments))
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

  fun ShellType.toShellName(): ShellName {
    return ShellName(this.toString().lowercase())
  }
}
