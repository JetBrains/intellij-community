// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.memory.utils.InstanceJavaValue
import com.intellij.debugger.streams.core.trace.StreamTracer
import com.intellij.debugger.streams.core.trace.TraceResultInterpreter
import com.intellij.debugger.streams.core.trace.XValueInterpreter
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.lib.impl.BreakpointBasedLibrarySupport
import com.intellij.debugger.streams.ui.impl.PrimitiveValueDescriptor
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
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
  private val session: XDebugSession,
  private val librarySupport: BreakpointBasedLibrarySupport,
  private val resultInterpreter: TraceResultInterpreter,
  private val xValueInterpreter: XValueInterpreter,
) : StreamTracer {
  override suspend fun trace(chain: StreamChain): StreamTracer.Result {
    val xDebugProcess = session.debugProcess as? JavaDebugProcess ?: return StreamTracer.Result.Unknown
    val suspendContext = xDebugProcess.session.suspendContext as? SuspendContextImpl
    val suspendManager = xDebugProcess.debuggerSession.process.suspendManager
    if (suspendContext == null) {
      LOG.error("SuspendContext is not available, probably tracer was executed when the program is not suspended")
      return StreamTracer.Result.Unknown
    }
    // TODO: maybe we need to use withAutoLoadClasses
    val evaluationContext = EvaluationContextImpl(suspendContext, suspendContext.frameProxy)

    //val stackDepthBeforeTracing = suspendContext.cachedThreadFrameCount

    val breakpointPositionResolver = JavaBreakpointPositionResolver()
    val breakpointFactory = BreakpointFactory()
    val positions =
      breakpointPositionResolver.findBreakpointPositions(chain) as? BreakpointResolveResult.Found ?: return StreamTracer.Result.Unknown

    val manager = StreamTracingManager(breakpointFactory, librarySupport.createRuntimeHandlerFactory())

    val result = manager.evaluateChain(positions, evaluationContext, chain)
    // TODO: we resumed execution above, so we must obtain a fresh suspend context

    val nodeManager = xDebugProcess.nodeManager
    val xValue = createXValue(
      session.project,
      nodeManager,
      evaluationContext,
      result,
    )

    val extractedResult = xValueInterpreter.extract(session, xValue)
    // TODO: extract shared value interpretation logic
  }

  private fun createXValue(
    project: Project,
    nodeManager: NodeManagerImpl,
    evaluationContext: EvaluationContextImpl,
    jvmValue: Value,
  ): XValue {
    val valueDescriptor = PrimitiveValueDescriptor(project, jvmValue)
    return InstanceJavaValue(valueDescriptor, evaluationContext, nodeManager)
  }
}
