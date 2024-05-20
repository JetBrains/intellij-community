package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Represents the specification of the Shell argument.
 *
 * Shell argument is the value of the parameter used in the Shell command or option.
 * For example, `ls` command has an argument, that should be a file name.
 * Or `-d` option of the `git branch` command accepts branch name as an argument.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellArgumentSpec {
  /**
   * The short name used to describe the meaning of this argument. For example `path`.
   */
  val displayName: @Nls String?

  /**
   * Whether this argument is not required to have a value.
   *
   * False by default (the argument is required).
   */
  val isOptional: Boolean

  /**
   * Whether this argument can be repeated infinitely.
   * For example, `git add` takes a variadic argument of filenames.
   *
   * False by default.
   */
  val isVariadic: Boolean

  /**
   * Whether the options can be placed between values of the variadic argument.
   * For example, it is true for `git add` command.
   * We can write like this: `git add file1 file2 -v file3`. Where `-v` is the option.
   *
   * True by default.
   */
  val optionsCanBreakVariadicArg: Boolean

  /**
   * List of [ShellRuntimeDataGenerator]'s used for providing the value suggestions of this argument.
   * Generators are executed at the moment of requesting the argument values with the specified [ShellRuntimeContext].
   */
  val generators: List<ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>>>
}
