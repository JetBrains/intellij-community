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
   * Specifies that this argument is not required to have a value.
   *
   * By default, the argument is required.
   */
  fun optional()

  @Deprecated("Please use optional() method instead")
  var isOptional: Boolean

  /**
   * Specifies that this argument can be repeated infinitely.
   * For example, `git add` takes a variadic argument of filenames.
   *
   * By default, the argument value can be specified only once.
   */
  fun variadic()

  @Deprecated("Please use variadic() method instead")
  var isVariadic: Boolean

  /**
   * Specifies that the options can't be placed between values of the variadic argument.
   * By default, this behavior is allowed.
   *
   * For example, it is not allowed for `echo` command.
   * If we write: `echo hello -n world`, `-n` will be considered as a variadic argument value rather than an option.
   * So, we should write `echo -n hello world` instead.
   */
  fun optionsCantBreakVariadicArg()

  /**
   * Generate suggestions for the argument values.
   * Note that the result of [content] execution is cached until the user executes the next command in the Terminal.
   * If you need custom caching logic or don't need it at all,
   * use another [suggestions] method overload and provide the ready to use [ShellRuntimeDataGenerator].
   *
   * Use [helper function][org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion]
   * to create [ShellCompletionSuggestion] objects.
   *
   * @param content is a suspending function that will be executed at the moment of requesting the argument value suggestions.
   * Inside [content] you can access the values of [ShellRuntimeContext] and generate the list of suggestions depending on
   * current shell directory, typed prefix, project, and so on.
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
   *
   * @param names the names of the suggestions.
   * Name is used to filter the completion popup to show only relevant items.
   * Also, it is inserted when chosen from the popup, if [ShellSuggestionContext.insertValue] is not specified.
   * Also, it is shown in the completion popup, if [ShellCompletionSuggestion.displayName] is not specified.
   */
  fun suggestions(vararg names: String)
}
