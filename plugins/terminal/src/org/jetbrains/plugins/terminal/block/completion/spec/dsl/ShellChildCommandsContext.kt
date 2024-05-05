// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellChildCommandsContext {
  fun subcommand(vararg names: String, content: ShellCommandContext.() -> Unit)
}

/**
 * @param [parentNames] used to build cache key/debug name of the subcommand/option/argument generators
 */
internal class ShellChildCommandsContextImpl(private val parentNames: List<String>) : ShellChildCommandsContext {
  private val commands: MutableList<ShellCommandSpec> = mutableListOf()

  override fun subcommand(vararg names: String, content: ShellCommandContext.() -> Unit) {
    val context = ShellCommandContextImpl(names.toList(), parentNames)
    content.invoke(context)
    commands.add(context.build())
  }

  fun build(): List<ShellCommandSpec> = commands
}
