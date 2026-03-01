// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellOptionSpec
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellChildOptionsContext {
  /**
   * @param names the names of the option (for example, short and long form: `-o` and `--option`)
   * Name is used to filter the completion popup to show only relevant items.
   * Also, it is inserted when chosen from the popup, if [ShellSuggestionContext.insertValue] is not specified.
   * Also, it is shown in the completion popup, if [ShellCompletionSuggestion.displayName] is not specified.
   */
  fun option(vararg names: String, content: ShellOptionContext.() -> Unit = {})
}

/**
 * @param [parentCommandNames] used to build cache key/debug name of the option argument's generators
 */
internal class ShellChildOptionsContextImpl(private val parentCommandNames: List<String>) : ShellChildOptionsContext {
  private val options: MutableList<ShellOptionSpec> = mutableListOf()

  override fun option(vararg names: String, content: ShellOptionContext.() -> Unit) {
    val context = ShellOptionContextImpl(names.asList(), parentCommandNames)
    content.invoke(context)
    options.addAll(context.build())
  }

  fun build(): List<ShellOptionSpec> = options
}
