// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.DebuggerUtils.findVmMethod
import com.intellij.debugger.streams.trace.breakpoint.interceptor.JAVA_UTIL_FUNCTION_DOUBLE_CONSUMER
import com.intellij.debugger.streams.trace.breakpoint.interceptor.JAVA_UTIL_FUNCTION_INT_CONSUMER
import com.intellij.debugger.streams.trace.breakpoint.interceptor.JAVA_UTIL_FUNCTION_LONG_CONSUMER
import com.intellij.debugger.streams.trace.breakpoint.interceptor.ValueInterceptorFactory
import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointTracingException
import com.intellij.debugger.streams.trace.breakpoint.ex.MethodNotFoundException
import com.intellij.debugger.streams.trace.breakpoint.ex.ValueInstantiationException
import com.intellij.debugger.streams.trace.breakpoint.ex.ValueInterceptionException
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.CommonClassNames.*
import com.sun.jdi.*
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.MethodExitRequest
import com.sun.jdi.request.StepRequest

private val LOG = logger<JavaMethodBreakpointFactory>()

/**
 * @author Shumaf Lovpache
 */
class JavaMethodBreakpointFactory(private val evaluationContextFactory: EvaluationContextFactory,
                                  private val collectorFactory: ValueInterceptorFactory,
                                  private val executionCallback: StreamExecutionCallback,
                                  private val streamChain: StreamChain) : MethodBreakpointFactory {

  private var originalStreamQualifierValue: ObjectReference? = null

  override fun replaceQualifierExpressionValue(evaluationContext: EvaluationContextImpl) {
    // TODO: !! is bad
    val frameProxy = evaluationContext.frameProxy!!
    val qualifierVariable = frameProxy.visibleVariableByName(streamChain.qualifierExpression.text)
    val qualifierValue = frameProxy.getValue(qualifierVariable) as ObjectReference

    // TODO: тут не всегда JAVA_UTIL_FUNCTION_CONSUMER
    val collector = collectorFactory.getForIntermediate(evaluationContext, getCollectorType(qualifierValue.referenceType().name()))
    val wrappedResult = collector.intercept(evaluationContext, qualifierValue)

    frameProxy.setValue(qualifierVariable, wrappedResult)

    if (originalStreamQualifierValue != null)
      throw ValueInterceptionException("Qualifier expression value has already been replaced")

    originalStreamQualifierValue = qualifierValue
  }

  override fun restoreQualifierExpressionValueIfNeeded(evaluationContext: EvaluationContextImpl) {
    if (originalStreamQualifierValue == null)
      return

    // TODO: !! is bad
    val frameProxy = evaluationContext.frameProxy!!
    val qualifierVariable = frameProxy.visibleVariableByName(streamChain.qualifierExpression.text)
    frameProxy.setValue(qualifierVariable, originalStreamQualifierValue)
  }

  override fun createProducerStepBreakpoint(evaluationContext: EvaluationContextImpl,
                                            signature: MethodSignature,
                                            nextBreakpoint: MethodExitRequest): MethodExitRequest =
    createIntermediateStepBreakpoint(evaluationContext, signature, nextBreakpoint)

  override fun createIntermediateStepBreakpoint(evaluationContext: EvaluationContextImpl,
                                                signature: MethodSignature,
                                                nextBreakpoint: MethodExitRequest): MethodExitRequest {
    return createMethodExitBreakpointRequest(evaluationContext, signature) { suspendContext, _, value ->
      nextBreakpoint.enable()

      val context = evaluationContextFactory.createContext(checkSuspendContext(suspendContext))
      val collector = collectorFactory.getForIntermediate(context, getCollectorType(signature.returnType))
      val wrappedResult = collector.intercept(context, value)

      return@createMethodExitBreakpointRequest wrappedResult
    }
  }

  override fun createTerminationOperationBreakpoint(evaluationContext: EvaluationContextImpl,
                                                    signature: MethodSignature): MethodExitRequest {
    return createMethodExitBreakpointRequest(evaluationContext, signature) { suspendContext, _, value ->
      collectorFactory
        .getForTermination(evaluationContext)
        .intercept(evaluationContext, value)

      if (suspendContext !is SuspendContextImpl)
        throw BreakpointTracingException("Cannot trace stream chain because suspend context has unsupported type")

      returnToStreamDeclaringMethod(suspendContext)
      return@createMethodExitBreakpointRequest null
    }
  }

  private fun returnToStreamDeclaringMethod(suspendContext: SuspendContextImpl) {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    val debugProcess = suspendContext.debugProcess
    val threadRef = suspendContext.thread!!.threadReference
    val req: StepRequest = debugProcess.requestsManager.vmRequestManager.createStepRequest(threadRef, StepRequest.STEP_LINE,
                                                                                           StepRequest.STEP_OUT)
    req.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
    req.enable()
  }

  /**
   * Returns null when method with specified [signature] cannot be found in target VM
   */
  private fun createMethodExitBreakpointRequest(evaluationContext: EvaluationContextImpl,
                                                signature: MethodSignature,
                                                transformer: (SuspendContext, Method, Value) -> Value?): MethodExitRequest {
    val vmMethod = findVmMethod(evaluationContext, signature) ?: throw MethodNotFoundException(signature)
    val requestor = MethodExitRequestor(evaluationContext.project, vmMethod) { requestor, suspendContext, event ->
      event.request().disable()
      suspendContext.debugProcess.requestsManager.deleteRequest(requestor)

      val threadProxy = suspendContext.thread ?: return@MethodExitRequestor

      val originalReturnValue = try {
        event.returnValue()
      }
      catch (e: UnsupportedOperationException) {
        val vm = event.virtualMachine()
        LOG.info("Return value interception is not supported in ${vm.name()} ${vm.version()}", e)
        return@MethodExitRequestor
      }

      val replacedReturnValue = try {
        transformer(suspendContext, event.method(), originalReturnValue)
      }
                                catch (e: Throwable) {
                                  LOG.info("Error occurred during ${signature} method return value modification", e)
                                  null
                                } ?: return@MethodExitRequestor

      try {
        threadProxy.forceEarlyReturn(replacedReturnValue)
      }
      catch (e: ClassNotLoadedException) {
        executionCallback.breakpointSetupFailed(e)
        LOG.info("Class for type ${replacedReturnValue.type().name()} has not been loaded yet", e)
      }
      catch (e: IncompatibleThreadStateException) {
        executionCallback.breakpointSetupFailed(e)
        LOG.info("Current thread is not suspended", e)
      }
      catch (e: InvalidTypeException) {
        executionCallback.breakpointSetupFailed(e)
        LOG.info("Could not cast value of type ${replacedReturnValue.type().name()} to ${originalReturnValue.type().name()}", e)
      }
    }

    val request = evaluationContext.debugProcess.requestsManager.createMethodExitRequest(requestor)
    request.addClassFilter(vmMethod.declaringType())

    return request
  }

  private fun checkSuspendContext(suspendContext: SuspendContext): SuspendContextImpl {
    if (suspendContext !is SuspendContextImpl)
      throw BreakpointTracingException("Cannot trace stream chain because suspend context has unsupported type")

    return suspendContext
  }

  private fun getCollectorType(streamType: String) = when(streamType) {
    JAVA_UTIL_STREAM_STREAM -> JAVA_UTIL_FUNCTION_CONSUMER
    JAVA_UTIL_STREAM_INT_STREAM -> JAVA_UTIL_FUNCTION_INT_CONSUMER
    JAVA_UTIL_STREAM_LONG_STREAM -> JAVA_UTIL_FUNCTION_LONG_CONSUMER
    JAVA_UTIL_STREAM_DOUBLE_STREAM -> JAVA_UTIL_FUNCTION_DOUBLE_CONSUMER
    else -> throw ValueInstantiationException("Could not get collector for stream of type $streamType")
  }
}