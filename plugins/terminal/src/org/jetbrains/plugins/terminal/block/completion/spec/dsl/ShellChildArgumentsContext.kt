// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.block.completion.spec.ShellArgumentSpec
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellChildArgumentsContext {
  fun argument(content: ShellArgumentContext.() -> Unit)
}

/**
 * @param [parentCommandNames] used to build cache key/debug name of the argument's generators
 */
internal class ShellChildArgumentsContextImpl(private val parentCommandNames: List<String>) : ShellChildArgumentsContext {
  private val arguments: MutableList<ShellArgumentSpec> = mutableListOf()

  override fun argument(content: ShellArgumentContext.() -> Unit) {
    val context = ShellArgumentContextImpl(parentCommandNames)
    content.invoke(context)
    arguments.add(context.build())
  }

  fun build(): List<ShellArgumentSpec> = arguments
}