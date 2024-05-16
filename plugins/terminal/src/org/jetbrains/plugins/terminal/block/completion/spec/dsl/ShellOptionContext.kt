// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import org.jetbrains.annotations.ApiStatus

/**
 * DSL for declaring the content and arguments of the Shell option.
 */
@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellOptionContext : ShellSuggestionContext {
  /**
   * Whether this option can be available for all subcommands of the current Shell command.
   *
   * False by default.
   */
  var isPersistent: Boolean

  /**
   * Whether this option always must be present in the Shell command call.
   *
   * False by default.
   */
  var isRequired: Boolean

  /**
   * The separator between the option name and the argument value (if option has an argument).
   * For example, in case of `--opt=value`, the separator should be `=`.
   *
   * Null (no separator) by default.
   */
  var separator: String?

  /**
   * The amount of times this option can be present in the command line.
   * Zero value means that it can be repeated infinitely.
   *
   * One by default.
   */
  var repeatTimes: Int

  /**
   * Names of the options with those this option cannot be used.
   *
   * Empty list by default.
   */
  var exclusiveOn: List<String>

  /**
   * Names of the options required to use this option.
   *
   * Empty list by default.
   */
  var dependsOn: List<String>

  /**
   * Specifies that this Shell option should have an argument.
   * Note that arguments are not optional by default.
   * If your argument is not necessary to be specified, then set [ShellArgumentContext.isOptional] to true.
   * Arguments should be defined in the same order as it is expected in the command line.
   * @param [content] description of the argument
   */
  fun argument(content: ShellArgumentContext.() -> Unit = {})
}