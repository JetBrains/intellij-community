// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.debugger.actions.AsyncStacksToggleAction
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.SuspendManagerUtil
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.execution.ui.layout.impl.RunnerContentUi
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.rt.debugger.ExceptionDebugHelper
import com.intellij.rt.debugger.coroutines.CoroutinesDebugHelper
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.base.util.safeLineNumber
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.core.StackFrameInterceptor
import org.jetbrains.kotlin.idea.debugger.core.stepping.CoroutineFilter
import org.jetbrains.kotlin.idea.debugger.coroutine.data.SuspendExitMode
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.SkipCoroutineStackFrameProxyImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.*
import org.jetbrains.kotlin.idea.debugger.coroutine.util.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


class CoroutineStackFrameInterceptor : StackFrameInterceptor {
    override fun createStackFrames(frame: StackFrameProxyImpl, debugProcess: DebugProcessImpl): List<XStackFrame>? {
        if (debugProcess.xdebugProcess?.session is XDebugSessionImpl
            && frame !is SkipCoroutineStackFrameProxyImpl
            && AsyncStacksToggleAction.isAsyncStacksEnabled(debugProcess.xdebugProcess?.session as XDebugSessionImpl)) {
            // skip -1 line in invokeSuspend and main
            val location = frame.safeLocation()
            if (location != null && location.safeLineNumber() < 0 &&
                (location.safeMethod()?.name() == "main" || location.isInvokeSuspend())) {
                return emptyList()
            }

            val suspendContextImpl = SuspendManagerUtil.getContextForEvaluation(debugProcess.suspendManager)
            val stackFrame = suspendContextImpl?.let {
                CoroutineFrameBuilder.coroutineExitFrame(frame, it)
            } ?: return null

            // only leave the first suspend frame
            if (!stackFrame.isFirstSuspendFrame) {
                return emptyList() // skip
            }

            if (Registry.`is`("debugger.kotlin.auto.show.coroutines.view")) {
                showCoroutinePanel(debugProcess)
            }

            val resumeWithFrame = stackFrame.threadPreCoroutineFrames.firstOrNull()

            if (threadAndContextSupportsEvaluation(suspendContextImpl, resumeWithFrame)) {
                val frameItemLists = CoroutineFrameBuilder.build(stackFrame, suspendContextImpl, withPreFrames = false)
                return listOf(stackFrame) + frameItemLists.frames.mapNotNull { it.createFrame(debugProcess) }
            }
            return listOf(stackFrame)
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

    override fun extractCoroutineFilter(suspendContext: SuspendContextImpl): CoroutineFilter? {
        val frameProxy = suspendContext.getStackFrameProxyImpl() ?: return null
        val defaultExecutionContext = DefaultExecutionContext(suspendContext, frameProxy)
        // First try to extract Continuation filter
        val continuationFilter = tryComputeContinuationFilter(frameProxy, defaultExecutionContext)
        if (continuationFilter != null) return continuationFilter
        // If continuation could not be extracted or the root continuation was not an instance of BaseContinuationImpl,
        // dump coroutines running on the current thread and compute [CoroutineIdFilter].
        val debugProbesImpl = DebugProbesImpl.instance(defaultExecutionContext)
        return if (debugProbesImpl != null && debugProbesImpl.isInstalled) {
            // first try the helper, it is the fastest way, then try the mirror
            val currentCoroutines = getCoroutinesRunningOnCurrentThreadFromHelper(defaultExecutionContext, debugProbesImpl)
                ?: debugProbesImpl.getCoroutinesRunningOnCurrentThread(defaultExecutionContext)

            if (currentCoroutines.isNotEmpty()) CoroutineIdFilter(currentCoroutines)
            else null
        } else {
            //TODO: IDEA-341142 show nice notification about this
            thisLogger().warn("[coroutine filter]: kotlinx-coroutines debug agent was not enabled, DebugProbesImpl class is not found.")
            null
        }
    }

    /**
     * This function computes [CoroutineFilter] given an instance of the current continuation
     * (passed as an argument to the suspend function, or instance of suspend lambda).
     * To distinguish coroutines, we need to go up the continuation stack and extract the continuation corresponding the root suspending frame.
     *
     * 1. Tries to extract continuation id (if the root continuation is an instance of CoroutineOwner) and return [ContinuationIdFilter].
     * 2. If continuation was extracted though it was not an instance of CoroutineOwner (if coroutines debug agent was not enabled), return [ContinuationObjectFilter].
     * 3. If computing the continuation filter fails, create [ContinuationObjectFilter].
     */
    private fun tryComputeContinuationFilter(
        frameProxy: StackFrameProxyImpl,
        defaultExecutionContext: DefaultExecutionContext
    ): CoroutineFilter? {
        // if continuation cannot be extracted, fall to CoroutineIdFilter
        val currentContinuation = extractContinuation(frameProxy) ?: return null
        // First try to get a ContinuationFilter from helper
        getContinuationFilterFromHelper(currentContinuation, defaultExecutionContext)?.let { return it }
        // If helper class failed
        val debugProbesImpl = DebugProbesImpl.instance(defaultExecutionContext)
        if (debugProbesImpl != null && debugProbesImpl.isInstalled) {
            extractContinuationId(currentContinuation, defaultExecutionContext)?.let { return it }
        }
        return extractBaseContinuation(currentContinuation, defaultExecutionContext)
    }

    private fun extractBaseContinuation(
        continuation: ObjectReference,
        defaultExecutionContext: DefaultExecutionContext
    ): CoroutineFilter? {
        val baseContinuationImpl = CoroutineStackFrameLight(defaultExecutionContext)
        var loopContinuation = continuation
        while (true) {
            val continuationMirror = baseContinuationImpl.mirror(loopContinuation, defaultExecutionContext)
            if (continuationMirror == null) {
                // for now, if continuation is not an instance of BaseContinuationImpl, fall to CoroutineIdFilter
                thisLogger().warn("[coroutine filter]: extracted completion field was not an instance of BaseContinuationImpl, ${defaultExecutionContext.frameProxy?.location()}")
                return null
            }
            val nextContinuation = continuationMirror.nextContinuation
            if (nextContinuation == null) {
                return ContinuationObjectFilter(continuationMirror.that)
            }
            loopContinuation = nextContinuation
        }
    }

    private fun extractContinuationId(
        continuation: ObjectReference,
        defaultExecutionContext: DefaultExecutionContext
    ): CoroutineFilter? {
        val baseContinuationImpl = CoroutineStackFrameLight(defaultExecutionContext)
        var loopContinuation = continuation
        while (true) {
            val continuationMirror = baseContinuationImpl.mirror(loopContinuation, defaultExecutionContext)
            if (continuationMirror == null) {
                // for now, if continuation is not an instance of BaseContinuationImpl, fall to CoroutineIdFilter
                thisLogger().warn("[coroutine filter]: extracted completion field was not an instance of BaseContinuationImpl, ${defaultExecutionContext.frameProxy?.location()}")
                return null
            }
            if (continuationMirror.coroutineOwner != null) {
                val coroutineOwner = DebugProbesImplCoroutineOwner(null, defaultExecutionContext)
                val coroutineOwnerMirror = coroutineOwner.mirror(continuationMirror.coroutineOwner, defaultExecutionContext)
                coroutineOwnerMirror?.coroutineInfo?.sequenceNumber?.let { return CoroutineIdFilter(setOf(it)) }
            }
            val nextContinuation = continuationMirror.nextContinuation
            if (nextContinuation == null) {
                return ContinuationObjectFilter(continuationMirror.that)
            }
            loopContinuation = nextContinuation
        }
    }

    private fun getContinuationFilterFromHelper(currentContinuation: ObjectReference, context: DefaultExecutionContext): CoroutineFilter? {
        val continuationIdValue = callMethodFromHelper(CoroutinesDebugHelper::class.java, context, "tryGetContinuationId", listOf(currentContinuation))
        (continuationIdValue as? LongValue)?.value()?.let { if (it != -1L) return CoroutineIdFilter(setOf(it)) }
        thisLogger().warn("[coroutine filter]: Could not extract continuation ID, location = ${context.frameProxy?.location()}")
        val rootContinuation = callMethodFromHelper(CoroutinesDebugHelper::class.java, context, "getRootContinuation", listOf(currentContinuation))
        if (rootContinuation == null) thisLogger().warn("[coroutine filter]: Could not extract continuation instance")
        return rootContinuation?.let { ContinuationObjectFilter(it as ObjectReference) }
    }

    private fun getCoroutinesRunningOnCurrentThreadFromHelper(
        context: DefaultExecutionContext,
        debugProbesImpl: DebugProbesImpl
    ): Set<Long>? {
        val threadReferenceProxyImpl = context.suspendContext.thread ?: return null
        val args = listOf(debugProbesImpl.getObject(), threadReferenceProxyImpl.threadReference)
        val result = callMethodFromHelper(CoroutinesDebugHelper::class.java, context, "getCoroutinesRunningOnCurrentThread", args)
        result ?: return null
        return (result as ArrayReference).values.asSequence().map { (it as LongValue).value() }.toHashSet()
    }

    private fun callMethodFromHelper(helperClass: Class<*>, context: DefaultExecutionContext, methodName: String, args: List<Value?>): Value? {
        try {
            return DebuggerUtilsImpl.invokeHelperMethod(context.evaluationContext, helperClass, methodName, args)
        } catch (e: Exception) {
            if (e is EvaluateException && e.exceptionFromTargetVM != null) {
                var exceptionStack = DebuggerUtilsImpl.getExceptionText(context.evaluationContext, e.exceptionFromTargetVM!!)
                if (exceptionStack != null) {
                    // drop user frames
                    val currentStackDepth = (DebuggerUtilsImpl.invokeHelperMethod(
                        context.evaluationContext,
                        ExceptionDebugHelper::class.java,
                        "getCurrentThreadStackDepth",
                        emptyList()
                    ) as IntegerValue).value()
                    val lines = exceptionStack.lines()
                    if (lines.size > currentStackDepth) {
                        exceptionStack = lines.subList(0, lines.size - currentStackDepth + 1).joinToString(separator = "\n")
                    }
                    DebuggerUtilsImpl.logError(e.message, e, exceptionStack)
                    return null
                }
            }
            DebuggerUtilsImpl.logError(e) // for now log everything
        }
        return null
    }

    private fun extractContinuation(frameProxy: StackFrameProxyImpl): ObjectReference? {
        val suspendExitMode = frameProxy.location().getSuspendExitMode()
        return when (suspendExitMode) {
            SuspendExitMode.SUSPEND_LAMBDA -> {
                frameProxy.thisVariableValue()?.let { return it }
                // Extract the previous stack frame at BaseContinuationImpl#resumeWith where invokeSuspend is invoked
                // and extract `this` reference to the current SuspendLambda there.
                // This is a WA for this problem: IDEA-349851, KT-67136.
                val prevStackFrame = frameProxy.threadProxy().frames().getOrNull(frameProxy.frameIndex + 1)
                if (prevStackFrame == null) {
                    thisLogger().error("[coroutine filtering]: Could not extract the previous stack frame for the frame ${frameProxy.stackFrame}:\n" +
                                               "thread = ${frameProxy.threadProxy().name()} \n" +
                                               "frames = ${frameProxy.threadProxy().frames()}")
                    return null
                }
                prevStackFrame.thisObject()
            }
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

    private fun SuspendContextImpl.getStackFrameProxyImpl(): StackFrameProxyImpl? =
        activeExecutionStack?.threadProxy?.frame(0) ?: this.frameProxy

    /**
     * The coroutine filter which defines a coroutine by the set of ids of coroutines running on the current thread.
     */
    private data class CoroutineIdFilter(val coroutinesRunningOnCurrentThread: Set<Long>) : CoroutineFilter {
        init {
            require(coroutinesRunningOnCurrentThread.isNotEmpty()) { "Coroutines set can not be empty" }
        }
        override fun canRunTo(nextCoroutineFilter: CoroutineFilter): Boolean =
            (nextCoroutineFilter is CoroutineIdFilter && coroutinesRunningOnCurrentThread.intersect(nextCoroutineFilter.coroutinesRunningOnCurrentThread).isNotEmpty())
    }

    /**
     * The coroutine filter which defines a coroutine by the instance of the Continuation corresponding to the root coroutine frame.
     */
    private data class ContinuationObjectFilter(val reference: ObjectReference) : CoroutineFilter {
        override fun canRunTo(nextCoroutineFilter: CoroutineFilter): Boolean =
            this == nextCoroutineFilter
    }
}