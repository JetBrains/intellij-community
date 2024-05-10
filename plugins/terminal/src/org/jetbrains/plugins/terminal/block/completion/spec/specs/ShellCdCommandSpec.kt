// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.specs

import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.getParentPath
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.getFileSuggestions

internal fun cdCommandSpec() = ShellCommandSpec("cd") {
  description = TerminalBundle.messagePointer("cd.command.description")
  argument {
    displayName = TerminalBundle.messagePointer("cd.command.arg.displayName")

    val generator = ShellRuntimeDataGenerator(
      debugName = "cd suggestions",
      getCacheKey = { "cd suggestions:${getParentPath(it.typedPrefix)}" }
    ) { context ->
      val path = getParentPath(context.typedPrefix)
      val directorySuggestions = context.getFileSuggestions(path, onlyDirectories = true)
      // Do not add additional suggestions if we are completing the nested path.
      // For example, if typedPrefix is like this: 'src/incompletePath'
      if (path.isEmpty()) {
        directorySuggestions + additionalSuggestions()
      }
      else directorySuggestions
    }
    suggestions(generator)
  }
}

private fun additionalSuggestions(): List<ShellCompletionSuggestion> = listOf(
  ShellCompletionSuggestion("-", description = TerminalBundle.messagePointer("cd.command.arg.dash.description")),
  ShellCompletionSuggestion("~", description = TerminalBundle.messagePointer("cd.command.arg.tilda.description"))
)