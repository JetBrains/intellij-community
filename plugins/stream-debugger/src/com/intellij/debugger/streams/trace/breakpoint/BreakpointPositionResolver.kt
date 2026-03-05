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
   * @param skipCount how many `MethodExitRequest` hits to ignore before treating one as the real qualifier exit.
   *
   * Needed when multiple chains in the same method share the same qualifier method - for example,
   * ```java
   * list.stream().filter(...).collect(toList())    // chain 1: skipCount = 0
   *     .stream().map(...).collect(toList())       // chain 2: skipCount = 1
   *     .stream().sorted().collect(toList())       // chain 3: skipCount = 2
   *     .stream().distinct().collect(toList());    // chain 4: skipCount = 3
   * ```
   * Here if we want to compute the third stream, we need to skip all previous calls of `Collection.stream()`.
   */
  data class Found(
    val skipCount: Int = 0,
    val qualifierExpressionMethod: JvmMethodSignature?,
    val intermediateStepsMethods: List<JvmMethodSignature>,
    val terminationOperationMethod: JvmMethodSignature,
  ) : BreakpointResolveResult()
}