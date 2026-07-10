// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.DebugProcessAdapterImpl
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.RequestHint
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.requests.Requestor
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.streams.trace.breakpoint.ex.MethodNotFoundException
import com.intellij.debugger.ui.breakpoints.AnyExceptionBreakpoint
import com.intellij.debugger.ui.breakpoints.FilteredRequestor
import com.intellij.debugger.ui.breakpoints.FilteredRequestorImpl
import com.intellij.debugger.ui.breakpoints.SyntheticBreakpoint
import com.intellij.debugger.ui.breakpoints.JavaExceptionBreakpointType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.sun.jdi.ClassNotLoadedException
import com.sun.jdi.IncompatibleThreadStateException
import com.sun.jdi.InvalidTypeException
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value
import com.sun.jdi.event.ExceptionEvent
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodEntryEvent
import com.sun.jdi.event.MethodExitEvent
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.ExceptionRequest
import com.sun.jdi.request.InvalidRequestStateException
import com.sun.jdi.request.MethodEntryRequest
import com.sun.jdi.request.MethodExitRequest
import com.sun.jdi.request.StepRequest
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpoint
import org.jetbrains.java.debugger.breakpoints.properties.JavaExceptionBreakpointProperties

private val LOG = logger<JdiBreakpointFactory>()

/**
 * JDI does not provide mirror for `null`.
 * `null` in debugee VM == `null` in JDI
 * So you should return same value as passed if you don't want to modify
 */
typealias ReturnValueTransformer = (EvaluationContextImpl, Method, Value?) -> Value?
typealias ArgumentsTransformer = (EvaluationContextImpl, Method, List<Value?>) -> List<Value?>
typealias ExceptionHandler = (EvaluationContextImpl, Location?, ObjectReference) -> Boolean

/**
 * Note:
 * - All breakpoints are disabled by default
 * - All breakpoints are deleted after hit
 */
internal class RequestHandle<out T : EventRequest>(
  private val debugProcess: DebugProcessImpl,
  private val requestor: Requestor,
  private val request: T,
) {
  fun enable(): Unit = request.enable()

  fun disable() {
    if (request.isEnabled) {
      request.disable()
    }
  }

  fun delete(): Unit = debugProcess.requestsManager.deleteRequest(requestor)
}

internal typealias MethodEntryRequestHandle = RequestHandle<MethodEntryRequest>
internal typealias MethodExitRequestHandle = RequestHandle<MethodExitRequest>
internal typealias ExceptionRequestHandle = RequestHandle<ExceptionRequest>

internal class JdiBreakpointFactory {
  fun createMethodEntryBreakpoint(evaluationContext: EvaluationContextImpl,
                                  signature: JvmMethodSignature,
                                  filter: ((SuspendContextImpl, MethodEntryEvent) -> Boolean)? = null,
                                  onMethodEntry: ArgumentsTransformer): MethodEntryRequestHandle {
    val vmMethod = findVmMethod(evaluationContext, signature) ?: throw MethodNotFoundException(signature)
    // There is no need to request a hit because we just need to replace arguments on the fly
    val requestor = MethodEntryRequestor(evaluationContext.project, vmMethod) { requestor, suspendContext, event ->
      if (filter != null && !filter(suspendContext, event)) {
        event.request().enable()
        return@MethodEntryRequestor
      }
      event.request().disable()
      suspendContext.debugProcess.requestsManager.deleteRequest(requestor)

      val evaluationContext = EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy())
      val argumentValues = getMethodArguments(suspendContext, event.method())
      val newArgumentsList = try {
        onMethodEntry(evaluationContext, event.method(), argumentValues)
      }
      catch (e: Throwable) {
        LOG.warn("Error occurred during ${signature} method arguments modification", e)
        return@MethodEntryRequestor
      }

      if (newArgumentsList == argumentValues) {
        return@MethodEntryRequestor
      }

      if (!validateArgumentsList(event.method(), newArgumentsList)) {
        LOG.warn(
          "Could not replace arguments in method ${vmMethod.name()}${vmMethod.signature()} because actual arguments differs from formal parameters")
        return@MethodEntryRequestor
      }

      replaceArguments(suspendContext, event.method(), newArgumentsList)
    }

    val request = evaluationContext.debugProcess.requestsManager.createMethodEntryRequest(requestor)
    request.addClassFilter(vmMethod.declaringType())

