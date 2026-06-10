// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.DebugProcessAdapterImpl
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.memory.utils.InstanceJavaValue
import com.intellij.debugger.streams.core.StreamDebuggerBundle
import com.intellij.debugger.streams.core.trace.StreamTracer
import com.intellij.debugger.streams.core.trace.TraceResultInterpreter
import com.intellij.debugger.streams.core.trace.XValueInterpreter
import com.intellij.debugger.streams.core.trace.interpretStreamResult
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.lib.impl.BreakpointBasedLibrarySupport
import com.intellij.debugger.streams.ui.impl.PrimitiveValueDescriptor
import com.intellij.xdebugger.frame.XValue
import com.sun.jdi.Value

/**
 * StreamTracer implementation that uses breakpoints for value interception.
 * 
 * This is the main entry point for breakpoint-based stream debugging.
 * Uses JDI breakpoints instead of peek-based injection to avoid double side effects.
 */
internal class BreakpointBasedStreamTracer(
  private val xDebugProcess: JavaDebugProcess,
  private val librarySupport: BreakpointBasedLibrarySupport,
  private val xValueInterpreter: XValueInterpreter,
  private val resultInterpreter: TraceResultInterpreter,
) : StreamTracer {
  override suspend fun trace(chain: StreamChain): StreamTracer.Result {
    val breakpointPositionResolver = JavaBreakpointPositionResolver()
    val positions = breakpointPositionResolver
      .findBreakpointPositions(chain) as? BreakpointResolveResult.Found
                    ?: return StreamTracer.Result.EvaluationFailed("", StreamDebuggerBundle.message("could.not.find.breakpoint.positions"))

    // Create ObjectStorage for protecting traced objects from GC
    val debugProcess = xDebugProcess.debuggerSession.process
    val objectStorage = ObjectStorage()
    val breakpointFactory = JdiBreakpointFactory()
    val manager = StreamTracingManager(
      breakpointFactory,
      objectStorage,
      librarySupport.createRuntimeHandlerFactory(objectStorage),
      debugProcess,
    )

    return try {
      val result = manager.evaluateChain(
        xDebugProcess.debuggerSession.contextManager.context,
        positions,
        chain
      )
      when (result) {
        is TracingResult.Success -> {
          val xValue = createXValue(result.evaluationContext, result.rawTrace)
          interpretStreamResult(xDebugProcess.session, xValueInterpreter, resultInterpreter, xValue, chain)
        }
        is TracingResult.TargetVmException -> {
          val xValue = createXValue(result.evaluationContext, result.exception)
          interpretStreamResult(xDebugProcess.session, xValueInterpreter, resultInterpreter, xValue, chain)
        }
        is TracingResult.Error -> StreamTracer.Result.EvaluationFailed("", result.errorMessage)
      }
    } finally {
      // Registering the resume listener is thread-safe and non-suspending, so it runs even on cancellation.
      scheduleReleaseOnResume(objectStorage, debugProcess)
    }
  }

  /**
   * Traced objects should be protected from GC until the debuggee is resumed.
   * We can't do this earlier because during rendering of the result `XValue`-s
   * perform additional method invocations.
   *
   * To schedule release on resume, we add a single-shot listener to the debug process.
   *
   * `resumed` runs on the debugger manager thread, so it is a safe place to release objects
   */
  private fun scheduleReleaseOnResume(objectStorage: ObjectStorage, debugProcess: DebugProcessImpl) {
    debugProcess.addDebugProcessListener(object : DebugProcessAdapterImpl() {
      override fun resumed(suspendContext: SuspendContextImpl) {
        debugProcess.removeDebugProcessListener(this)
        objectStorage.releaseAll()
      }
    })
  }

  private suspend fun createXValue(
    evaluationContext: EvaluationContextImpl,
    jvmValue: Value,
  ): XValue {
    return withDebugContext(evaluationContext.managerThread) {
      val nodeManager = xDebugProcess.nodeManager
      val valueDescriptor = PrimitiveValueDescriptor(xDebugProcess.session.project, jvmValue)
      InstanceJavaValue(valueDescriptor, evaluationContext, nodeManager)
    }
  }
}
