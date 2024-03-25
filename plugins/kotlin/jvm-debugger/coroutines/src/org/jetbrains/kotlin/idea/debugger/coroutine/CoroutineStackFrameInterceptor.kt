// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.debugger.actions.AsyncStacksToggleAction
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.SuspendManagerUtil
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.execution.ui.layout.impl.RunnerContentUi
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.rt.debugger.CoroutinesDebugHelper
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.sun.jdi.ArrayReference
import com.sun.jdi.Location
import com.sun.jdi.LongValue
import com.sun.jdi.ObjectReference
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.core.StackFrameInterceptor
import org.jetbrains.kotlin.idea.debugger.core.stepping.ContinuationFilter
import org.jetbrains.kotlin.idea.debugger.coroutine.data.SuspendExitMode
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.SkipCoroutineStackFrameProxyImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.BaseContinuationImplLight
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.DebugMetadata
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.DebugProbesImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.util.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


class CoroutineStackFrameInterceptor : StackFrameInterceptor {
    override fun createStackFrame(frame: StackFrameProxyImpl, debugProcess: DebugProcessImpl): XStackFrame? {
        if (debugProcess.xdebugProcess?.session is XDebugSessionImpl
            && frame !is SkipCoroutineStackFrameProxyImpl
            && AsyncStacksToggleAction.isAsyncStacksEnabled(debugProcess.xdebugProcess?.session as XDebugSessionImpl)) {
            val suspendContextImpl = SuspendManagerUtil.getContextForEvaluation(debugProcess.suspendManager)
            val stackFrame = suspendContextImpl?.let {
                CoroutineFrameBuilder.coroutineExitFrame(frame, it)
            }

            if (stackFrame != null && Registry.`is`("debugger.kotlin.auto.show.coroutines.view")) {
                showCoroutinePanel(debugProcess)
            }

            return stackFrame
        }
        return null
    }

    private fun showCoroutinePanel(debugProcess: DebugProcessImpl) {
        val ui = debugProcess.session.xDebugSession?.ui.safeAs<RunnerLayoutUiImpl>() ?: return
        val runnerContentUi = RunnerContentUi.KEY.getData(ui) ?: return
        runInEdt {
            runnerContentUi.findOrRestoreContentIfNeeded(CoroutineDebuggerContentInfo.XCOROUTINE_THREADS_CONTENT)
        }
    }

    override fun extractContinuationFilter(suspendContext: SuspendContextImpl): ContinuationFilter? {
        val frameProxy = suspendContext.getStackFrameProxyImpl() ?: return null
        val defaultExecutionContext = DefaultExecutionContext(suspendContext, frameProxy)
        val debugProbesImpl = DebugProbesImpl.instance(defaultExecutionContext)
        if (debugProbesImpl != null && debugProbesImpl.isInstalled) {
            // first try the helper, it is the fastest way
            var currentCoroutines = getCoroutinesRunningOnCurrentThreadFromHelper(defaultExecutionContext, debugProbesImpl)

            // then try the mirror
            if (currentCoroutines == null) {
                currentCoroutines = debugProbesImpl.getCoroutinesRunningOnCurrentThread(defaultExecutionContext)
            }
            return when {
                currentCoroutines.isEmpty() -> null
                else -> ContinuationIdFilter(currentCoroutines)
            }
        } else {
            //TODO: IDEA-341142 show nice notification about this
            thisLogger().warn("No ThreadLocal coroutine tracking is found")
        }
        return continuationObjectFilter(suspendContext, defaultExecutionContext)
    }

    private fun getCoroutinesRunningOnCurrentThreadFromHelper(
        context: DefaultExecutionContext,
        debugProbesImpl: DebugProbesImpl
    ): Set<Long>? {
        try {
            val helperClass = ClassLoadingUtils.getHelperClass(CoroutinesDebugHelper::class.java, context.evaluationContext)
            if (helperClass != null) {
                val method = DebuggerUtils.findMethod(helperClass, "getCoroutinesRunningOnCurrentThread", null)
                if (method != null) {
                    val array = context.evaluationContext.computeAndKeep {
                        context.invokeMethod(helperClass, method, listOf(debugProbesImpl.getObject())) as ArrayReference
                    }
                    return array.values.asSequence().map { (it as LongValue).value() }.toHashSet()
                }
            }
        } catch (e: Exception) {
            DebuggerUtilsImpl.logError(e) // for now log everything
        }
        return null
    }

