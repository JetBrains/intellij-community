// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.EvaluationContextWrapper
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class JavaEvaluationContext(val context: EvaluationContextImpl) : EvaluationContextWrapper {
  override val project: Project
    get() = context.project

  override fun launchDebuggerCommand(command: suspend CoroutineScope.() -> Unit) {
    context.managerThread.coroutineScope.launch {
      command()
    }
  }
}