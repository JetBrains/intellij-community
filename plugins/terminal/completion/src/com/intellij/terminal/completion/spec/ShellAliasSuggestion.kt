package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

/**
 * Represents an alias provided by the parent command and the way to resolve such an alias.
 *
 * For example, consider `git` command provides an alias suggestion with name `ch` and alias value `checkout`.
 * Now, if you type `git ch`, the completion logic will recognize it as a real command `git checkout`
 * and provide suggestions for `checkout` subcommand.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellAliasSuggestion : ShellCompletionSuggestion {
  /**
   * String to resolve the alias to.
   */
  val aliasValue: String
}
