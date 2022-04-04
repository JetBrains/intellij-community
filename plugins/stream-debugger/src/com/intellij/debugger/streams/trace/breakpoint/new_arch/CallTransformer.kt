// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.new_arch

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.sun.jdi.Value

/**
 * @author Shumaf Lovpache
 * Hook interface for stream chain modification
 */
interface CallTransformer {
  /**
   * Fires immediately after method representing chain operator was called
   * This hook can be used for ex. to change predicate in filter operator
   * [evaluationContextImpl] evaluation context for current breakpoint hit
   * [arguments] list of arguments passed to operator
   * @return transformed arguments list
   */
  fun transformArguments(evaluationContextImpl: EvaluationContextImpl, arguments: List<Value?>): List<Value?>
}