// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.lib.LibrarySupport
import com.intellij.debugger.streams.core.trace.StreamTracer
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.openapi.diagnostic.logger
import com.intellij.xdebugger.XDebugSession

private val LOG = logger<BreakpointBasedStreamTracer>()

/**
 * StreamTracer implementation that uses breakpoints for value interception.
 * 
 * This is the main entry point for breakpoint-based stream debugging.
 * Uses JDI breakpoints instead of peek-based injection to avoid double side effects.
 */
class BreakpointBasedStreamTracer(
  private val librarySupport: LibrarySupport,
  private val session: XDebugSession
) : StreamTracer {
  override suspend fun trace(chain: StreamChain): StreamTracer.Result {
    val xDebugProcess = session.debugProcess as? JavaDebugProcess ?: return StreamTracer.Result.Unknown
    val suspendContext = xDebugProcess.session.suspendContext as? SuspendContextImpl
    if (suspendContext == null) {
      LOG.error("SuspendContext is not available, probably tracer was executed after when the program is not suspended")
      return StreamTracer.Result.Unknown
    }
    // MVP: Placeholder for actual tracing logic
    
    // For now, return Unknown to indicate that tracing is not yet implemented
    // This allows the default tracer (expression evaluation) to be used as fallback
    return StreamTracer.Result.Unknown
  }
}
