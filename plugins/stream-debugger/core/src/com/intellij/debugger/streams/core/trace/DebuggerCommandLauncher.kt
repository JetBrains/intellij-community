package com.intellij.debugger.streams.core.trace

import kotlinx.coroutines.CoroutineScope

interface DebuggerCommandLauncher {
  fun launchDebuggerCommand(command: suspend CoroutineScope.() -> Unit)

  suspend fun <T> computeInDebuggerContext(command: suspend CoroutineScope.() -> T): T
}