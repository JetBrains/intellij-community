// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import org.jetbrains.annotations.Nls
import java.util.function.Supplier

internal abstract class ShellSuggestionContextBase(
  final override val names: List<String>,
) : ShellSuggestionContext {
  override var displayName: String? = null
  override var description: Supplier<@Nls String>? = null
  override var insertValue: String? = null
  override var priority: Int = 50
}