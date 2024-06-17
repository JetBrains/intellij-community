// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.specs

import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellSuggestionType
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.getParentPath
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.getChildFiles
import java.io.File

internal fun cdCommandSpec() = ShellCommandSpec("cd") {
  description(TerminalBundle.messagePointer("cd.command.description"))
  argument {
    displayName(TerminalBundle.messagePointer("cd.command.arg.displayName"))

    val generator = ShellRuntimeDataGenerator(
      debugName = "cd suggestions",
      getCacheKey = { "cd suggestions:${getParentPath(it.typedPrefix, it.shellName)}" }
    ) { context ->
      val path = getParentPath(context.typedPrefix, context.shellName)
      val directories = context.getChildFiles(path, onlyDirectories = true)
      val prefixReplacementIndex = path.length + if (context.typedPrefix.startsWith('"')) 1 else 0
      val suggestions = directories.flatMap {
        val suggestion = ShellCompletionSuggestion(name = it, type = ShellSuggestionType.FOLDER, prefixReplacementIndex = prefixReplacementIndex)
        val hiddenSuggestion = ShellCompletionSuggestion(
          name = it.removeSuffix(File.separator),
          type = ShellSuggestionType.FOLDER,
          prefixReplacementIndex = prefixReplacementIndex,
          isHidden = true
        )
        listOf(suggestion, hiddenSuggestion)
      }
      val adjustedPrefix = context.typedPrefix.removePrefix("\"").removeSuffix("'")
      if (path.isNotEmpty() && path == adjustedPrefix) {
        val emptySuggestion = ShellCompletionSuggestion(name = "", prefixReplacementIndex = prefixReplacementIndex, isHidden = true)
        suggestions + emptySuggestion
      }
      else if (path.isEmpty()) {
        // Add additional suggestions only if we are not completing the nested directory.
        // For example, if typedPrefix is like this: 'somePath'.
        // But not like this: 'src/somePath'.
        suggestions + additionalSuggestions()
      }
      else suggestions
    }
    suggestions(generator)
  }
}

private fun additionalSuggestions(): List<ShellCompletionSuggestion> = listOf(
  ShellCompletionSuggestion("-", description = TerminalBundle.message("cd.command.arg.dash.description")),
  ShellCompletionSuggestion("~", description = TerminalBundle.message("cd.command.arg.tilda.description"))
)
