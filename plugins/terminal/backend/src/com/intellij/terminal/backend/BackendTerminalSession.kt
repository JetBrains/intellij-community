package com.intellij.terminal.backend

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.terminal.session.TerminalSession
import kotlinx.coroutines.CoroutineScope

internal interface BackendTerminalSession : TerminalSession {
  /**
   * Scope in which all session-related activities are executed.
   * The lifecycle of the session is bound to it.
   * If it cancels, then the shell process will be terminated.
   * And if the process is terminated on its own, then the scope will be canceled as well.
   */
  val coroutineScope: CoroutineScope

  companion object {
    val LOG: Logger = logger<BackendTerminalSession>()
  }
}