package com.intellij.debugger.streams.core.trace

import kotlinx.coroutines.CoroutineScope

interface DebuggerCommandLauncher {
  fun launchDebuggerCommand(command: suspend CoroutineScope.() -> Unit)
}