// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus

/** Reports shell activity without document offsets. State reads are thread-safe. Listener registration and delivery require the EDT. */
@ApiStatus.Internal
interface TerminalShellLifecycle {
  /** Null until shell integration starts, and after the session terminates. */
  val outputStatus: StateFlow<TerminalOutputStatus?>
  val runningCommand: String?

  @RequiresEdt
  fun addListener(parentDisposable: Disposable, listener: Listener)

  interface Listener {
    fun commandStarted(command: String) {}
    fun commandFinished(command: String?, exitCode: Int) {}
  }
}
