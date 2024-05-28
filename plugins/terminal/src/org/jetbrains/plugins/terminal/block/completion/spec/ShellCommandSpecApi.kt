// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.terminal.completion.spec.ShellCommandSpec
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellSuggestionType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellCommandContext
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellCommandContextImpl
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellCommandSpecDsl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCompletionSuggestionImpl
import javax.swing.Icon

/**
 * The single true way of creating [ShellCommandSpec].
 * @param name name of the shell command
 * @param content description of subcommands, options and arguments using DSL
 */
@ApiStatus.Experimental
@ShellCommandSpecDsl
fun ShellCommandSpec(name: String, content: ShellCommandContext.() -> Unit = {}): ShellCommandSpec {
  val context = ShellCommandContextImpl(listOf(name))
  content.invoke(context)
  return context.build().first()
}

/**
 * Creates [ShellCompletionSuggestion] with the following parameters:
 * @param name the string to be shown in the completion popup and inserted on completion
 * @param type used for now only to automatically configure the icon
 * @param displayName the string to be shown in the completion popup instead of [name] if specified
 * @param description text to be shown in the documentation popup
 * @param insertValue the string to be inserted on completion instead of [name] if specified.
 * Supports specifying caret position after completion item insertion in a form `some{caret}item`.
 * In this example `someitem` text will be inserted and caret is placed between `some` and `item`.
 * @param priority int from 0 to 100 with default 50.
 * Allows specifying the order of the items in the completion popup.
 * The greater the number, the closer the item will be to the first place.
 * @param icon used to provide custom icon instead of autodetected from [type]
 * @param prefixReplacementIndex see [ShellCompletionSuggestion.prefixReplacementIndex].
 */
@ApiStatus.Experimental
fun ShellCompletionSuggestion(
  name: String,
  type: ShellSuggestionType = ShellSuggestionType.ARGUMENT,
  displayName: String? = null,
  description: @Nls String? = null,
  insertValue: String? = null,
  priority: Int = 50,
  icon: Icon? = null,
  prefixReplacementIndex: Int = 0,
): ShellCompletionSuggestion {
  return ShellCompletionSuggestionImpl(name, type, displayName, description, insertValue, priority, icon, prefixReplacementIndex)
}
