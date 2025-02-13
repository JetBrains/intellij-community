package com.intellij.debugger.streams.core.trace

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

interface DebuggerCommandLauncher {
  val project : Project
  fun launchDebuggerCommand(command: suspend CoroutineScope.() -> Unit)
}