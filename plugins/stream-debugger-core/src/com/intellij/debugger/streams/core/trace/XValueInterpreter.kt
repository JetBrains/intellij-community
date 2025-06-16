// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.trace

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XValue
import org.jetbrains.annotations.Nls

interface GenericEvaluationContext {
  val project: Project
}

interface XValueInterpreter {
  sealed class Result {
    data class Array(val arrayReference: ArrayReference, val hasInnerExceptions: Boolean, val evaluationContext: GenericEvaluationContext) : Result()
    data class Error(@Nls val message: String) : Result()
    object Unknown : Result()
  }

  suspend fun extract(session: XDebugSession, result: XValue): Result
}