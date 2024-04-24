package com.intellij.terminal.block.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellCommandSpec : ShellCompletionSuggestion {
  override val type: ShellSuggestionType
    get() = ShellSuggestionType.COMMAND

  val requiresSubcommand: Boolean
  val parserDirectives: ShellCommandParserDirectives

  val subcommandsGenerator: ShellRuntimeDataGenerator<List<ShellCommandSpec>>
  val optionsGenerator: ShellRuntimeDataGenerator<List<ShellOptionSpec>>
  val argumentsGenerator: ShellRuntimeDataGenerator<List<ShellArgumentSpec>>
}