// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.streams.StreamDebuggerBundle
import com.intellij.debugger.streams.trace.StreamTracer
import com.intellij.debugger.streams.trace.TraceResultInterpreter
import com.intellij.debugger.streams.trace.TracingCallback
import com.intellij.debugger.streams.trace.TracingResult
import com.intellij.debugger.streams.trace.breakpoint.DebuggerUtils.STREAM_DEBUGGER_UTILS_CLASS_FILE
import com.intellij.debugger.streams.trace.breakpoint.DebuggerUtils.STREAM_DEBUGGER_UTILS_CLASS_NAME
import com.intellij.debugger.streams.trace.breakpoint.TracerUtils.tryExtractExceptionMessage
import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointPlaceNotFoundException
import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointTracingException
import com.intellij.debugger.streams.trace.breakpoint.new_arch.JDIMethodBreakpointFactory
import com.intellij.debugger.streams.trace.breakpoint.new_arch.StreamTracingManager
import com.intellij.debugger.streams.trace.breakpoint.new_arch.StreamTracingManagerImpl
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.BreakpointTracingSupport
import com.intellij.debugger.streams.trace.breakpoint.new_arch.lib.RuntimeHandlerFactory
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.CommonClassNames
import com.intellij.xdebugger.XDebugSession
import com.sun.jdi.ArrayReference
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference

private val LOG = logger<MethodBreakpointTracer>()

/**
 * @author Shumaf Lovpache
 */
class MethodBreakpointTracer(private val session: XDebugSession,
                             private val breakpointTracingSupport: BreakpointTracingSupport,
                             private val breakpointResolver: BreakpointResolver,
                             private val resultInterpreter: TraceResultInterpreter) : StreamTracer {
  override fun trace(chain: StreamChain, callback: TracingCallback) {
    val xDebugProcess = session.debugProcess as? JavaDebugProcess ?: return

    val runTraceCommand = object : DebuggerCommandImpl(PrioritizedTask.Priority.NORMAL) {
      override fun action() {
        trace(xDebugProcess, chain, callback)
      }
    }
    val debuggerManagerThread = xDebugProcess.debuggerSession.process.managerThread
    debuggerManagerThread.schedule(runTraceCommand)
  }

  private fun trace(debugProcess: JavaDebugProcess, chain: StreamChain, callback: TracingCallback) {
    val process = debugProcess.debuggerSession.process
    process.managerThread.schedule(object : SuspendContextCommandImpl(process.suspendManager.pausedContext) {
      override fun contextAction(suspendContext: SuspendContextImpl) {
        val evalContext = createEvaluationContext(debugProcess, suspendContext)
        val evalContextFactory: EvaluationContextFactory = DefaultEvaluationContextFactory(evalContext.classLoader!!)
        val objectStorage: ObjectStorage = DisableCollectionObjectStorage()
        val valueManager: ValueManager = createValueManager(objectStorage)
        val handlerFactory: RuntimeHandlerFactory = breakpointTracingSupport.createRuntimeHandlerFactory(valueManager)

        val breakpointFactory: com.intellij.debugger.streams.trace.breakpoint.new_arch.MethodBreakpointFactory = JDIMethodBreakpointFactory()
        val tracingManager: StreamTracingManager = StreamTracingManagerImpl(breakpointFactory, breakpointResolver, evalContextFactory,
                                                                            handlerFactory, valueManager, debugProcess)
        try {
          // TODO: после трассировки проверять, что брейкпоинты,
          //  которые мы расставили были подчищены. Также полезно
          //  рассмотреть как поведет себя control flow в случае
          //  завершения дебага, не будет ли течь память

          // TODO: посмотреть куда будут вываливаться исключения, созданные в коллбеках брейкпоинтов
          tracingManager.evaluateChain(evalContext, chain) { context, result ->
            if (result is ArrayReference) {
              val interpretedResult: TracingResult = try {
                resultInterpreter.interpret(chain, result)
              }
              catch (t: Throwable) {
                callback.evaluationFailed("", StreamDebuggerBundle.message("evaluation.failed.cannot.interpret.result", t.message!!))
                throw t
              }
              callback.evaluated(interpretedResult, context)
              return@evaluateChain
            }

            if (result is ObjectReference) {
              val type = result.referenceType()
              if (type is ClassType) {
                var classType: ClassType? = type
                while (classType != null && CommonClassNames.JAVA_LANG_THROWABLE != classType.name()) {
                  classType = classType.superclass()
                }
                if (classType != null) {
                  val exceptionMessage = tryExtractExceptionMessage(result)
                  val description = "Evaluation failed: " + type.name() + " exception thrown"
                  val descriptionWithReason = if (exceptionMessage == null) description else "$description: $exceptionMessage"
                  callback.evaluationFailed("", descriptionWithReason)
                  return@evaluateChain
                }
              }
            }

            callback.evaluationFailed("", StreamDebuggerBundle.message("evaluation.failed.unknown.result.type"))
          }
        }
        catch (e: BreakpointPlaceNotFoundException) {
          callback.evaluationFailed("", StreamDebuggerBundle.message("evaluation.failed.cannot.find.places.for.breakpoints"))
          LOG.error(e)
        }
        catch (e: BreakpointTracingException) {
          callback.evaluationFailed("", StreamDebuggerBundle.message("evaluation.failed.cannot.initialize.breakpoints"))
          objectStorage.dispose()
          LOG.error(e)
        }
      }
    })
  }

  private fun createValueManager(objectStorage: ObjectStorage): ValueManager {
    val container = ValueManagerImpl(objectStorage)
    container.defineClass(OBJECT_COLLECTOR_CLASS_NAME, RuntimeLibrary.getBytecodeLoader(OBJECT_COLLECTOR_CLASS_FILE))
    container.defineClass(INT_COLLECTOR_CLASS_NAME, RuntimeLibrary.getBytecodeLoader(INT_COLLECTOR_CLASS_FILE))
    container.defineClass(LONG_COLLECTOR_CLASS_NAME, RuntimeLibrary.getBytecodeLoader(LONG_COLLECTOR_CLASS_FILE))
    container.defineClass(DOUBLE_COLLECTOR_CLASS_NAME, RuntimeLibrary.getBytecodeLoader(DOUBLE_COLLECTOR_CLASS_FILE))
    container.defineClass(STREAM_DEBUGGER_UTILS_CLASS_NAME, RuntimeLibrary.getBytecodeLoader(STREAM_DEBUGGER_UTILS_CLASS_FILE))

    return container
  }

  private fun createEvaluationContext(debugProcess: JavaDebugProcess, suspendContext: SuspendContextImpl): EvaluationContextImpl {
    val process = debugProcess.debuggerSession.process
    val currentStackFrameProxy = suspendContext.frameProxy
    val ctx = EvaluationContextImpl(suspendContext, currentStackFrameProxy)
      .withAutoLoadClasses(true)
    // explicitly setting class loader because we don't want to modify user's class loader
    ctx.classLoader = ClassLoadingUtils.getClassLoader(ctx, process)
    return ctx
  }
}
