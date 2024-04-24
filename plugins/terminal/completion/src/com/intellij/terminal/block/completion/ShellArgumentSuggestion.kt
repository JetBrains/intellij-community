// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.block.completion

import com.intellij.terminal.block.completion.spec.ShellArgumentSpec
import com.intellij.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShellArgumentSuggestion(suggestion: ShellCompletionSuggestion, val argument: ShellArgumentSpec) : ShellCompletionSuggestion by suggestion