// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.ui.ComponentContainer
import com.jediterm.core.util.TermSize

interface TerminalContentController : ComponentContainer {
  fun getTerminalSize(): TermSize?

  fun isFocused(): Boolean
}