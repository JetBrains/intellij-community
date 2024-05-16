package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

/**
 * Represents the specification of the Shell option with [names].
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellOptionSpec : ShellCompletionSuggestion {
  override val type: ShellSuggestionType
    get() = ShellSuggestionType.OPTION

  /**
   * Whether this option can be available for all subcommands of the current Shell command.
   *
   * False by default.
   */
  val isRequired: Boolean

  /**
   * Whether this option always must be present in the Shell command call.
   *
   * False by default.
   */
  val isPersistent: Boolean

  /**
   * The separator between the option name and the argument value (if option has an argument).
   * For example, in case of `--opt=value`, the separator should be `=`.
   *
   * Null (no separator) by default.
   */
  val separator: String?

  /**
   * The amount of times this option can be present in the command line.
   * Zero value means that it can be repeated infinitely.
   *
   * One by default.
   */
  val repeatTimes: Int

  /**
   * Names of the options with those this option cannot be used.
   *
   * Empty list by default.
   */
  val exclusiveOn: List<String>

  /**
   * Names of the options required to use this option.
   *
   * Empty list by default.
   */
  val dependsOn: List<String>

  /**
   * Available arguments of this option.
   * Note that the order of the arguments is the same as it is expected in the command line
   */
  val arguments: List<ShellArgumentSpec>
}