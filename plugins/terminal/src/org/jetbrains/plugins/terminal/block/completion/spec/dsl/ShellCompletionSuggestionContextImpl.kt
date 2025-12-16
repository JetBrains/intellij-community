// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellSuggestionType
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCompletionSuggestionImpl
import javax.swing.Icon

internal class ShellCompletionSuggestionContextImpl(
  private val name: String,
) : ShellSuggestionContextBase(listOf(name)), ShellCompletionSuggestionContext {
  override var type: ShellSuggestionType = ShellSuggestionType.ARGUMENT
  private var icon: Icon? = null
  override var prefixReplacementIndex: Int = 0
  override var isHidden: Boolean = false

  override fun icon(icon: Icon) {
    this.icon = icon
  }

  fun build(): ShellCompletionSuggestion {
    return ShellCompletionSuggestionImpl(
      name = name,
      type = type,
      displayName = displayName,
      description = descriptionSupplier?.get(),
      insertValue = insertValue,
      priority = priority,
      icon = icon,
      prefixReplacementIndex = prefixReplacementIndex,
      isHidden = isHidden,
    )
  }
}