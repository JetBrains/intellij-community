package com.intellij.debugger.streams.core.trace

import kotlinx.coroutines.CoroutineScope

/**
 * An abstract way to schedule computation in the debugger context.
 *
 * Some parts of the stream debugger (eg. UI) needs to access debugger state
 * which often should be performed in the debugger's context to ensure consistency.
 *
 * Implementations (eg. Java, .NET) execute operations within the [com.intellij.xdebugger.frame.XSuspendContext]
 * and cancel computations once the debuggee is resumed.
 */
interface DebuggerCommandLauncher {
  fun executeDebuggerCommand(command: suspend CoroutineScope.() -> Unit)

  suspend fun <T> computeInDebuggerContext(command: suspend CoroutineScope.() -> T): T
}