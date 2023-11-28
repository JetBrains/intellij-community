// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.util

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// This is a copy-pasted minimal variant of the com.intellij.platform.util.coroutines.CoroutineScopeKt.childScope.
// Since 241 it has been moved to a separate module,
// so we need to have our own version to preserve binary compatibility.

private fun requireNoJob(context: CoroutineContext) {
  require(context[Job] == null) { "Context must not specify a Job: $context" }
}

internal fun CoroutineScope.childScope(
  context: CoroutineContext = EmptyCoroutineContext,
  supervisor: Boolean = true
): CoroutineScope {
  requireNoJob(context)
  return MermaidChildScope(coroutineContext + context, supervisor)
}

/**
 * This allows to see actual coroutine context (and name!)
 * in the coroutine dump instead of "SupervisorJobImpl{Active}@598294b2".
 *
 * [See issue](https://github.com/Kotlin/kotlinx.coroutines/issues/3428)
 */
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
private class MermaidChildScope(
  context: CoroutineContext,
  private val supervisor: Boolean
): JobImpl(context[Job]), CoroutineScope {
  override fun childCancelled(cause: Throwable): Boolean {
    return !supervisor && super.childCancelled(cause)
  }

  override val coroutineContext: CoroutineContext = context + this

  override fun toString(): String {
    val coroutineName = coroutineContext[CoroutineName]?.name
    return (if (coroutineName != null) "\"$coroutineName\":" else "") +
      (if (supervisor) "supervisor:" else "") +
      super.toString()
  }
}
