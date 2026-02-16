// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.streams.core.StreamDebuggerBundle
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedHandlerFactory
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.StreamOperationHandler
import com.sun.jdi.Value
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.MethodEntryRequest
import com.sun.jdi.request.MethodExitRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.annotations.Nls

sealed class EvaluationResult {
  data class Success(val rawTrace: Value) : EvaluationResult()
  data class Error(val errorMessage: @Nls String) : EvaluationResult()
}

internal class StreamTracingManager(
  private val debuggerContext: DebuggerContextImpl,
  private val breakpointFactory: BreakpointFactory,
  private val handlerFactory: BreakpointBasedHandlerFactory,
) {
  private var sourceOperationBreakpoint: MethodExitRequest? = null
  private var intermediateOperationsBreakpoints: List<StreamCallRuntimeInfo> = emptyList()
  private lateinit var terminalOperationBreakpoint: StreamCallRuntimeInfo

  suspend fun evaluateChain(breakpointPositions: BreakpointResolveResult.Found, chain: StreamChain): EvaluationResult {
    val evaluationFinished = createEvaluationFinishedFuture()
    withDebugContext(debuggerContext.managerThread!!) {
      val evaluationContextImpl = debuggerContext.createEvaluationContext()
                                  ?: return@withDebugContext EvaluationResult.Error(StreamDebuggerBundle.message("program.is.not.suspended"))

      withDebugContext(evaluationContextImpl.suspendContext) {
        val firstRequestor = createRequestors(evaluationContextImpl, chain, breakpointPositions, evaluationFinished)
        firstRequestor.enable()
      }

      // TODO: I need to find a better solution because now we need to manually put a breakpoint on stream exit.
      evaluationContextImpl.debugProcess.suspendManager.resume(evaluationContextImpl.suspendContext)
    }

    evaluationFinished.await()
    return EvaluationResult.Success(TODO("get result from instrumentation"))
  }

  private suspend fun createEvaluationFinishedFuture(): CompletableDeferred<Unit> {
    val currentJob = currentCoroutineContext()[Job]
    val evaluationFinished = CompletableDeferred<Unit>(currentJob)
    return evaluationFinished
  }

  // returns first breakpoint request for stream chain
  private fun createRequestors(
    evaluationContext: EvaluationContextImpl,
    chain: StreamChain,
    positions: BreakpointResolveResult.Found,
    evaluationFinished: CompletableDeferred<Unit>,
  ): EventRequest {
    val qualifierExpressionBreakpoint = if (positions.qualifierExpressionMethod == null) {
      // if qualifier expression is variable we need to replace it in current stack frame

      // TODO: call instrumentation
      null
    }
    else {
      // if it is a method call, then we set additional breakpoint as for an intermediate operation
      sourceOperationBreakpoint = createSourceOperationRequestor(evaluationContext, positions.qualifierExpressionMethod/*, time*/)
      sourceOperationBreakpoint
    }

    intermediateOperationsBreakpoints = chain
      .intermediateCalls.zip(positions.intermediateStepsMethods)
      .mapIndexed { callOrder, (call, methodSignature) ->
        createIntermediateOperationRequestors(evaluationContext, /*time, */callOrder, call, methodSignature)
      }

    terminalOperationBreakpoint = createTerminalOperationRequestors(
      evaluationContext,
      chain.terminationCall,
      positions.terminationOperationMethod,
      evaluationFinished
    )

    return qualifierExpressionBreakpoint
           ?: intermediateOperationsBreakpoints.firstOrNull()?.methodEntryRequest
           ?: terminalOperationBreakpoint.methodEntryRequest
  }

  private fun createSourceOperationRequestor(
    evaluationContext: EvaluationContextImpl,
    methodSignature: JvmMethodSignature,
  ): MethodExitRequest {
    return breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { suspendContext, _, value ->
      enableNextBreakpoint(-1)
      value

      // TODO: instrumentation
    }
  }

  private fun createIntermediateOperationRequestors(
    evaluationContext: EvaluationContextImpl,
    callOrder: Int,
    call: IntermediateStreamCall,
    methodSignature: JvmMethodSignature,
  ): StreamCallRuntimeInfo {
    val handler = handlerFactory.getForIntermediate(callOrder, call)
    // create exit request first to be able to activate it in entry request
    val exitRequest = breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { suspendContext, _, value ->
      enableNextBreakpoint(callOrder)
      value

      // TODO: instrumentation
    }
    val entryRequest = breakpointFactory.createMethodEntryBreakpoint(evaluationContext, methodSignature) { suspendContext, method, args ->
      exitRequest.enable()

      // TODO: instrumentation
      args
    }
    return StreamCallRuntimeInfo(handler, entryRequest, exitRequest)
  }

  private fun createTerminalOperationRequestors(
    evaluationContext: EvaluationContextImpl,
    call: TerminatorStreamCall,
    methodSignature: JvmMethodSignature,
    evaluationFinished: CompletableDeferred<Unit>,
  ): StreamCallRuntimeInfo {
    val handler = handlerFactory.getForTermination(call)
    val exitRequest = breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { suspendContext, _, value ->
      evaluationFinished.complete(Unit)

      // TODO: step ot or run to position to exit from the stream
      value
    }
    val entryRequest = breakpointFactory.createMethodEntryBreakpoint(evaluationContext, methodSignature) { suspendContext, method, args ->
      exitRequest.enable()
      // TODO: instrumentation
      args
    }
    return StreamCallRuntimeInfo(handler, entryRequest, exitRequest)
  }

  private fun disableBreakpointRequests() {
    if (sourceOperationBreakpoint?.isEnabled == true) {
      sourceOperationBreakpoint?.disable()
    }

    for (intermediateStepBreakpoint in intermediateOperationsBreakpoints) {
      if (intermediateStepBreakpoint.methodEntryRequest.isEnabled) {
        intermediateStepBreakpoint.methodEntryRequest.disable()
      }
      if (intermediateStepBreakpoint.methodExitRequest.isEnabled) {
        intermediateStepBreakpoint.methodExitRequest.disable()
      }
    }

    if (terminalOperationBreakpoint.methodEntryRequest.isEnabled) {
      terminalOperationBreakpoint.methodEntryRequest.disable()
    }
    if (terminalOperationBreakpoint.methodExitRequest.isEnabled) {
      terminalOperationBreakpoint.methodExitRequest.disable()
    }
  }

  private fun enableNextBreakpoint(callNumber: Int) {
    if (callNumber + 1 >= intermediateOperationsBreakpoints.size) {
      terminalOperationBreakpoint.methodEntryRequest.enable()
    }
    else {
      intermediateOperationsBreakpoints[callNumber + 1].methodEntryRequest.enable()
    }
  }
}

private data class StreamCallRuntimeInfo(
  val handler: StreamOperationHandler,
  val methodEntryRequest: MethodEntryRequest,
  val methodExitRequest: MethodExitRequest,
)