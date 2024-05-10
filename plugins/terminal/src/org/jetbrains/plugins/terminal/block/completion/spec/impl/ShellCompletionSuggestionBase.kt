// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

internal abstract class ShellCompletionSuggestionBase(
  final override val names: List<String>,
  private val descriptionSupplier: Supplier<@Nls String>?
) : ShellCompletionSuggestion {
  final override val description: String?
    get() = descriptionSupplier?.get()

  init {
    if (names.isEmpty()) {
      error("At least one name must be provided")
    }
  }
}