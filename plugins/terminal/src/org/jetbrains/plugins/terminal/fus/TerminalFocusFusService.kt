// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.fus

import com.intellij.openapi.components.serviceOrNull
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalFocusFusService {
  companion object {
    @JvmStatic fun getInstance(): TerminalFocusFusService? = serviceOrNull()
  }

  fun ensureInitialized()
}
