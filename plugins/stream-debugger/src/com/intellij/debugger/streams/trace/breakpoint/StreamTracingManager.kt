// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.streams.core.StreamDebuggerBundle
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.BreakpointBasedHandlerFactory
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.StreamInstrumentationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.sun.jdi.InvocationException
import com.sun.jdi.Value
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.MethodEntryRequest
import com.sun.jdi.request.MethodExitRequest
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.annotations.Nls

private val LOG = logger<StreamTracingManager>()

sealed class EvaluationResult {
  data class Success(val evaluationContext: EvaluationContextImpl, val rawTrace: Value) : EvaluationResult()
  data class Error(val errorMessage: @Nls String) : EvaluationResult()
}

internal class StreamTracingManager(
  private val breakpointFactory: JdiBreakpointFactory,
  private val objectStorage: DisableCollectionObjectStorage,
  private val handlerFactory: BreakpointBasedHandlerFactory,
) {
  private var sourceOperationBreakpoint: MethodExitRequest? = null
  private var intermediateOperationsBreakpoints: List<StreamCallRuntimeInfo> = emptyList()
  private lateinit var terminalOperationBreakpoint: StreamCallRuntimeInfo

  private lateinit var instrumentationManager: StreamInstrumentationManager

  private val evaluationFinished = CompletableDeferred<EvaluationContextImpl>()

  suspend fun evaluateChain(debuggerContext: DebuggerContextImpl, breakpointPositions: BreakpointResolveResult.Found, chain: StreamChain): EvaluationResult {
    val debugProcess = debuggerContext.debugProcess!!

    withDebugContext(debuggerContext.managerThread!!) {
      val evaluationContextImpl = debuggerContext.createEvaluationContext()
                                  ?: return@withDebugContext EvaluationResult.Error(StreamDebuggerBundle.message("program.is.not.suspended"))

      withDebugContext(evaluationContextImpl.suspendContext) {
        instrumentationManager = StreamInstrumentationManager.create(handlerFactory, objectStorage, chain, evaluationContextImpl)
        val firstRequestor = createRequestors(evaluationContextImpl, chain, breakpointPositions)
        firstRequestor.enable()
      }

      debugProcess.suspendManager.resume(evaluationContextImpl.suspendContext)
    }

    // Evaluation is finished when the execution returns from the terminal operation
    val evalCtx = evaluationFinished.await()

    // Collect results from instrumentation
    val result = withDebugContext(evalCtx.suspendContext) {
      try {
        instrumentationManager.restoreQualifierVariableIfReplaced(evalCtx)
        instrumentationManager.collectResults(evalCtx)
      } catch (e: EvaluateException) {
        val cause = e.cause as? InvocationException
        if (cause != null) {
          cause.exception()
        } else {
          thisLogger().error(e)
          null
        }
      }
    }

    return if (result != null) {
      EvaluationResult.Success(evalCtx, result)
    } else {
      EvaluationResult.Error(StreamDebuggerBundle.message("program.is.not.suspended"))
    }
  }

  // returns first breakpoint request for stream chain
  private fun createRequestors(
    evaluationContext: EvaluationContextImpl,
    chain: StreamChain,
    positions: BreakpointResolveResult.Found,
  ): EventRequest {
    val qualifierExpressionBreakpoint = if (positions.qualifierExpressionMethod == null) {
      // if qualifier expression is variable we need to replace it in current stack frame
      instrumentationManager.replaceQualifierVariable(evaluationContext, chain.qualifierExpression)
      null
    }
    else {
      // if it is a method call, then we set additional breakpoint as for an intermediate operation
      sourceOperationBreakpoint = createSourceOperationRequestor(evaluationContext, positions.qualifierExpressionMethod)
      sourceOperationBreakpoint
    }

    intermediateOperationsBreakpoints = chain
      .intermediateCalls.zip(positions.intermediateStepsMethods)
      .mapIndexed { callOrder, (call, methodSignature) ->
        createIntermediateOperationRequestors(evaluationContext, callOrder, call, methodSignature)
      }

    terminalOperationBreakpoint = createTerminalOperationRequestors(
      evaluationContext,
      chain.terminationCall,
      positions.terminationOperationMethod,
    )

    return qualifierExpressionBreakpoint
           ?: intermediateOperationsBreakpoints.firstOrNull()?.methodEntryRequest
           ?: terminalOperationBreakpoint.methodEntryRequest
  }

  private fun createSourceOperationRequestor(
    evaluationContext: EvaluationContextImpl,
    methodSignature: JvmMethodSignature,
  ): MethodExitRequest {
    return breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { evalContext, _, value ->
      LOG.debug("Source operation exit request hit")
      val result = instrumentationManager.onSourceOperationExit(evalContext, value)
      enableNextBreakpoint(-1)
      result
    }
  }

  private fun createIntermediateOperationRequestors(
    evaluationContext: EvaluationContextImpl,
    callOrder: Int,
    @Suppress("UNUSED_PARAMETER") call: IntermediateStreamCall,
    methodSignature: JvmMethodSignature,
  ): StreamCallRuntimeInfo {
    // create exit request first to be able to activate it in entry request
    val exitRequest = breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { evalContext, _, value ->
      LOG.debug("Intermediate operation ${call.name} exit request hit")
      val result = instrumentationManager.onIntermediateOperationExit(evalContext, callOrder, value)
      enableNextBreakpoint(callOrder)
      result
    }
    val entryRequest = breakpointFactory.createMethodEntryBreakpoint(evaluationContext, methodSignature) { evalContext, method, args ->
      LOG.debug("Intermediate operation ${call.name} entry request hit")
      val result = instrumentationManager.onIntermediateOperationEntry(evalContext, callOrder, method, args)
      exitRequest.enable()
      result
    }
    return StreamCallRuntimeInfo(entryRequest, exitRequest)
  }

  private fun createTerminalOperationRequestors(
    evaluationContext: EvaluationContextImpl,
    @Suppress("UNUSED_PARAMETER") call: TerminatorStreamCall,
    methodSignature: JvmMethodSignature,
  ): StreamCallRuntimeInfo {
    val exitRequest = breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { evalContext, _, value ->
      LOG.debug("Terminal operation ${call.name} exit request hit")
      instrumentationManager.onTerminalOperationExit(evalContext, value)

      // Step out of the terminal method so VM lands on the next statement in user code.
      breakpointFactory.stepOut(evalContext, evaluationFinished::complete)

      value
    }
    val entryRequest = breakpointFactory.createMethodEntryBreakpoint(evaluationContext, methodSignature) { evalContext, method, args ->
      LOG.debug("Terminal operation ${call.name} entry request hit")
      val result = instrumentationManager.onTerminalOperationEntry(evalContext, method, args)
      exitRequest.enable()
      result
    }
    return StreamCallRuntimeInfo(entryRequest, exitRequest)
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
  val methodEntryRequest: MethodEntryRequest,
  val methodExitRequest: MethodExitRequest,
)
