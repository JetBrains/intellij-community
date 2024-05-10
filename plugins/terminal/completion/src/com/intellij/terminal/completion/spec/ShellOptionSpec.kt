package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellOptionSpec : ShellCompletionSuggestion {
  override val type: ShellSuggestionType
    get() = ShellSuggestionType.OPTION

  val isRequired: Boolean
  val isPersistent: Boolean
  val separator: String?
  val repeatTimes: Int
  val exclusiveOn: List<String>
  val dependsOn: List<String>

  val arguments: List<ShellArgumentSpec>
}