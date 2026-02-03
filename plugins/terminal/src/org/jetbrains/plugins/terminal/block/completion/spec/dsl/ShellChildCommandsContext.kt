// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import org.jetbrains.annotations.ApiStatus

/**
 * DSL for declaring subcommands of the Shell command.
 */
@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellChildCommandsContext {
  /**
   * Specifies that shell command can have the following subcommand.
   *
   * @param names the names of the subcommand (for example, "commit" and "push")
   * Name is used to filter the completion popup to show only relevant items.
   * Also, it is inserted when chosen from the popup, if [ShellSuggestionContext.insertValue] is not specified.
   * Also, it is shown in the completion popup, if [ShellCompletionSuggestion.displayName] is not specified.
   * @param content subcommand description
   */
  fun subcommand(vararg names: String, content: ShellCommandContext.() -> Unit = {})
}

/**
 * @param [parentNames] used to build cache key/debug name of the subcommand/option/argument generators
 */
internal class ShellChildCommandsContextImpl(private val parentNames: List<String>) : ShellChildCommandsContext {
  private val commands: MutableList<ShellCommandSpec> = mutableListOf()

  override fun subcommand(vararg names: String, content: ShellCommandContext.() -> Unit) {
    val context = ShellCommandContextImpl(names.toList(), parentNames)
    content.invoke(context)
    commands.addAll(context.build())
  }

  fun build(): List<ShellCommandSpec> = commands
}
