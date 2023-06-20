// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import kotlinx.serialization.Serializable

abstract class BaseSuggestion {
  abstract val names: List<String>
  abstract val displayName: String?
  abstract val insertValue: String?
  abstract val description: String?
  abstract val priority: Int
  abstract val hidden: Boolean
}

@Serializable
data class ShellSubcommand(
  override val names: List<String>,
  val requiresSubcommand: Boolean = false,

  val subcommands: List<ShellSubcommand> = emptyList(),
  val options: List<ShellOption> = emptyList(),
  val args: List<ShellArgument> = emptyList(),
  val additionalSuggestions: List<ShellSuggestion> = emptyList(),

  val loadSpec: String? = null,
  val parserDirectives: ShellCommandParserDirectives = DEFAULT_PARSER_DIRECTIVES,

  override val displayName: String? = null,
  override val insertValue: String? = null,
  override val description: String? = null,
  override val priority: Int = 50,
  override val hidden: Boolean = false
) : BaseSuggestion()

@Serializable
data class ShellOption(
  override val names: List<String>,
  val args: List<ShellArgument> = emptyList(),
  val isPersistent: Boolean = false,
  val isRequired: Boolean = false,
  val separator: String? = null,
  val repeatTimes: Int = 1,  // can be 0, it means that option can be repeated infinitely
  val exclusiveOn: List<String> = emptyList(),
  val dependsOn: List<String> = emptyList(),

  override val displayName: String? = null,
  override val insertValue: String? = null,
  override val description: String? = null,
  override val priority: Int = 50,
  override val hidden: Boolean = false
) : BaseSuggestion()

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
  override val names: List<String> = emptyList(),
  val type: String? = null,

  override val displayName: String? = null,
  override val insertValue: String? = null,
  override val description: String? = null,
  override val priority: Int = 50,
  override val hidden: Boolean = false
) : BaseSuggestion()

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