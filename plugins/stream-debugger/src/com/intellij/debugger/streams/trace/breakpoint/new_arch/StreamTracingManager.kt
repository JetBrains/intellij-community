// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.new_arch

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.wrapper.StreamChain
import com.sun.jdi.Value

typealias ChainEvaluationCallback = (EvaluationContextImpl, Value?) -> Unit

/**
 * @author Shumaf Lovpache
 * Performs async runtime chain modification using JDI.
 * Note that this is a stateful object, so it's scope is the current tracing stream chain.
 */
interface StreamTracingManager {
  fun evaluateChain(evaluationContextImpl: EvaluationContextImpl, chain: StreamChain, callback: (EvaluationContextImpl, Value?) -> Unit)
}