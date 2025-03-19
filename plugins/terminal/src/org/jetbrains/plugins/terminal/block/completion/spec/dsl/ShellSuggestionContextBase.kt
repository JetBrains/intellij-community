// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import org.jetbrains.annotations.Nls
import java.util.function.Supplier

internal abstract class ShellSuggestionContextBase(
  final override val names: List<String>,
) : ShellSuggestionContext {
  override var displayName: String? = null
  override var insertValue: String? = null
  override var priority: Int = 50
    set(value) {
      if (value in 0..100) {
        field = value
      }
      else error("Priority must be between 0 and 100")
    }

  protected var descriptionSupplier: Supplier<@Nls String>? = null

  override fun description(text: String) {
    descriptionSupplier = Supplier { text }
  }

  override fun description(supplier: Supplier<String>) {
    descriptionSupplier = supplier
  }

  init {
    if (names.isEmpty()) {
      error("At least one name must be provided")
    }
  }
}