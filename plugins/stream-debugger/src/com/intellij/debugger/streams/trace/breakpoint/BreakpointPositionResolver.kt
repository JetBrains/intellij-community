// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.streams.core.wrapper.StreamChain

interface BreakpointPositionResolver {
  /**
   * Searches breakpoint places for [StreamChain] operations.
   * If any intermediate or terminal call cannot be resolved should return [BreakpointResolveResult.NotFound]
   */
  suspend fun findBreakpointPositions(chain: StreamChain): BreakpointResolveResult
}

sealed class BreakpointResolveResult {
  object NotFound : BreakpointResolveResult()
  /**
   * @param qualifierExpressionMethod null if a qualified expression is not a function call
   */
  data class Found(
    val qualifierExpressionMethod: JvmMethodSignature?,
    val intermediateStepsMethods: List<JvmMethodSignature>,
    val terminationOperationMethod: JvmMethodSignature,
  ) : BreakpointResolveResult()
}