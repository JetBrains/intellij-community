// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.specs.make

import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.completion.spec.*
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator

@ApiStatus.Internal
object ShellMakeCommandSpec {

  fun create(): ShellCommandSpec = ShellCommandSpec("make") {
    description(TerminalBundle.messagePointer("make.command.description"))
    argument {
      isVariadic = true

      displayName(TerminalBundle.messagePointer("make.command.arg.displayName"))

      val generator = ShellRuntimeDataGenerator(
        debugName = "make suggestions",
        getCacheKey = { "make suggestions:${it.currentDirectory}" }
      ) { context ->
        val path = context.currentDirectory
        context.getMakefileSuggestions("$path/Makefile")
      }

      suggestions(generator)
    }
  }

  private suspend fun ShellRuntimeContext.getMakefileSuggestions(
    path: String
  ): List<ShellCompletionSuggestion> {
    if (path.isBlank()) return emptyList()
    val catCommand = if (shellName.isBash() || shellName.isZsh()) "command cat $path" else "cat $path"
    val result = runShellCommand(catCommand)
    if (result.exitCode != 0) {
      logger<ShellRuntimeContext>().warn("Get file command for path '$path' failed with exit code ${result.exitCode}, output: ${result.output}")
      return emptyList()
    }
    return parseMakefileForSuggestions(result.output)
  }

  private fun parseMakefileForSuggestions(makefileContents: String): List<ShellCompletionSuggestion> {
    return makefileContents.splitToSequence("\n", "\r", "\r\n")
      .mapNotNull { MakefileTarget.parse(it) }
      .map { makefileTarget ->
        ShellCompletionSuggestion(
          makefileTarget.name,
          ShellSuggestionType.ARGUMENT,
          null,
          listOfNotNull(
            makefileTarget.comment
              .nullize(true),
            makefileTarget.dependencies
              .takeIf { it.isNotEmpty() }
              ?.joinToString(" ")
              ?.let { "Dependencies: $it" }
          ).joinToString("\n").takeIf { it.isNotBlank() }
        )
      }
      .toList()
  }

}
