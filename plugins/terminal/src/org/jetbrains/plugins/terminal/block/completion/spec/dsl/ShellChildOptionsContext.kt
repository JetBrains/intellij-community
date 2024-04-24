// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.block.completion.spec.ShellOptionSpec
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellChildOptionsContext {
  fun option(vararg names: String, content: ShellOptionContext.() -> Unit)
}

internal class ShellChildOptionsContextImpl : ShellChildOptionsContext {
  private val options: MutableList<ShellOptionSpec> = mutableListOf()

  override fun option(vararg names: String, content: ShellOptionContext.() -> Unit) {
    val context = ShellOptionContextImpl(names.asList())
    content.invoke(context)
    options.add(context.build())
  }

  fun build(): List<ShellOptionSpec> = options
}