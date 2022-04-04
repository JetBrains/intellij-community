// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.new_arch

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.*
import com.intellij.debugger.streams.trace.breakpoint.DebuggerUtils.findVmMethod
import com.intellij.debugger.streams.trace.breakpoint.ex.MethodNotFoundException
import com.intellij.openapi.diagnostic.logger
import com.sun.jdi.*
import com.sun.jdi.request.ExceptionRequest
import com.sun.jdi.request.MethodEntryRequest
import com.sun.jdi.request.MethodExitRequest

private val LOG = logger<JDIMethodBreakpointFactory>()

class JDIMethodBreakpointFactory : MethodBreakpointFactory {
  override fun createMethodEntryBreakpoint(evaluationContext: EvaluationContextImpl,
                                           signature: MethodSignature,
                                           transformer: ArgumentsTransformer): MethodEntryRequest {
    val vmMethod = findVmMethod(evaluationContext, signature) ?: throw MethodNotFoundException(signature)
    val requestor = MethodEntryRequestor(evaluationContext.project, vmMethod) { requestor, suspendContext, event ->
      event.request().disable()
      suspendContext.debugProcess.requestsManager.deleteRequest(requestor)

      val argumentValues = try {
        getMethodArguments(suspendContext, event.method())
      }
      catch (e: UnsupportedOperationException) {
        val vm = event.virtualMachine()
        LOG.warn("Method arguments interception is not supported in ${vm.name()} ${vm.version()}", e)
        return@MethodEntryRequestor
      }

      val newArgumentsList = try {
        transformer(suspendContext, event.method(), argumentValues)
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

    return request
  }

  private fun getMethodArguments(suspendContext: SuspendContextImpl, method: Method): List<Value?> {
    val frameProxy = suspendContext.frameProxy!!
    return method.arguments().map { param ->
      frameProxy.visibleValueByName(param.name())
    }
  }

  private fun replaceArguments(suspendContext: SuspendContextImpl, method: Method, newArgumentsList: List<Value?>) {
    val frameProxy = suspendContext.frameProxy!!
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

  override fun createMethodExitBreakpoint(evaluationContext: EvaluationContextImpl,
                                          signature: MethodSignature,
                                          transformer: ReturnValueTransformer): MethodExitRequest {
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
        LOG.warn("Return value interception is not supported in ${vm.name()} ${vm.version()}", e)
        return@MethodExitRequestor
      }

      val replacedReturnValue = try {
        transformer(suspendContext, event.method(), originalReturnValue)
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

    return request
  }

  override fun createExceptionBreakpoint(evaluationContext: EvaluationContextImpl,
                                         exceptionType: ReferenceType?,
                                         callback: ExceptionHandler): ExceptionRequest {
    val requestor = ExceptionBreakpointRequestor(evaluationContext.project) { requestor, suspendContext, event ->
      event.request().disable()
      suspendContext.debugProcess.requestsManager.deleteRequest(requestor)

      callback(suspendContext, event.catchLocation(), event.exception())
    }

    return evaluationContext.debugProcess.requestsManager.createExceptionRequest(requestor, exceptionType, true, true)
  }
}