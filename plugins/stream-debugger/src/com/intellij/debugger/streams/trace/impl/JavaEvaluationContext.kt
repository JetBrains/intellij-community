// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.streams.trace.EvaluationContextWrapper
import com.intellij.openapi.project.Project

class JavaEvaluationContext(val context: EvaluationContextImpl) : EvaluationContextWrapper {
  override val project: Project
    get() = context.project

  override fun scheduleDebuggerCommand(command: Runnable) {
    context.debugProcess.managerThread.invoke(object : DebuggerCommandImpl() {
      override fun action() = command.run()
    })
  }
}