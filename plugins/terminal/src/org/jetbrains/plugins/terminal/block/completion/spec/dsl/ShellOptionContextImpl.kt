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
  private var isPersistent: Boolean = false
  private var isRequired: Boolean = false
  private var separator: String? = null
  private var repeatTimes: Int = 1
  private var exclusiveOn: List<String> = emptyList()
  private var dependsOn: List<String> = emptyList()

  private val arguments: MutableList<ShellArgumentSpec> = mutableListOf()

  override fun persistent() {
    isPersistent = true
  }

  override fun required() {
    isRequired = true
  }

  override fun separator(separator: String) {
    this.separator = separator
  }

  override fun repeatTimes(times: Int) {
    repeatTimes = times
  }

  override fun exclusiveOn(on: List<String>) {
    exclusiveOn = on
  }

  override fun dependsOn(on: List<String>) {
    dependsOn = on
  }

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