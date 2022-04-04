// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointPlaceNotFoundException
import com.intellij.debugger.streams.wrapper.StreamChain

/**
 * @author Shumaf Lovpache
 */
interface BreakpointResolver {
  /**
   * Searches breakpoint places for [StreamChain] operations
   *
   * @throws BreakpointPlaceNotFoundException if the place corresponding to the stream operation is not found
   */
  fun findBreakpointPlaces(chain: StreamChain): StreamChainBreakpointPlaces
}

/**
 * Represents the result of method breakpoint positions lookup
 *
 * @param qualifierExpressionMethod method signature for qualified expression or null if qualified expression is not a function call
 * @param intermediateStepsMethods method signatures for intermediate steps
 * @param terminationOperationMethod termination operation signature
 */
data class StreamChainBreakpointPlaces(val qualifierExpressionMethod: MethodSignature?,
                                       val intermediateStepsMethods: List<MethodSignature>,
                                       val terminationOperationMethod: MethodSignature)