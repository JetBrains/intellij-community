// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import org.jetbrains.annotations.Nls
import java.util.function.Supplier

internal abstract class ShellSuggestionContextBase(
  protected val names: List<String>,
) : ShellSuggestionContext {
  protected var displayName: String? = null
  protected var insertValue: String? = null

  @Suppress("OVERRIDE_DEPRECATION")
  override var priority: Int = 50
    set(value) {
      if (value in 0..100) {
        field = value
      }
      else error("Priority must be between 0 and 100")
    }

  protected var descriptionSupplier: Supplier<@Nls String>? = null

  override fun displayName(name: String) {
    displayName = name
  }

  override fun description(text: String) {
    descriptionSupplier = Supplier { text }
  }

  override fun description(supplier: Supplier<String>) {
    descriptionSupplier = supplier
  }

  override fun insertValue(value: String) {
    this.insertValue = value
  }

  override fun priority(priority: Int) {
    this.priority = priority
  }

  init {
    if (names.isEmpty()) {
      error("At least one name must be provided")
    }
  }
}