    return MethodEntryRequestHandle(evaluationContext.debugProcess, requestor, request)
  }

  fun createMethodExitBreakpoint(evaluationContext: EvaluationContextImpl,
                                 signature: JvmMethodSignature,
                                 filter: ((SuspendContextImpl, MethodExitEvent) -> Boolean)? = null,
                                 onMethodExit: ReturnValueTransformer): MethodExitRequestHandle {
    val vmMethod = findVmMethod(evaluationContext, signature) ?: throw MethodNotFoundException(signature)
    val requestor = MethodExitRequestor(evaluationContext.project, vmMethod) { requestor, suspendContext, event ->
      if (filter != null && !filter(suspendContext, event)) {
        event.request().enable()
        return@MethodExitRequestor
      }
      event.request().disable()
      suspendContext.debugProcess.requestsManager.deleteRequest(requestor)

      val threadProxy = suspendContext.thread ?: return@MethodExitRequestor

      val originalReturnValue = try {
        event.returnValue()
      }
      catch (e: UnsupportedOperationException) {
        val vm = event.virtualMachine()
        LOG.warn("Return value interception is not supported in ${vm.name()} ${vm.version()}", e)
        return@MethodExitRequestor
      }

      val evaluationContext = EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy())
      val replacedReturnValue = try {
        onMethodExit(evaluationContext, event.method(), originalReturnValue)
      }
      catch (e: Throwable) {
        LOG.warn("Error occurred during ${signature} method return value modification", e)
        return@MethodExitRequestor
      }

      if (replacedReturnValue == originalReturnValue) {
        return@MethodExitRequestor
      }

      try {
        threadProxy.forceEarlyReturn(replacedReturnValue)
      }
      catch (e: ClassNotLoadedException) {
        LOG.warn("Class for type ${replacedReturnValue?.type()?.name()} has not been loaded yet", e)
        throw e
      }
      catch (e: IncompatibleThreadStateException) {
        LOG.warn("Current thread is not suspended", e)
        throw e
      }
      catch (e: InvalidTypeException) {
        LOG.warn("Could not cast value of type ${replacedReturnValue?.type()?.name()} to ${originalReturnValue.type().name()}", e)
        throw e
      }
    }

    val request = evaluationContext.debugProcess.requestsManager.createMethodExitRequest(requestor)
    request.addClassFilter(vmMethod.declaringType())

    return MethodExitRequestHandle(evaluationContext.debugProcess, requestor, request)
  }

  fun createExceptionBreakpoint(evaluationContext: EvaluationContextImpl,
                                exceptionType: ReferenceType? = null,
                                threadFilter: ThreadReference? = null,
                                callback: ExceptionHandler): ExceptionRequestHandle {
    val requestor = createExceptionRequestor(evaluationContext.project, callback)

    val request = evaluationContext.debugProcess.requestsManager.createExceptionRequest(requestor, exceptionType, true, true)
    if (threadFilter != null) {
      request.addThreadFilter(threadFilter)
    }
    return ExceptionRequestHandle(evaluationContext.debugProcess, requestor, request)
  }

  private fun createExceptionRequestor(
    project: Project,
    callback: ExceptionHandler,
  ): FilteredRequestor {
    val defaultExceptionBreakpoint = getDefaultExceptionBreakpoint(project)
    if (defaultExceptionBreakpoint != null) {
      return TechnicalExceptionBreakpoint(defaultExceptionBreakpoint, project, callback)
    }

    LOG.warn("Failed to get default Java exception breakpoint; falling back to a generic requestor")
    return object : FilteredRequestorImpl(project), SyntheticBreakpoint {
      override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean {
        return processExceptionEvent(this, callback, action, event)
      }

      override fun getSuspendPolicy(): String = DebuggerSettings.SUSPEND_THREAD
    }
  }

  private fun getMethodArguments(suspendContext: SuspendContextImpl, method: Method): List<Value?> {
    val frameProxy = suspendContext.frameProxyOrThrow()
    return method.arguments().map { param ->
      frameProxy.visibleValueByName(param.name())
    }
  }

  private fun replaceArguments(suspendContext: SuspendContextImpl, method: Method, newArgumentsList: List<Value?>) {
    val frameProxy = suspendContext.frameProxyOrThrow()
    method
      .arguments()
      .zip(newArgumentsList)
      .forEachIndexed { idx, (param, arg) ->
        try {
          val variable = frameProxy.visibleVariableByName(param.name())
          frameProxy.setValue(variable, arg)
        }
        catch (e: ClassNotLoadedException) {
          LOG.warn("Class for type ${param.typeName()} has not been loaded yet", e)
          throw e
        }
        catch (e: InvalidTypeException) {
          LOG.warn("Could not cast value of type ${arg?.type()?.name()} to ${param.typeName()}", e)
          throw e
        }
        catch (e: EvaluateException) {
          LOG.warn("Could not replace argument with index ${idx} in method ${method.name()}${method.signature()}", e)
          throw e
        }
      }
  }

  private fun validateArgumentsList(method: Method, argumentsList: List<Value?>): Boolean {
    val methodParameters = method.arguments()
    if (argumentsList.size != methodParameters.size) {
      return false
    }

    return methodParameters.zip(argumentsList).all { (param, arg) ->
      DebuggerUtils.instanceOf(arg?.type(), param.typeName())
    }
  }

  /**
   * Performs a step-out from the current frame. [onComplete] is called when the step finishes
   * and the VM is suspended at the new location.
   */
  fun stepOut(evaluationContext: EvaluationContextImpl, onComplete: (EvaluationContextImpl) -> Unit) {
    val suspendContext = evaluationContext.suspendContext
    val thread = suspendContext.thread ?: return
    suspendContext.debugProcess.doStep(
      suspendContext, thread, StepRequest.STEP_MIN, StepRequest.STEP_OUT,
      StepOutRequestHint(thread, suspendContext, onComplete), null,
    )
  }
}

