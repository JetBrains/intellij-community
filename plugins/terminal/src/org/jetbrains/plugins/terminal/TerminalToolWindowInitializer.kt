// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.wm.ToolWindow
import org.jetbrains.annotations.ApiStatus

/**
 * Allows providing custom logic for initializing the terminal tool window.
 */
@ApiStatus.Internal
interface TerminalToolWindowInitializer {
  fun initialize(toolWindow: ToolWindow)

  companion object {
    private val EP_NAME = ExtensionPointName<TerminalToolWindowInitializer>("org.jetbrains.plugins.terminal.toolWindowInitializer")

    @JvmStatic
    fun performInitialization(toolWindow: ToolWindow) {
      for (extension in EP_NAME.extensionList) {
        extension.initialize(toolWindow)
      }
    }
  }
}