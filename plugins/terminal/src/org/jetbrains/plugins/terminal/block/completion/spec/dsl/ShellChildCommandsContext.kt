// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellChildCommandsContext {
  fun subcommand(vararg names: String, content: ShellCommandContext.() -> Unit)
}

internal class ShellChildCommandsContextImpl : ShellChildCommandsContext {
  private val commands: MutableList<ShellCommandSpec> = mutableListOf()

  override fun subcommand(vararg names: String, content: ShellCommandContext.() -> Unit) {
    val context = ShellCommandContextImpl(names.toList())
    content.invoke(context)
    commands.add(context.build())
  }

  fun build(): List<ShellCommandSpec> = commands
}