private fun processExceptionEvent(
  requestor: Requestor,
  callback: ExceptionHandler,
  action: SuspendContextCommandImpl,
  event: LocatableEvent?,
): Boolean {
  if (event !is ExceptionEvent) return false
  val context = action.suspendContext ?: return false
  if (context.thread?.isSuspended != true) return false

  val evalCtx = EvaluationContextImpl(context, context.getFrameProxy())
  val hit = runCatching {
    callback(evalCtx, event.catchLocation(), event.exception())
  }.onFailure {
    LOG.warn("Error occurred during exception breakpoint callback", it)
  }.getOrDefault(false)

  if (hit) {
    event.request().disable()
    context.debugProcess.requestsManager.deleteRequest(requestor)
  }

  return hit
}

private fun getDefaultExceptionBreakpoint(project: Project): XBreakpoint<JavaExceptionBreakpointProperties>? {
  val exceptionType = XDebuggerUtil.getInstance().findBreakpointType(JavaExceptionBreakpointType::class.java)
                      ?: return null
  return XDebuggerManager.getInstance(project).breakpointManager.getDefaultBreakpoints(exceptionType).firstOrNull()
}

private class TechnicalExceptionBreakpoint(
  xBreakpoint: XBreakpoint<JavaExceptionBreakpointProperties>,
  project: Project,
  private val callback: ExceptionHandler,
) : AnyExceptionBreakpoint(project, xBreakpoint), SyntheticBreakpoint {
  override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean {
    return processExceptionEvent(this, callback, action, event)
  }

  override fun getSuspendPolicy(): String = DebuggerSettings.SUSPEND_THREAD
}

private class StepOutRequestHint(
  thread: ThreadReferenceProxyImpl,
  suspendContext: SuspendContextImpl,
  private val onComplete: (EvaluationContextImpl) -> Unit,
) : RequestHint(thread, suspendContext, StepRequest.STEP_OUT) {
  override fun getNextStepDepth(context: SuspendContextImpl): Int {
    // Debugger engine may initiate transfer to SUSPEND_ALL and invalidate `context`
    // To fix that we install a single-shot listener that fires when program is actually paused
    context.debugProcess.addDebugProcessListener(object : DebugProcessAdapterImpl() {
      override fun paused(suspendContext: SuspendContextImpl) {
        context.debugProcess.removeDebugProcessListener(this)
        onComplete(EvaluationContextImpl(suspendContext, suspendContext.frameProxy))
      }
      override fun processDetached(process: DebugProcessImpl, closedByUser: Boolean) {
        context.debugProcess.removeDebugProcessListener(this)
      }
    })
    return STOP
  }
}