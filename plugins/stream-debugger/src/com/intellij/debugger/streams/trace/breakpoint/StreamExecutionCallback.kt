// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.interceptor.StreamTraceValues

/**
 * @author Shumaf Lovpache
 */
interface StreamExecutionCallback {
  fun evaluated(collectedValues: StreamTraceValues, context: EvaluationContextImpl)

  fun breakpointSetupFailed(e: Throwable)

  fun tracingSetupFailed(e: Throwable)

  fun streamExecutionFailed(e: Throwable)
}