// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.executeOnDMT
import com.intellij.debugger.streams.core.trace.DebuggerCommandLauncher
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

class JavaDebuggerCommandLauncher(val context: EvaluationContextImpl) : DebuggerCommandLauncher {
  override val project: Project
    get() = context.project

  override fun launchDebuggerCommand(command: suspend CoroutineScope.() -> Unit) {
    // runs in debugger manager thread only while SuspendContext is not resumed
    executeOnDMT(context.suspendContext, block = command)
  }
}