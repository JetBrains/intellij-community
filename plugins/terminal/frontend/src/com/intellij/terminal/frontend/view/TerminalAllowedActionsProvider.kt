package com.intellij.terminal.frontend.view

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface TerminalAllowedActionsProvider {
  fun getActionIds(): List<String>

  companion object {
    internal val EP_NAME = ExtensionPointName.create<TerminalAllowedActionsProvider>("org.jetbrains.plugins.terminal.allowedActionsProvider")
  }
}