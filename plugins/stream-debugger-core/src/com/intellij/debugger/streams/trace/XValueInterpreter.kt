// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace

import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XValue
import org.jetbrains.annotations.Nls

interface XValueInterpreter {
  data class Result(val arrayReference: ArrayReference, val hasInnerExceptions: Boolean, val evaluationContext: EvaluationContextWrapper)

  fun tryExtractResult(session: XDebugSession, result: XValue): Result?
  fun tryExtractError(result: XValue): @Nls String?
}