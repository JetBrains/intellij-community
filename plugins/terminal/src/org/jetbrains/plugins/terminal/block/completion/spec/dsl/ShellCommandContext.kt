// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.block.completion.spec.ShellCommandParserDirectives
import com.intellij.terminal.block.completion.spec.ShellRuntimeContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellCommandContext : ShellSuggestionContext {
  var requiresSubcommand: Boolean
  var parserDirectives: ShellCommandParserDirectives

  fun subcommands(content: suspend ShellChildCommandsContext.(ShellRuntimeContext) -> Unit)
  fun options(content: ShellChildOptionsContext.() -> Unit)
  fun argument(content: ShellArgumentContext.() -> Unit = {})
}