// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.terminal.block.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.block.completion.spec.ShellSuggestionType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellCompletionSuggestionImpl
import java.util.function.Supplier

@ApiStatus.Experimental
fun ShellCompletionSuggestion(
  name: String,
  type: ShellSuggestionType = ShellSuggestionType.ARGUMENT,
  displayName: String? = null,
  description: Supplier<@Nls String>? = null,
  insertValue: String? = null,
  priority: Int = 50
): ShellCompletionSuggestion {
  return ShellCompletionSuggestionImpl(listOf(name), type, displayName, description, insertValue, priority)
}