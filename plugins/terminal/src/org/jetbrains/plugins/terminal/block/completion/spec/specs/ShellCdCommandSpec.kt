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

internal fun cdCommandSpec() = ShellCommandSpec("cd") {
  description(TerminalBundle.messagePointer("cd.command.description"))
  argument {
    displayName(TerminalBundle.messagePointer("cd.command.arg.displayName"))

    val generator = ShellRuntimeDataGenerator(
      debugName = "cd suggestions",
      getCacheKey = { "cd suggestions:${getParentPath(it.typedPrefix)}" }
    ) { context ->
      val path = getParentPath(context.typedPrefix)
      val directories = context.getChildFiles(path, onlyDirectories = true)
      val prefixReplacementIndex = path.length + if (context.typedPrefix.startsWith('"')) 1 else 0
      val suggestions = directories.map {
        ShellCompletionSuggestion(name = it, type = ShellSuggestionType.FOLDER, prefixReplacementIndex = prefixReplacementIndex)
      }
      // Do not add additional suggestions if we are completing the nested path.
      // For example, if typedPrefix is like this: 'src/incompletePath'
      if (path.isEmpty()) {
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
