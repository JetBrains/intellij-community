// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.DebugProcessAdapterImpl
import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
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
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.MethodExitEvent
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.MethodEntryRequest
import com.sun.jdi.request.MethodExitRequest
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.annotations.Nls

private val LOG = logger<StreamTracingManager>()

internal sealed interface EvaluationStatus {
  data class EvaluationStarted(
    val resumer: DebugProcessListener,
    val instrumentation: StreamInstrumentationManager,
  ) : EvaluationStatus

  sealed interface Evaluated : EvaluationStatus
  data class Success(val evaluationContext: EvaluationContextImpl, val rawTrace: Value) : Evaluated
  data class Error(val errorMessage: @Nls String, val cause: Throwable? = null) : Evaluated
}

internal class StreamTracingManager(
  private val breakpointFactory: JdiBreakpointFactory,
  private val objectStorage: DisableCollectionObjectStorage,
  private val handlerFactory: BreakpointBasedHandlerFactory,
) {
  private var sourceOperationBreakpoint: MethodExitRequestHandle? = null
  private var intermediateOperationsBreakpoints: List<StreamCallRuntimeInfo> = emptyList()
  private lateinit var terminalOperationBreakpoint: StreamCallRuntimeInfo

  private val evaluationFinished = CompletableDeferred<EvaluationContextImpl>()

  suspend fun evaluateChain(
    debuggerContext: DebuggerContextImpl,
    breakpointPositions: BreakpointResolveResult.Found,
    chain: StreamChain,
  ): EvaluationStatus.Evaluated {
    val started = when (val setupResult = startTracingAndResume(debuggerContext, breakpointPositions, chain)) {
      is EvaluationStatus.Evaluated -> return setupResult
      is EvaluationStatus.EvaluationStarted -> setupResult
    }

    val debugProcess = debuggerContext.debugProcess!!
    val evalCtx = try {
      evaluationFinished.await()
    } finally {
      debugProcess.removeDebugProcessListener(started.resumer)
      disableBreakpointRequests()
    }

    return gatherTracingResults(started.instrumentation, evalCtx)
  }

  private suspend fun startTracingAndResume(
    debuggerContext: DebuggerContextImpl,
    breakpointPositions: BreakpointResolveResult.Found,
    chain: StreamChain,
  ): EvaluationStatus = withDebugContext(debuggerContext.managerThread!!) {
    val debugProcess = debuggerContext.debugProcess!!
    val evaluationContext = debuggerContext.createEvaluationContext()
                            ?: return@withDebugContext EvaluationStatus.Error(
                              StreamDebuggerBundle.message("program.is.not.suspended"))

    val instrumentation = withDebugContext(evaluationContext.suspendContext) {
      val mgr = StreamInstrumentationManager.create(handlerFactory, objectStorage, chain, evaluationContext)
      createRequestors(evaluationContext, chain, breakpointPositions, mgr).enable()
      mgr
    }

    val resumer = SpuriousBreakpointResumer(evaluationContext.suspendContext.thread)
    debugProcess.addDebugProcessListener(resumer)
    debugProcess.suspendManager.resume(evaluationContext.suspendContext)
    EvaluationStatus.EvaluationStarted(resumer, instrumentation)
  }

  private suspend fun gatherTracingResults(
    instrumentation: StreamInstrumentationManager,
    evalCtx: EvaluationContextImpl,
  ): EvaluationStatus.Evaluated = withDebugContext(evalCtx.suspendContext) {
    try {
      instrumentation.restoreQualifierVariableIfReplaced(evalCtx)
      EvaluationStatus.Success(evalCtx, instrumentation.collectResults(evalCtx))
    }
    catch (e: EvaluateException) {
      val cause = e.cause as? InvocationException
      if (cause != null) EvaluationStatus.Success(evalCtx, cause.exception())
      else {
        thisLogger().error(e)
        EvaluationStatus.Error(StreamDebuggerBundle.message("program.is.not.suspended"))
      }
    }
  }

  // returns first breakpoint request for stream chain
  private fun createRequestors(
    evaluationContext: EvaluationContextImpl,
    chain: StreamChain,
    positions: BreakpointResolveResult.Found,
    instrumentation: StreamInstrumentationManager,
  ): RequestHandle<*> {
    val qualifierExpressionBreakpoint = if (positions.qualifierExpressionMethod == null) {
      // if qualifier expression is variable we need to replace it in current stack frame
      instrumentation.replaceQualifierVariable(evaluationContext, chain.qualifierExpression)
      null
    }
    else {
      // if it is a method call, then we set additional breakpoint as for an intermediate operation
      sourceOperationBreakpoint = createSourceOperationRequestor(evaluationContext, positions.qualifierExpressionMethod, instrumentation, positions.skipCount)
      sourceOperationBreakpoint
    }

    intermediateOperationsBreakpoints = chain
      .intermediateCalls.zip(positions.intermediateStepsMethods)
      .mapIndexed { callOrder, (call, methodSignature) ->
        createIntermediateOperationRequestors(evaluationContext, callOrder, call, methodSignature, instrumentation)
      }

    terminalOperationBreakpoint = createTerminalOperationRequestors(
      evaluationContext,
      chain.terminationCall,
      positions.terminationOperationMethod,
      instrumentation,
    )

    return qualifierExpressionBreakpoint
           ?: intermediateOperationsBreakpoints.firstOrNull()?.methodEntryRequest
           ?: terminalOperationBreakpoint.methodEntryRequest
  }

  private fun createSourceOperationRequestor(
    evaluationContext: EvaluationContextImpl,
    methodSignature: JvmMethodSignature,
    instrumentation: StreamInstrumentationManager,
    qualifierSkipCount: Int,
  ): MethodExitRequestHandle {
    val filter = if (qualifierSkipCount > 0) {
      var remainingSkips = qualifierSkipCount
      { _: SuspendContextImpl, _: MethodExitEvent ->
        if (remainingSkips > 0) { remainingSkips--; false }
        else true
      }
    } else {
      null
    }

    return breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature, filter) { evalContext, _, value ->
      LOG.debug("Source operation exit request hit")
      val result = instrumentation.onSourceOperationExit(evalContext, value)
      enableNextBreakpoint(-1)
      result
    }
  }

  private fun createIntermediateOperationRequestors(
    evaluationContext: EvaluationContextImpl,
    callOrder: Int,
    @Suppress("UNUSED_PARAMETER") call: IntermediateStreamCall,
    methodSignature: JvmMethodSignature,
    instrumentation: StreamInstrumentationManager,
  ): StreamCallRuntimeInfo {
    // create exit request first to be able to activate it in entry request
    val exitRequest = breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { evalContext, _, value ->
      LOG.debug("Intermediate operation ${call.name} exit request hit")
      val result = instrumentation.onIntermediateOperationExit(evalContext, callOrder, value)
      enableNextBreakpoint(callOrder)
      result
    }
    val entryRequest = breakpointFactory.createMethodEntryBreakpoint(evaluationContext, methodSignature) { evalContext, method, args ->
      LOG.debug("Intermediate operation ${call.name} entry request hit")
      val result = instrumentation.onIntermediateOperationEntry(evalContext, callOrder, method, args)
      exitRequest.enable()
      result
    }
    return StreamCallRuntimeInfo(entryRequest, exitRequest)
  }

  private fun createTerminalOperationRequestors(
    evaluationContext: EvaluationContextImpl,
    @Suppress("UNUSED_PARAMETER") call: TerminatorStreamCall,
    methodSignature: JvmMethodSignature,
    instrumentation: StreamInstrumentationManager,
  ): StreamCallRuntimeInfo {
    val exitRequest = breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { evalContext, _, value ->
      LOG.debug("Terminal operation ${call.name} exit request hit")
      instrumentation.onTerminalOperationExit(evalContext, value)

      // Step out of the terminal method so VM lands on the next statement in user code.
      breakpointFactory.stepOut(evalContext, evaluationFinished::complete)

      value
    }
    val entryRequest = breakpointFactory.createMethodEntryBreakpoint(evaluationContext, methodSignature) { evalContext, method, args ->
      LOG.debug("Terminal operation ${call.name} entry request hit")
      val result = instrumentation.onTerminalOperationEntry(evalContext, method, args)
      exitRequest.enable()
      result
    }
    return StreamCallRuntimeInfo(entryRequest, exitRequest)
  }

  private fun disableBreakpointRequests() {
    sourceOperationBreakpoint?.disable()

    for (intermediateStepBreakpoint in intermediateOperationsBreakpoints) {
      intermediateStepBreakpoint.methodEntryRequest.disable()
      intermediateStepBreakpoint.methodExitRequest.disable()
    }

    terminalOperationBreakpoint.methodEntryRequest.disable()
    terminalOperationBreakpoint.methodExitRequest.disable()
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
  val methodEntryRequest: MethodEntryRequestHandle,
  val methodExitRequest: MethodExitRequestHandle,
)

/**
 * We need to skip breakpoints that are triggered during stream execution for several reasons:
 * - sometimes the runtime stops on lambdas even if the breakpoint is only on the line
 * ```
 * Stream.of(1, 2, 3).peek(x -> { <*> }).toArray();
 * ```
 * - the user may set a breakpoint inside a lambda or somewhere within the stream implementation
 */
private class SpuriousBreakpointResumer(
  private val streamThread: ThreadReferenceProxyImpl?,
) : DebugProcessAdapterImpl() {
  override fun paused(suspendContext: SuspendContextImpl) {
    if (streamThread != null && suspendContext.thread != streamThread) return
    val events = suspendContext.eventSet ?: return
    if (events.all { it is BreakpointEvent }) {
      LOG.info("Auto-resuming spurious user breakpoint during stream tracing")
      suspendContext.debugProcess.suspendManager.resume(suspendContext)
    }
  }
}