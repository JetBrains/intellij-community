// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@ApiStatus.Internal
class TraceContext(
  val title: @Nls String,
  val parentTraceContext: TraceContext?,
) : AbstractCoroutineContextElement(TraceContext) {
  val timestamp: Long = System.currentTimeMillis()
  val uuid: UUID = UUID.randomUUID()

  constructor(title: @Nls String, coroutineScope: CoroutineScope) : this(title, coroutineScope.coroutineContext[Key])

  override fun toString(): String = "TraceContext($title, $timestamp)\n\t-> $parentTraceContext"

  /**
   * Key for [TraceContext] instance in the coroutine context.
   */
  companion object Key : CoroutineContext.Key<TraceContext> {
    suspend operator fun invoke(title: @Nls String): TraceContext {
      val parent = currentCoroutineContext()[Key]
      return TraceContext(title, parent)
    }
  }
}

@ApiStatus.Internal
val NON_INTERACTIVE_ROOT_TRACE_CONTEXT: TraceContext = TraceContext(PyCommunityBundle.message("tracecontext.non.interactive"), null)