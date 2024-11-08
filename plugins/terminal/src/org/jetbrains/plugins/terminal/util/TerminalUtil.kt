// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.model.TerminalModelListener
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.withTimeout
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import java.time.Duration

internal fun TerminalTextBuffer.addModelListener(parentDisposable: Disposable, listener: TerminalModelListener) {
  addModelListener(listener)
  Disposer.register(parentDisposable) {
    removeModelListener(listener)
  }
}

internal fun TtyConnector.waitFor(timeout: Duration, callback: () -> Unit) {
  if (!this.isConnected) {
    callback()
    return
  }
  val processTtyConnector = ShellTerminalWidget.getProcessTtyConnector(this)
  if (processTtyConnector != null) {
    val onExit = processTtyConnector.process.onExit()
    service<InternalTerminalCoroutineService>().coroutineScope.launch(Dispatchers.IO) {
      try {
        withTimeout(timeout) {
          onExit.await() // the future will be canceled on timeout (not affecting the process itself)
        }
      }
      finally {
        callback()
      }
    }
  }
  else {
    val connector = this
    service<InternalTerminalCoroutineService>().coroutineScope.launch(Dispatchers.IO) {
      try {
        withTimeout(timeout) {
          connector.waitFor()
        }
      }
      finally {
        callback()
      }
    }
  }
}

@Service(Service.Level.APP)
private class InternalTerminalCoroutineService(val coroutineScope: CoroutineScope)

@JvmField
internal val STOP_EMULATOR_TIMEOUT: Duration = Duration.ofMillis(1500)
