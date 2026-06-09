// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.DebugProcessAdapterImpl
import com.intellij.debugger.engine.DebugProcessImpl
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
import com.sun.jdi.InvocationException
import com.sun.jdi.Location
import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.MethodExitEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls

private val LOG = logger<StreamTracingManager>()

internal sealed class TracingResult {
  data class Success(
    val evaluationContext: EvaluationContextImpl,
    val rawTrace: ArrayReference,
  ) : TracingResult()

  data class TargetVmException(
    val exception: ObjectReference,
    val evaluationContext: EvaluationContextImpl,
  ) : TracingResult()

  data class Error(
    val errorMessage: @Nls String,
    val cause: Throwable? = null,
  ) : TracingResult()
}

private sealed interface EvaluationStatus {
  class EvaluationStarted(
    val requestors: RequestorsSet,
    val resumer: DebugProcessListener,
  ) : EvaluationStatus {
    fun cleanUp(debugProcess: DebugProcessImpl) {
      debugProcess.removeDebugProcessListener(resumer)
      requestors.cleanUp()
    }
  }

  data class EvaluationFinished(val result: TracingResult) : EvaluationStatus
}

internal class StreamTracingManager(
  private val breakpointFactory: JdiBreakpointFactory,
  private val objectStorage: ObjectStorage,
  private val handlerFactory: BreakpointBasedHandlerFactory,
) {
  private val evaluationFinished = CompletableDeferred<TracingResult>()

  suspend fun evaluateChain(
    debuggerContext: DebuggerContextImpl,
    breakpointPositions: BreakpointResolveResult.Found,
    chain: StreamChain,
  ): TracingResult {
    val started = when (val setupResult = startTracingAndResume(debuggerContext, breakpointPositions, chain)) {
      is EvaluationStatus.EvaluationFinished -> return setupResult.result
      is EvaluationStatus.EvaluationStarted -> setupResult
    }

    return try {
      evaluationFinished.await()
    } finally {
      withContext(NonCancellable) {
        withDebugContext(debuggerContext.managerThread!!) {
          started.cleanUp(debuggerContext.debugProcess!!)
        }
      }
    }
  }

  private suspend fun startTracingAndResume(
    debuggerContext: DebuggerContextImpl,
    breakpointPositions: BreakpointResolveResult.Found,
    chain: StreamChain,
  ): EvaluationStatus = withDebugContext(debuggerContext.managerThread!!) {
    val debugProcess = debuggerContext.debugProcess!!
    val evaluationContext = debuggerContext.createEvaluationContext()
                            ?: return@withDebugContext EvaluationStatus.EvaluationFinished(
                              TracingResult.Error(StreamDebuggerBundle.message("program.is.not.suspended")))

    var requestors: RequestorsSet? = null
    try {
      withDebugContext(evaluationContext.suspendContext) {
        val mgr = StreamInstrumentationManager.create(handlerFactory, objectStorage, chain, evaluationContext)
        val created = createRequestors(evaluationContext, chain, breakpointPositions, mgr)
        // Capture the handle on the debugger manager thread before enabling, so a cancellation while returning from
        // this withDebugContext call cannot orphan the already-created requestors (cleaned up in catch below).
        requestors = created
        created.firstToEnable().enable()
      }

      val resumer = SpuriousBreakpointResumer(evaluationContext.suspendContext.thread)
      debugProcess.addDebugProcessListener(resumer)
      debugProcess.suspendManager.resume(evaluationContext.suspendContext)
      EvaluationStatus.EvaluationStarted(checkNotNull(requestors), resumer)
    }
    catch (e: CancellationException) {
      requestors?.cleanUp()
      throw e
    }
    catch (e: Throwable) {
      LOG.error("Failed to set up stream tracing", e)
      requestors?.cleanUp()
      EvaluationStatus.EvaluationFinished(
        TracingResult.Error(StreamDebuggerBundle.message("stream.tracing.failed.due.to.internal.error"), e))
    }
  }

  private fun gatherTracingResults(
    instrumentation: StreamInstrumentationManager,
    evaluationContext: EvaluationContextImpl,
  ): TracingResult {
    return try {
      TracingResult.Success(evaluationContext, instrumentation.collectResults(evaluationContext))
    } catch (t: Throwable) {
      if (t is EvaluateException && t.cause is InvocationException) {
        val clientException = (t.cause as InvocationException).exception()
        TracingResult.TargetVmException(clientException, evaluationContext)
      } else {
        TracingResult.Error(StreamDebuggerBundle.message("stream.tracing.failed.due.to.internal.error"), t)
      }
    }
  }

  private fun createRequestors(
    evaluationContext: EvaluationContextImpl,
    chain: StreamChain,
    positions: BreakpointResolveResult.Found,
    instrumentation: StreamInstrumentationManager,
  ): RequestorsSet {
    val created = mutableListOf<RequestHandle<*>>()
    try {
      // create from the end so that each step references the already created next one
      val terminal = createTerminalOperationRequestors(
        evaluationContext,
        chain.terminationCall,
        positions.terminationOperationMethod,
        instrumentation
      )
      created += terminal.methodEntryRequest
      created += terminal.methodExitRequest

      var nextEntry: MethodEntryRequestHandle = terminal.methodEntryRequest
      val intermediate = arrayOfNulls<StreamCallRequestors>(chain.intermediateCalls.size)
      for (i in chain.intermediateCalls.indices.reversed()) {
        val info = createIntermediateOperationRequestors(
          evaluationContext,
          i,
          chain.intermediateCalls[i],
          positions.intermediateStepsMethods[i],
          instrumentation,
          nextEntry
        )
        created += info.methodEntryRequest
        created += info.methodExitRequest
        intermediate[i] = info
        nextEntry = info.methodEntryRequest
      }

      val source = if (positions.qualifierExpressionMethod == null) {
        // if qualifier expression is variable we need to replace it in current stack frame
        instrumentation.replaceQualifierVariable(evaluationContext, chain.qualifierExpression)
        null
      }
      else {
        // if it is a method call, then we set additional breakpoint as for an intermediate operation
        createSourceOperationRequestor(
          evaluationContext,
          positions.qualifierExpressionMethod,
          instrumentation,
          positions.skipCount,
          nextEntry
        ).also { created += it }
      }

      val exception = setupExceptionBreakpoint(evaluationContext, instrumentation)
        .also { created += it }

      return RequestorsSet(source, intermediate.asList().requireNoNulls(), terminal, exception)
    }
    catch (e: Throwable) {
      created.forEach { it.delete() }
      throw e
    }
  }

  private fun createSourceOperationRequestor(
    evaluationContext: EvaluationContextImpl,
    methodSignature: JvmMethodSignature,
    instrumentation: StreamInstrumentationManager,
    qualifierSkipCount: Int,
    nextEntry: MethodEntryRequestHandle,
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
      withFinishTracingOnException(value) {
        val result = instrumentation.onSourceOperationExit(evalContext, value)
        if (!evaluationFinished.isCompleted) nextEntry.enable()
        result
      }
    }
  }

  private fun createIntermediateOperationRequestors(
    evaluationContext: EvaluationContextImpl,
    callOrder: Int,
    call: IntermediateStreamCall,
    methodSignature: JvmMethodSignature,
    instrumentation: StreamInstrumentationManager,
    nextEntry: MethodEntryRequestHandle,
  ): StreamCallRequestors {
    // create exit request first to be able to activate it in entry request
    val exitRequest = breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { evalContext, _, value ->
      LOG.debug("Intermediate operation ${call.name} exit request hit")
      withFinishTracingOnException(value) {
        val result = instrumentation.onIntermediateOperationExit(evalContext, callOrder, value)
        if (!evaluationFinished.isCompleted) nextEntry.enable()
        result
      }
    }
    val entryRequest = breakpointFactory.createMethodEntryBreakpoint(evaluationContext, methodSignature) { evalContext, method, args ->
      LOG.debug("Intermediate operation ${call.name} entry request hit")
      withFinishTracingOnException(args) {
        val result = instrumentation.onIntermediateOperationEntry(evalContext, callOrder, method, args)
        exitRequest.enable()
        result
      }
    }
    return StreamCallRequestors(entryRequest, exitRequest)
  }

  private fun createTerminalOperationRequestors(
    evaluationContext: EvaluationContextImpl,
    @Suppress("UNUSED_PARAMETER") call: TerminatorStreamCall,
    methodSignature: JvmMethodSignature,
    instrumentation: StreamInstrumentationManager,
  ): StreamCallRequestors {
    val exitRequest = breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { evalContext, _, value ->
      LOG.debug("Terminal operation ${call.name} exit request hit")
      withFinishTracingOnException(value) {
        instrumentation.onTerminalOperationExit(evalContext, value)
        // Step out of the terminal method so VM lands on the next statement in user code.
        breakpointFactory.stepOut(evalContext) { ctx ->
          try {
            instrumentation.restoreQualifierVariableIfReplaced(ctx)
            evaluationFinished.complete(gatherTracingResults(instrumentation, ctx))
          }
          catch (e: Throwable) {
            LOG.error("Failed to gather stream tracing results", e)
            evaluationFinished.complete(TracingResult.Error(StreamDebuggerBundle.message("stream.tracing.failed.due.to.internal.error"), e))
          }
        }
        value
      }
    }
    val entryRequest = breakpointFactory.createMethodEntryBreakpoint(evaluationContext, methodSignature) { evalContext, method, args ->
      LOG.debug("Terminal operation ${call.name} entry request hit")
      withFinishTracingOnException(args) {
        val result = instrumentation.onTerminalOperationEntry(evalContext, method, args)
        exitRequest.enable()
        result
      }
    }
    return StreamCallRequestors(entryRequest, exitRequest)
  }

  /**
   * Runs [block]; on any throwable completes [evaluationFinished] with [TracingResult.Error]
   * and returns [fallback] so the JDI requestor can safely resume the VM.
   */
  private inline fun <R> withFinishTracingOnException(fallback: R, block: () -> R): R {
    return try {
      if (evaluationFinished.isCompleted) fallback else block()
    }
    catch (e: Throwable) {
      LOG.error("Stream tracing handler error", e)
      evaluationFinished.complete(TracingResult.Error(StreamDebuggerBundle.message("stream.tracing.failed.due.to.internal.error"), e))
      fallback
    }
  }

  private fun setupExceptionBreakpoint(
    evaluationContext: EvaluationContextImpl,
    instrumentation: StreamInstrumentationManager,
  ): ExceptionRequestHandle {
    val streamCallerFrameCount = evaluationContext.suspendContext.thread?.frameCount() ?: 0
    val threadRef = evaluationContext.suspendContext.thread?.getThreadReference()
    val handle = breakpointFactory.createExceptionBreakpoint(
      evaluationContext,
      threadFilter = threadRef,
    ) { evalContext, catchLoc, exception ->
      val shouldStop = runCatching {
        isStreamInterruptingException(evalContext.suspendContext, catchLoc, streamCallerFrameCount)
      }.getOrDefault(false)
      if (shouldStop) {
        instrumentation.onException(evalContext, exception)
        evaluationFinished.complete(gatherTracingResults(instrumentation, evalContext))
        true
      }
      else {
        false
      }
    }
    handle.enable()
    return handle
  }

  private fun isStreamInterruptingException(
    suspendContext: SuspendContextImpl,
    catchLocation: Location?,
    streamCallerFrameCount: Int,
  ): Boolean {
    catchLocation ?: return true
    val thread = suspendContext.thread ?: return false
    // ignore exceptions from evaluation - they will be reported by the debugger engine as EvaluateException
    if (thread.isEvaluating) return false
    val throwFrameCount = thread.frameCount()
    val streamCallerIdx = throwFrameCount - streamCallerFrameCount
    if (streamCallerIdx < 0) return false
    val frames = thread.frames()
    val catchMethod = catchLocation.method()
    val catchType = catchLocation.declaringType()
    return frames.drop(streamCallerIdx).any { frame ->
      frame.location().method() == catchMethod &&
      frame.location().declaringType() == catchType
    }
  }
}

private data class StreamCallRequestors(
  val methodEntryRequest: MethodEntryRequestHandle,
  val methodExitRequest: MethodExitRequestHandle,
)

private class RequestorsSet(
  val source: MethodExitRequestHandle?,
  val intermediate: List<StreamCallRequestors>,
  val terminal: StreamCallRequestors,
  val exception: ExceptionRequestHandle,
) {
  fun firstToEnable(): RequestHandle<*> =
    source ?: intermediate.firstOrNull()?.methodEntryRequest ?: terminal.methodEntryRequest

  fun cleanUp() {
    source?.delete()
    intermediate.forEach {
      it.methodEntryRequest.delete()
      it.methodExitRequest.delete()
    }
    terminal.methodEntryRequest.delete()
    terminal.methodExitRequest.delete()
    exception.delete()
  }
}

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
