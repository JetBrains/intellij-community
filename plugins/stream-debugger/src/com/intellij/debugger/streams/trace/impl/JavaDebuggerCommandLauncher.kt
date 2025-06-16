// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.executeOnDMT
import com.intellij.debugger.streams.core.trace.DebuggerCommandLauncher
import com.intellij.xdebugger.XDebugSession
import kotlinx.coroutines.CoroutineScope

class JavaDebuggerCommandLauncher(val session: XDebugSession) : DebuggerCommandLauncher {
  override fun launchDebuggerCommand(command: suspend CoroutineScope.() -> Unit) {
    // runs in debugger manager thread only while SuspendContext is not resumed
    val debuggerContext = (session.debugProcess as JavaDebugProcess).debuggerSession.contextManager.context
    executeOnDMT(debuggerContext, block = command)
  }
}