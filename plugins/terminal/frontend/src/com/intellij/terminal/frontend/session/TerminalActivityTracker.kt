package com.intellij.terminal.frontend.session

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalActivityTracker {
  /**
   * Should be called every time when any user activity is performed in the terminal backend.
   * For example, typing.
   */
  fun registerActivity()

  companion object {
    internal val EP_NAME =
      ExtensionPointName.create<TerminalActivityTracker>("org.jetbrains.plugins.terminal.activityTracker")
  }
}
