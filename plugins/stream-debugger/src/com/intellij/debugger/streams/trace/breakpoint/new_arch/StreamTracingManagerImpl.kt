// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.new_arch

import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.*
import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointTracingException
import com.intellij.debugger.streams.trace.breakpoint.ex.ValueInterceptionException
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeHandlerFactory
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.QualifierExpression
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall
import com.intellij.openapi.application.runInEdt
import com.intellij.psi.CommonClassNames
import com.intellij.psi.CommonClassNames.JAVA_LANG_THROWABLE
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.sun.jdi.ArrayReference
import com.sun.jdi.Location
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import com.sun.jdi.request.*

class StreamTracingManagerImpl(private val breakpointFactory: MethodBreakpointFactory,
                               private val breakpointResolver: BreakpointResolver,
                               private val evalContextFactory: EvaluationContextFactory,
                               private val handlerFactory: RuntimeHandlerFactory,
                               private val valueManager: ValueManager,
                               private val debugProcess: JavaDebugProcess) : StreamTracingManager {
  private var originalStreamQualifierValue: ObjectReference? = null

  private var exceptionGuard: ExceptionRequest? = null

  private var sourceOperationBreakpoint: MethodExitRequest? = null
  private var intermediateOperationsBreakpoints: List<StreamCallRuntimeInfo> = emptyList()
  private var terminalOperationBreakpoint: StreamCallRuntimeInfo? = null

  // if exception occurred during stream execution here will be thrown exception
  private var exceptionInstance: ObjectReference? = null

  override fun evaluateChain(evaluationContextImpl: EvaluationContextImpl, chain: StreamChain, callback: ChainEvaluationCallback) {
    val firstRequestor = createRequestors(evaluationContextImpl, chain, breakpointResolver.findBreakpointPlaces(chain))
    firstRequestor.enable()
    //initExceptionGuard(evaluationContextImpl) TODO: better exception handling

    val session = debugProcess.session
    val sessionListener = createTraceFinishListener(chain, session, callback)
    session.addSessionListener(sessionListener)
    runInEdt { session.resume() }
  }

  private fun initExceptionGuard(evaluationContext: EvaluationContextImpl) {
    exceptionGuard = breakpointFactory.createExceptionBreakpoint(evaluationContext) { suspendContext, location, exception ->
      if (location == null || affectsStreamExecution(location, exception)) { // unhandled
        disableBreakpointRequests()
        exceptionInstance = exception
        // TODO: possible early exit?
      }
    }
    exceptionGuard!!.enable()
  }

  // This method should check if exception handler is outside the stream
  private fun affectsStreamExecution(location: Location?, exception: ObjectReference): Boolean {
    return false // so far only unhandled exceptions
  }

  private fun createTraceFinishListener(chain: StreamChain,
                                        session: XDebugSession,
                                        callback: ChainEvaluationCallback): XDebugSessionListener {
    val currentStackFrame = session.currentStackFrame as? JavaStackFrame
                            ?: throw BreakpointTracingException("Cannot determine current location")
    val outerMethod = currentStackFrame.descriptor.method
    return object : XDebugSessionListener {
      override fun sessionPaused() {
        super.sessionPaused()
        val frame = session.currentStackFrame
        if (frame is JavaStackFrame && frame.descriptor.method == outerMethod) {
          val ctx = debugProcess.session.suspendContext as SuspendContextImpl
          val evaluationContext = evalContextFactory.createContext(ctx)
          restoreQualifierExpressionValueIfNeeded(evaluationContext, chain)

          val result = getFormattedResult(evaluationContext)
          callback(evaluationContext, result)

          debugProcess.session.removeSessionListener(this)
        }
      }
    }
  }

  // returns first breakpoint request for stream chain
  private fun createRequestors(evaluationContext: EvaluationContextImpl,
                               chain: StreamChain,
                               locations: StreamChainBreakpointPlaces): EventRequest {
    val qualifierExpressionBreakpoint = if (locations.qualifierExpressionMethod == null) {
      // if qualifier expression is variable we need to replace it in current stack frame
      replaceQualifierExpressionValue(evaluationContext, chain.qualifierExpression)
      null
    }
    else {
      // if it is a method call, then we set additional breakpoint as for an intermediate operation
      sourceOperationBreakpoint = createSourceOperationRequestor(evaluationContext, locations.qualifierExpressionMethod)
      sourceOperationBreakpoint
    }

    intermediateOperationsBreakpoints = chain
      .intermediateCalls.zip(locations.intermediateStepsMethods)
      .mapIndexed { callOrder, (call, methodSignature) ->
        createIntermediateOperationRequestors(evaluationContext, callOrder, call, methodSignature)
      }

    terminalOperationBreakpoint = createTerminalOperationRequestors(evaluationContext, chain.terminationCall,
                                                                    locations.terminationOperationMethod)

    return qualifierExpressionBreakpoint
           ?: intermediateOperationsBreakpoints.firstOrNull()?.methodEntryRequest
           ?: terminalOperationBreakpoint!!.methodEntryRequest
  }

  private fun createSourceOperationRequestor(evaluationContext: EvaluationContextImpl,
                                             methodSignature: MethodSignature): MethodExitRequest {
    return breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { suspendContext, _, value ->
      enableNextBreakpoint(-1)
      transformIfObjectReference(value) {
        val context = evalContextFactory.createContext(checkSuspendContext(suspendContext))
        val handler = handlerFactory.getForSource()
        val nextHandler = getNextCallTransformer(-1)

        nextHandler.beforeCall(context, handler.afterCall(context, it))
      }
    }
  }

  private fun createIntermediateOperationRequestors(evaluationContext: EvaluationContextImpl,
                                                    callOrder: Int, call: IntermediateStreamCall,
                                                    methodSignature: MethodSignature): StreamCallRuntimeInfo {
    val handler = handlerFactory.getForIntermediate(call)
    // create exit request first to be able to activate it in entry request
    val exitRequest = breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { suspendContext, _, value ->
      enableNextBreakpoint(callOrder)
      val context = evalContextFactory.createContext(checkSuspendContext(suspendContext))
      val nextTransformer = getNextCallTransformer(callOrder)
      nextTransformer.beforeCall(context, handler.afterCall(context, value))
    }
    val entryRequest = breakpointFactory.createMethodEntryBreakpoint(evaluationContext, methodSignature) { suspendContext, _, args ->
      exitRequest.enable()
      val context = evalContextFactory.createContext(checkSuspendContext(suspendContext))
      handler.transformArguments(context, args)
    }
    return StreamCallRuntimeInfo(handler, entryRequest, exitRequest)
  }

  private fun createTerminalOperationRequestors(evaluationContext: EvaluationContextImpl,
                                                call: TerminatorStreamCall,
                                                methodSignature: MethodSignature): StreamCallRuntimeInfo {
    val handler = handlerFactory.getForTermination(call)
    val exitRequest = breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { suspendContext, _, value ->
      val context = evalContextFactory.createContext(checkSuspendContext(suspendContext))
      val stepOutRequest = createStepOutRequest(context.suspendContext)
      stepOutRequest.enable()
      handler.afterCall(context, value)
    }
    val entryRequest = breakpointFactory.createMethodEntryBreakpoint(evaluationContext, methodSignature) { suspendContext, _, args ->
      exitRequest.enable()
      val context = evalContextFactory.createContext(checkSuspendContext(suspendContext))
      handler.transformArguments(context, args)
    }
    return StreamCallRuntimeInfo(handler, entryRequest, exitRequest)
  }

  private fun transformIfObjectReference(value: Value?, transformer: (ObjectReference) -> Value?): Value? {
    return if (value != null && value is ObjectReference) {
      transformer(value)
    }
    else {
      value
    }
  }

  // TODO: remove requestors if exception occurs
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

    if (terminalOperationBreakpoint?.methodEntryRequest?.isEnabled == true) {
      terminalOperationBreakpoint?.methodEntryRequest?.disable()
    }
    if (terminalOperationBreakpoint?.methodExitRequest?.isEnabled == true) {
      terminalOperationBreakpoint?.methodExitRequest?.disable()
    }
  }

  // TODO: move to separate class
  private fun getFormattedResult(evaluationContext: EvaluationContextImpl): Value {
    return valueManager.watch(evaluationContext) {
      val infos = intermediateOperationsBreakpoints.map { it.handler.result(evaluationContext) }.toMutableList()
      val streamResult = if (exceptionInstance != null) {
        array(JAVA_LANG_THROWABLE, 1).apply {
          setValue(0, exceptionInstance)
        }
      }
      else {
        val streamResult = terminalOperationBreakpoint?.handler?.result(evaluationContext)
        val resultArray = streamResult as ArrayReference
        infos.add(resultArray.getValue(0))
        resultArray.getValue(1)
      }
      val infoArray = if (infos.isNotEmpty()) {
        array(infos)
      }
      else {
        array(CommonClassNames.JAVA_LANG_OBJECT, 0)
      }
      array(
        infoArray,
        streamResult,
        array(0L.mirror)
      )
    }
  }

  private fun createStepOutRequest(suspendContext: SuspendContextImpl): StepRequest {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    val debugProcess = suspendContext.debugProcess
    val threadRef = suspendContext.thread!!.threadReference
    val req: StepRequest = debugProcess.requestsManager.vmRequestManager.createStepRequest(threadRef, StepRequest.STEP_LINE,
                                                                                           StepRequest.STEP_OUT)
    req.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
    return req
  }

  private fun getNextCallTransformer(callNumber: Int): BeforeCallTransformer {
    return if (callNumber + 1 >= intermediateOperationsBreakpoints.size) {
      terminalOperationBreakpoint!!.handler
    }
    else {
      intermediateOperationsBreakpoints[callNumber + 1].handler
    }
  }

  private fun enableNextBreakpoint(callNumber: Int) {
    if (callNumber + 1 >= intermediateOperationsBreakpoints.size) {
      terminalOperationBreakpoint!!.methodEntryRequest.enable()
    }
    else {
      intermediateOperationsBreakpoints[callNumber + 1].methodEntryRequest.enable()
    }
  }

  /**
   * NOTE: this approach will work only if the qualifier expression is a simple local variable reference
   * For ex. if qualifier expression is something like this:
   * ```
   * a > 2 ? Stream.of(1, 2, 3) : Stream.iterate(1, x -> x + 1)
   * ```
   * or this:
   * ```
   * obj.field
   * ```
   * we can't determine where we should place breakpoint
   */
  private fun replaceQualifierExpressionValue(evaluationContext: EvaluationContextImpl, qualifierExpression: QualifierExpression) {
    // not null assertion fails only if incorrect evaluationContext passed to method
    val frameProxy = evaluationContext.frameProxy!!
    val qualifierVariable = frameProxy.visibleVariableByName(qualifierExpression.text)
    val qualifierValue = frameProxy.getValue(qualifierVariable) as ObjectReference

    val handler = handlerFactory.getForSource()
    val transformedQualifierValue = handler.afterCall(evaluationContext, qualifierValue)
    frameProxy.setValue(qualifierVariable, transformedQualifierValue)

    if (originalStreamQualifierValue != null)
      throw ValueInterceptionException("Qualifier expression value has already been replaced")

    originalStreamQualifierValue = qualifierValue
  }

  private fun restoreQualifierExpressionValueIfNeeded(evaluationContext: EvaluationContextImpl, streamChain: StreamChain) {
    if (originalStreamQualifierValue == null)
      return

    // not null assertion fails only if incorrect evaluationContext passed to method
    val frameProxy = evaluationContext.frameProxy!!
    val qualifierVariable = frameProxy.visibleVariableByName(streamChain.qualifierExpression.text)
    frameProxy.setValue(qualifierVariable, originalStreamQualifierValue)
  }

  private fun checkSuspendContext(suspendContext: SuspendContext): SuspendContextImpl {
    if (suspendContext !is SuspendContextImpl)
      throw BreakpointTracingException("Cannot trace stream chain because suspend context has unsupported type")

    return suspendContext
  }
}

private data class StreamCallRuntimeInfo(val handler: StreamOperationHandler,
                                         val methodEntryRequest: MethodEntryRequest,
                                         val methodExitRequest: MethodExitRequest)
