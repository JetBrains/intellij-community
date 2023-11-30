// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.completion

import org.jetbrains.terminal.completion.BaseSuggestion
import org.jetbrains.terminal.completion.ShellArgument
import org.jetbrains.terminal.completion.ShellSuggestion

class ShellArgumentSuggestion(suggestion: ShellSuggestion, val argument: ShellArgument) : BaseSuggestion by suggestion