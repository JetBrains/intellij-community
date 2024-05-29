// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

/**
 * DSL for declaring the Shell argument: the rules of its usage and how to get the values of it.
 *
 * Shell argument is the value of the parameter used in the Shell command or option.
 * For example, `ls` command has an argument, that should be a file name.
 * Or `-d` option of the `git branch` command accepts branch name as an argument.
 */
@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellArgumentContext {
  /**
   * The short name used to describe the meaning of this argument. For example `path`.
   */
  fun displayName(@Nls text: String)

  /**
   * The short name used to describe the meaning of this argument. For example `path`.
   */
  fun displayName(supplier: Supplier<@Nls String>)

  /**
   * Whether this argument is not required to have a value.
   *
   * False by default (the argument is required).
   */
  var isOptional: Boolean

  /**
   * Specifies that this argument can be repeated infinitely.
   * For example, `git add` takes a variadic argument of filenames.
   *
   * False by default.
   */
  var isVariadic: Boolean

  /**
   * Specifies that the options can be placed between values of the variadic argument.
   * For example, it is true for `git add` command.
   * We can write like this: `git add file1 file2 -v file3`. Where `-v` is the option.
   *
   * True by default.
   */
  var optionsCanBreakVariadicArg: Boolean

  /**
   * Generate suggestions for the argument values.
   * Note that the result of [content] execution is cached until the user executes the next command in the Terminal.
   * If you need custom caching logic or don't need it at all,
   * use another [suggestions] method overload and provide the ready to use [ShellRuntimeDataGenerator].
   *
   * Use [helper function][org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion] to create [ShellCompletionSuggestion].
   *
   * @param content is a suspending function that will be executed at the moment of requesting the argument value suggestions.
   * Inside [content] you can access the values of [ShellRuntimeContext] and generate the list of suggestions depending on
   * current shell directory, typed prefix, project and so on.
   */
  fun suggestions(content: suspend (ShellRuntimeContext) -> List<ShellCompletionSuggestion>)

  /**
   * Generate suggestions for the argument values using your own [ShellRuntimeDataGenerator].
   *
   * Use [helper function][org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator] to create your own
   * [ShellRuntimeDataGenerator] with custom caching logic.
   *
   * Use [helper function][org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion] to create [ShellCompletionSuggestion].
   *
   * @see org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators
   */
  fun suggestions(generator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>>)

  /**
   * Provide the hardcoded values for the argument.
   * It creates a [ShellCompletionSuggestion] for each of the provided [names] with
   * [ARGUMENT][com.intellij.terminal.completion.spec.ShellSuggestionType.ARGUMENT] suggestion type.
   */
  fun suggestions(vararg names: String)
}
