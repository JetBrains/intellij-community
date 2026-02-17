// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.memory.utils.InstanceJavaValue
import com.intellij.debugger.streams.core.StreamDebuggerBundle
import com.intellij.debugger.streams.core.trace.AbstractStreamTracer
import com.intellij.debugger.streams.core.trace.StreamTracer
import com.intellij.debugger.streams.core.trace.TraceResultInterpreter
import com.intellij.debugger.streams.core.trace.XValueInterpreter
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.lib.impl.BreakpointBasedLibrarySupport
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.StreamInstrumentationManager
import com.intellij.debugger.streams.ui.impl.PrimitiveValueDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.xdebugger.frame.XValue
import com.sun.jdi.Value

private val LOG = logger<BreakpointBasedStreamTracer>()

/**
 * StreamTracer implementation that uses breakpoints for value interception.
 * 
 * This is the main entry point for breakpoint-based stream debugging.
 * Uses JDI breakpoints instead of peek-based injection to avoid double side effects.
 */
internal class BreakpointBasedStreamTracer(
  private val xDebugProcess: JavaDebugProcess,
  private val librarySupport: BreakpointBasedLibrarySupport,
  xValueInterpreter: XValueInterpreter,
  resultInterpreter: TraceResultInterpreter,
) : AbstractStreamTracer(xDebugProcess.session, xValueInterpreter, resultInterpreter) {
  override suspend fun trace(chain: StreamChain): StreamTracer.Result {
    val debuggerContext = xDebugProcess.debuggerSession.contextManager.context

    val breakpointPositionResolver = JavaBreakpointPositionResolver()
    val positions = breakpointPositionResolver
      .findBreakpointPositions(chain) as? BreakpointResolveResult.Found
                    ?: return StreamTracer.Result.EvaluationFailed("", StreamDebuggerBundle.message("could.not.find.breakpoint.positions"))

    val breakpointFactory = BreakpointFactory()
    val handlerFactory = librarySupport.createRuntimeHandlerFactory()
    val instrumentationManager = StreamInstrumentationManager(handlerFactory, chain)
    val manager = StreamTracingManager(debuggerContext, breakpointFactory, instrumentationManager)

    val result = manager.evaluateChain(positions, chain)
    return when (result) {
      is EvaluationResult.Error -> StreamTracer.Result.EvaluationFailed("", result.errorMessage)
      is EvaluationResult.Success -> {
        val xValue = createXValue(
          debuggerContext,
          result.rawTrace,
        ) ?: return StreamTracer.Result.EvaluationFailed("", StreamDebuggerBundle.message("program.is.not.suspended"))

        interpretStreamResult(xValue, chain, streamTraceExpression = "")
      }
    }
  }

  private suspend fun createXValue(
    debuggerContext: DebuggerContextImpl,
    jvmValue: Value,
  ): XValue? = withDebugContext(debuggerContext.managerThread!!) {
    val evaluationContext = debuggerContext.createEvaluationContext() ?: return@withDebugContext null
    val nodeManager = xDebugProcess.nodeManager
    val valueDescriptor = PrimitiveValueDescriptor(xDebugProcess.session.project, jvmValue)
    InstanceJavaValue(valueDescriptor, evaluationContext, nodeManager)
  }
}
