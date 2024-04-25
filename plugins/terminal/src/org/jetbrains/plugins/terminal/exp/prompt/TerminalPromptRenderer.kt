// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.prompt

internal interface TerminalPromptRenderer {
  fun calculateRenderingInfo(state: TerminalPromptState): PromptRenderingInfo
}