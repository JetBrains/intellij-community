// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion

import com.intellij.terminal.completion.spec.ShellArgumentSpec
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShellArgumentSuggestion(private val suggestion: ShellCompletionSuggestion, val argument: ShellArgumentSpec) : ShellCompletionSuggestion by suggestion {
  override fun toString(): String {
    return "ShellArgumentSuggestion(argument=$argument, suggestion=$suggestion)"
  }
}
