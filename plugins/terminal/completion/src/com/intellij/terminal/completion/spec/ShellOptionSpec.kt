package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

/**
 * Represents the specification of the Shell option with [name].
 *
 * Shell options also can be named as keys or flags in the shell command.
 * Usually, the option is starting with `-` for a short option or `--` for a long one.
 * For example `-a`, `-l` are the short options, while `--long` is a more verbose option.
 * But generally, any meaningful string can be an option, even without `-`.
 * Shell options can have their own arguments.
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
   * Whitespace is used as a separator by default (but the value of the property is null in this case).
   */
  val separator: String?

  /**
   * The maximum number of times this option can be present in the command line.
   * Zero value means that it can be repeated infinitely.
   * If the option is already present this number of times in the command, it won't be shown in the completion popup anymore.
   *
   * One by default.
   */
  val repeatTimes: Int

  /**
   * Names of the options with those this option cannot be used.
   * If any of such options is used in the command, this option won't be shown in the completion popup.
   *
   * Empty list by default.
   */
  val exclusiveOn: List<String>

  /**
   * Names of the options required to use this option.
   * Until all such options are used in the command, this option won't be shown in the completion popup.
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
