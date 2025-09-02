// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.trace

import com.intellij.debugger.streams.core.wrapper.StreamChain
import org.jetbrains.annotations.Nls

/**
 * @author Vitaliy.Bibaev
 */
interface StreamTracer {
  sealed class Result {
    data class Evaluated(val result: TracingResult, val evaluationContext: GenericEvaluationContext) : Result()
    data class EvaluationFailed(val traceExpression: String, val message: @Nls String) : Result()
    data class CompilationFailed(val traceExpression: String, val message: @Nls String) : Result()
    object Unknown : Result()
  }
  suspend fun trace(chain: StreamChain) : Result
}
