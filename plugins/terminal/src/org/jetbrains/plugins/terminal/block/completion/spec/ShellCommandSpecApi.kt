// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.terminal.completion.spec.ShellAliasSuggestion
import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.*

/**
 * The single true way of creating [ShellCommandSpec].
 * @param name name of the shell command
 * @param content description of subcommands, options, and arguments using DSL
 */
@ApiStatus.Experimental
@ShellCommandSpecDsl
fun ShellCommandSpec(name: String, content: ShellCommandContext.() -> Unit = {}): ShellCommandSpec {
  val context = ShellCommandContextImpl(listOf(name))
  content.invoke(context)
  return context.build().first()
}

/**
 * Creates [com.intellij.terminal.completion.spec.ShellCompletionSuggestion] instance.
 *
 * @param name is used to filter the completion popup to show only relevant items.
 * Also, it is inserted when chosen from the popup, if [ShellCompletionSuggestion.insertValue] is not specified.
 * Also, it is shown in the completion popup, if [ShellCompletionSuggestion.displayName] is not specified.
 * @param description text to be shown in the documentation popup
 */
@ApiStatus.Experimental
fun ShellCompletionSuggestion(
  name: String,
  description: @Nls String,
): ShellCompletionSuggestion {
  return ShellCompletionSuggestion(name) { description(description) }
}

/**
 * Creates [com.intellij.terminal.completion.spec.ShellCompletionSuggestion] instance.
 *
 * @param name is used to filter the completion popup to show only relevant items.
 * Also, it is inserted when chosen from the popup, if [ShellCompletionSuggestion.insertValue] is not specified.
 * Also, it is shown in the completion popup, if [ShellCompletionSuggestion.displayName] is not specified.
 * @param content the builder function where you can configure other parameters.
 * It is called immediately during this function call.
 */
@ApiStatus.Experimental
fun ShellCompletionSuggestion(
  name: String,
  content: ShellCompletionSuggestionContext.() -> Unit = {},
): ShellCompletionSuggestion {
  val context = ShellCompletionSuggestionContextImpl(name)
  content.invoke(context)
  return context.build()
}

/**
 * Creates a [com.intellij.terminal.completion.spec.ShellAliasSuggestion] with the following parameters.
 *
 * @param name is used to filter the completion popup to show only relevant items.
 * Also, it is inserted when chosen from the popup, if [ShellCompletionSuggestion.insertValue] is not specified.
 * Also, it is shown in the completion popup, if [ShellCompletionSuggestion.displayName] is not specified.
 * @param aliasValue the expanded form of the alias used to generate suggestions
 */
@ApiStatus.Experimental
fun ShellAliasSuggestion(
  name: String,
  aliasValue: String,
  content: ShellCompletionSuggestionContext.() -> Unit = {},
): ShellAliasSuggestion {
  val context = ShellCompletionSuggestionContextImpl(name)
  content.invoke(context)
  return context.buildAlias(aliasValue)
}
