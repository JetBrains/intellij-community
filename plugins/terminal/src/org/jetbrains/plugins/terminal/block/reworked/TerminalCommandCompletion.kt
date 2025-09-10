// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.idea.AppModeAssertions
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
object TerminalCommandCompletion {
  private const val REGISTRY_KEY = "terminal.new.ui.completion.popup"

  fun isEnabled(): Boolean {
    return Registry.`is`(REGISTRY_KEY, false)
           && AppModeAssertions.isMonolith()  // Disable in RemDev at the moment because it is not supported yet
           && OS.CURRENT != OS.Windows        // Disable on Windows for now as it requires additional support
  }

  @TestOnly
  fun enableForTests(parentDisposable: Disposable) {
    Registry.get(REGISTRY_KEY).setValue(true, parentDisposable)
  }
}