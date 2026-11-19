// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun interface TerminalSelectAllProvider {
  fun selectAll()

  companion object {
    @JvmField val DATA_KEY: DataKey<TerminalSelectAllProvider> = DataKey.create("TerminalSelectAllProvider")
  }
}
