// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.completion.spec.ShellAliasSuggestion
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellSuggestionType
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellAliasSuggestionImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCompletionSuggestionImpl
import javax.swing.Icon

internal class ShellCompletionSuggestionContextImpl(
  private val name: String,
) : ShellSuggestionContextBase(listOf(name)), ShellCompletionSuggestionContext {
  private var type: ShellSuggestionType = ShellSuggestionType.ARGUMENT
  private var icon: Icon? = null
  private var prefixReplacementIndex: Int = 0
  private var isHidden: Boolean = false
  private var shouldEscape: Boolean = true

  override fun type(type: ShellSuggestionType) {
    this.type = type
  }

  override fun icon(icon: Icon) {
    this.icon = icon
  }

  override fun prefixReplacementIndex(index: Int) {
    this.prefixReplacementIndex = index
  }

  override fun hidden() {
    isHidden = true
  }

  override fun noEscaping() {
    shouldEscape = false
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
      shouldEscape = shouldEscape,
    )
  }

  fun buildAlias(aliasValue: String): ShellAliasSuggestion {
    return ShellAliasSuggestionImpl(
      name = name,
      aliasValue = aliasValue,
      type = type,
      displayName = displayName,
      description = descriptionSupplier?.get(),
      insertValue = insertValue,
      priority = priority,
      icon = icon,
      prefixReplacementIndex = prefixReplacementIndex,
      isHidden = isHidden,
      shouldEscape = shouldEscape,
    )
  }
}