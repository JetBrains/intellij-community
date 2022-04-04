// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.formatter

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.interceptor.StreamTraceValues
import com.intellij.debugger.streams.wrapper.StreamChain
import com.sun.jdi.ArrayReference

/**
 * @author Shumaf Lovpache
 */
interface StreamTraceFormatter {
  fun formatTraceResult(chain: StreamChain, collectedValues: StreamTraceValues, evaluationContext: EvaluationContextImpl): ArrayReference
}