    private fun continuationObjectFilter(
        suspendContext: SuspendContextImpl,
        defaultExecutionContext: DefaultExecutionContext
    ): ContinuationObjectFilter? {
        val frameProxy = suspendContext.getStackFrameProxyImpl() ?: return null
        val continuation = extractContinuation(frameProxy) ?: return null
        val baseContinuation = extractBaseContinuation(continuation, defaultExecutionContext) ?: return null
        return ContinuationObjectFilter(baseContinuation)
    }

    private fun extractContinuation(frameProxy: StackFrameProxyImpl): ObjectReference? {
        val suspendExitMode = frameProxy.location().getSuspendExitMode()
        return when (suspendExitMode) {
            SuspendExitMode.SUSPEND_LAMBDA -> frameProxy.thisVariableValue()
            // If the final call within a function body is a suspend call, and it's the only suspend call,
            // then tail call optimization is applied, and no state machine is generated, hence only completion variable is available.
            SuspendExitMode.SUSPEND_METHOD_PARAMETER -> frameProxy.continuationVariableValue() ?: frameProxy.completionVariableValue()
            else -> null
        }
    }

    override fun callerLocation(suspendContext: SuspendContextImpl): Location? {
        val frameProxy = suspendContext.getStackFrameProxyImpl() ?: return null
        val continuationObject = extractContinuation(frameProxy) ?: return null
        val executionContext = DefaultExecutionContext(suspendContext, frameProxy)
        val debugMetadata = DebugMetadata.instance(executionContext) ?: return null
        // At first, try to extract the completion field of the current BaseContinuationImpl instance,
        // if the completion field is null, then return the object itself and try to extract the StackTraceElement
        val completionObject = debugMetadata.baseContinuationImpl.getNextContinuation(continuationObject, executionContext) ?: continuationObject
        val stackTraceElement = debugMetadata.getStackTraceElement(completionObject, executionContext)?.stackTraceElement() ?: return null
        return DebuggerUtilsEx.findOrCreateLocation(suspendContext.debugProcess, stackTraceElement)
    }

    private fun extractBaseContinuation(
        continuation: ObjectReference,
        defaultExecutionContext: DefaultExecutionContext
    ): ObjectReference? {
        val baseContinuationImpl = BaseContinuationImplLight(defaultExecutionContext)
        var loopContinuation = continuation
        while (true) {
            val continuationMirror = baseContinuationImpl.mirror(loopContinuation, defaultExecutionContext) ?: return null
            val nextContinuation = continuationMirror.nextContinuation
            if (nextContinuation == null) {
                return continuationMirror.coroutineOwner
            }
            loopContinuation = nextContinuation
        }
    }

    private fun SuspendContextImpl.getStackFrameProxyImpl(): StackFrameProxyImpl? =
        activeExecutionStack?.threadProxy?.frame(0) ?: this.frameProxy

    private data class ContinuationIdFilter(val coroutinesRunningOnCurrentThread: Set<Long>) : ContinuationFilter {
        init {
            require(coroutinesRunningOnCurrentThread.isNotEmpty()) { "Coroutines set can not be empty" }
        }
        override fun canRunTo(nextContinuationFilter: ContinuationFilter): Boolean {
            return nextContinuationFilter is ContinuationIdFilter && 
                    coroutinesRunningOnCurrentThread.intersect(nextContinuationFilter.coroutinesRunningOnCurrentThread).isNotEmpty()
        }
    }

    // Is used when there is no debug information about unique Continuation ID (for example, for the old versions)
    private data class ContinuationObjectFilter(val reference: ObjectReference) : ContinuationFilter {
        override fun canRunTo(nextContinuationFilter: ContinuationFilter): Boolean = 
            this == nextContinuationFilter
    }
}
