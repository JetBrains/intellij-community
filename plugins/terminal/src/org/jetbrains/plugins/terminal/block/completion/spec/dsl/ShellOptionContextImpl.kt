// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.completion.spec.ShellArgumentSpec
import com.intellij.terminal.completion.spec.ShellOptionSpec
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellOptionSpecImpl

/**
 * @param [parentCommandNames] used to build cache key/debug name of the argument's generators
 */
internal class ShellOptionContextImpl(
  names: List<String>,
  private val parentCommandNames: List<String>
) : ShellSuggestionContextBase(names), ShellOptionContext {
  override var isPersistent: Boolean = false
  override var isRequired: Boolean = false
  override var separator: String? = null
  override var repeatTimes: Int = 1
  override var exclusiveOn: List<String> = emptyList()
  override var dependsOn: List<String> = emptyList()

  private val arguments: MutableList<ShellArgumentSpec> = mutableListOf()

  override fun argument(content: ShellArgumentContext.() -> Unit) {
    val context = ShellArgumentContextImpl(parentCommandNames + names.first(), argNumber = arguments.size + 1)
    content.invoke(context)
    arguments.add(context.build())
  }

  fun build(): List<ShellOptionSpec> {
    return names.map { name ->
      ShellOptionSpecImpl(
        name = name,
        displayName = displayName,
        descriptionSupplier = descriptionSupplier,
        insertValue = insertValue,
        priority = priority,
        isRequired = isRequired,
        isPersistent = isPersistent,
        separator = separator,
        repeatTimes = repeatTimes,
        exclusiveOn = exclusiveOn,
        dependsOn = dependsOn,
        arguments = arguments
      )
    }
  }
}