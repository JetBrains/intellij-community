package com.intellij.terminal.backend

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalActivityTracker {
  /**
   * Should be called every time when any user activity is performed in the terminal backend.
   * For example, typing.
   */
  fun registerActivity()

  companion object {
    fun getInstance(): TerminalActivityTracker = service()
  }
}

/**
 * Default implementation of the [TerminalActivityTracker] that just does nothing on user activity.
 */
internal class TerminalNoOpActivityTracker : TerminalActivityTracker {
  override fun registerActivity() {}
}