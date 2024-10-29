// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.completion.spec.ShellCommandParserOptions
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import org.jetbrains.annotations.ApiStatus

/**
 * DSL for declaring subcommands, option and arguments of the Shell command.
 *
 * Shell command is either the main command or the subcommand of some command.
 * For example, `git` is a command, `branch` is a subcommand of the command `git`.
 * Both `git` and `branch` can be declared using this part of the DSL.
 * Commands can have its own subcommands, options, and arguments.
 */
@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellCommandContext : ShellSuggestionContext {
  /**
   * Whether this command can't be executed without mentioning the subcommand.
   *
   * False by default.
   */
  var requiresSubcommand: Boolean

  /**
   * Allows modifying default parser options.
   * @see [ShellCommandParserOptions]
   */
  var parserOptions: ShellCommandParserOptions

  /**
   * Specify the subcommands of the current command.
   *
   * @param [content] is suspending function that will be executed at the moment of requesting the subcommands.
   * Inside [content] you can access the values of [ShellRuntimeContext] and generate the list of subcommands depending on
   * current shell directory, typed prefix, project and so on.
   */
  fun subcommands(content: suspend ShellChildCommandsContext.(ShellRuntimeContext) -> Unit)

  /**
   * Allows specifying options that depend on the shell state. For example, on the command version.
   *
   * Use ordinary [option] if you need to define the option that doesn't depend on the shell state.
   *
   * @param [content] is suspending function that will be executed at the moment of requesting the options.
   * Inside [content] you can access the values of [ShellRuntimeContext] and generate the list of options depending on
   * current shell directory, typed prefix, project and so on.
   */
  fun dynamicOptions(content: suspend ShellChildOptionsContext.(ShellRuntimeContext) -> Unit)

  /**
   * Specifies that this option can be used in the current command not depending on the shell state.
   *
   * Use [dynamicOptions] if your option can be used only in some particular shell state.
   *
   * @param names the names of the option (for example, short and long form: `-o` and `--option`)
   * @param content description of the option
   */
  fun option(vararg names: String, content: ShellOptionContext.() -> Unit = {})

  /**
   * Specifies that this Shell command should have an argument.
   * Note that arguments are not optional by default.
   * If your argument is not necessary to be specified, then set [ShellArgumentContext.isOptional] to true.
   * Arguments should be defined in the same order as it is expected in the command line.
   * @param [content] description of the argument
   */
  fun argument(content: ShellArgumentContext.() -> Unit = {})
}
