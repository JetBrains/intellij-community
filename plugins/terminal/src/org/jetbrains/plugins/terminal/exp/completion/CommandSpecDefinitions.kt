// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import kotlinx.serialization.Serializable

@Serializable
data class ShellSubcommand(
  val names: List<String>,
  val requiresSubcommand: Boolean = false,

  val subcommands: List<ShellSubcommand> = emptyList(),
  val options: List<ShellOption> = emptyList(),
  val args: List<ShellArgument> = emptyList(),
  val additionalSuggestions: List<ShellSuggestion> = emptyList(),

  val loadSpec: String? = null,
  val parserDirectives: ShellCommandParserDirectives = DEFAULT_PARSER_DIRECTIVES,

  val displayName: String? = null,
  val insertValue: String? = null,
  val description: String? = null,
  val priority: Int = 50,
  val hidden: Boolean = false
)

@Serializable
data class ShellOption(
  val names: List<String>,
  val args: List<ShellArgument> = emptyList(),
  val isPersistent: Boolean = false,
  val isRequired: Boolean = false,
  val separator: String? = null,
  val repeatTimes: Int = 1,
  val exclusiveOn: List<String> = emptyList(),
  val dependsOn: List<String> = emptyList(),

  val displayName: String? = null,
  val insertValue: String? = null,
  val description: String? = null,
  val priority: Int = 50,
  val hidden: Boolean = false
)

@Serializable
data class ShellArgument(
  val name: String? = null,
  val description: String? = null,
  val suggestions: List<ShellSuggestion> = emptyList(),
  val templates: List<String> = emptyList(),
  val generators: List<ShellSuggestionsGenerator> = emptyList(),
  val isVariadic: Boolean = false,
  val optionsCanBreakVariadicArg: Boolean = false,
  val isOptional: Boolean = false,
  val isCommand: Boolean = false,
  val default: String? = null,
  val loadSpec: String? = null
)

@Serializable
data class ShellSuggestion(
  val names: List<String> = emptyList(),
  val type: String? = null,

  val displayName: String? = null,
  val insertValue: String? = null,
  val description: String? = null,
  val priority: Int = 50,
  val hidden: Boolean = false
)

@Serializable
data class ShellSuggestionsGenerator(
  val templates: List<String> = emptyList(),
  val script: String? = null,
  val splitOn: String? = null,
)

@Serializable
data class ShellCommandParserDirectives(
  val flagsArePosixNoncompliant: Boolean = false,
  val optionsMustPrecedeArguments: Boolean = false,
  val optionArgSeparators: List<String> = emptyList()
)

internal val DEFAULT_PARSER_DIRECTIVES = ShellCommandParserDirectives()