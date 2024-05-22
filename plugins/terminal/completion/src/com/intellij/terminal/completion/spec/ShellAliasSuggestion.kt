package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

/**
 * Represents an alias provided by the parent command and the way to resolve such an alias.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellAliasSuggestion : ShellCompletionSuggestion {
  /**
   * String to resolve the alias to.
   */
  val aliasValue: String
}
