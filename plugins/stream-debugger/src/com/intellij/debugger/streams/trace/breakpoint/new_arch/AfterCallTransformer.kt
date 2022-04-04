// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.new_arch

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.sun.jdi.ObjectReference

/**
 * @author Shumaf Lovpache
 * Hook interface for stream chain modification
 */
interface AfterCallTransformer {
  /**
   * Fires before method representing chain operator returns
   * [evaluationContextImpl] evaluation context for current breakpoint hit
   * [chainInstance] stream chain instance as operator result
   * @return transformed chain
   */
  fun afterCall(evaluationContextImpl: EvaluationContextImpl, chainInstance: ObjectReference): ObjectReference
}