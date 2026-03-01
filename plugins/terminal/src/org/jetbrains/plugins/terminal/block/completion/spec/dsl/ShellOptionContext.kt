// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import org.jetbrains.annotations.ApiStatus

/**
 * DSL for declaring the content and arguments of the Shell option.
 *
 * Shell options also can be named as keys or flags in the shell command.
 * Usually, the option is starting with `-` for a short option or `--` for a long one.
 * For example `-a`, `-l` are the short options, while `--long` is a more verbose option.
 * But generally, any meaningful string can be an option, even without `-`.
 * Shell options can have their own arguments.
 */
@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellOptionContext : ShellSuggestionContext {
  /**
   * Makes this option available for all subcommands of the current Shell command.
   */
  fun persistent()

  /**
   * Specifies that this option always must be present in the Shell command call.
   */
  fun required()

  /**
   * Specifies the separator between the option name and the argument value (if the option has an argument).
   * For example, in case of `--opt=value`, the separator should be `=`.
   * Whitespace is used as a separator by default.
   *
   * Currently, separators support implementation is not finished.
   * There are several known issues: IJPL-150188, IJPL-157693, IJPL-182543.
   */
  fun separator(separator: String)

  /**
   * Specifies the maximum number of times this option can be present in the command line.
   * Zero value means that it can be repeated infinitely.
   * If the option is already present this number of times in the command, it won't be shown in the completion popup anymore.
   *
   * One by default.
   */
  fun repeatTimes(times: Int)

  /**
   * Specifies the names of the options with those this option cannot be used.
   * If any of such options is used in the command, this option won't be shown in the completion popup.
   */
  fun exclusiveOn(on: List<String>)

  /**
   * Specifies names of the options required to use this option.
   * Until all such options are used in the command, this option won't be shown in the completion popup.
   */
  fun dependsOn(on: List<String>)

  /**
   * Specifies that this Shell option should have an argument.
   * Note that arguments are not optional by default.
   * If your argument is not necessary to be specified, specify [ShellArgumentContext.optional].
   * Arguments should be defined in the same order as it is expected in the command line.
   * @param [content] description of the argument
   */
  fun argument(content: ShellArgumentContext.() -> Unit = {})
}
