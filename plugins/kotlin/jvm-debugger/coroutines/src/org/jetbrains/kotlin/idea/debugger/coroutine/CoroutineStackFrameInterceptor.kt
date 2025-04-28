// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.debugger.engine.*
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.impl.HelperClassNotAvailableException
import com.intellij.debugger.impl.MethodNotFoundException
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.rt.debugger.coroutines.CoroutinesDebugHelper
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.base.util.safeLineNumber
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerCoreBundle
import org.jetbrains.kotlin.idea.debugger.core.StackFrameInterceptor
import org.jetbrains.kotlin.idea.debugger.core.stepping.CoroutineFilter
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.SkipCoroutineStackFrameProxyImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.CoroutineStackFrameLight
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.DebugMetadata
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.DebugProbesImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.DebugProbesImplCoroutineOwner
import org.jetbrains.kotlin.idea.debugger.coroutine.util.*


private class CoroutineStackFrameInterceptor : StackFrameInterceptor {
    override fun createStackFrames(frame: StackFrameProxyImpl, debugProcess: DebugProcessImpl): List<XStackFrame>? {
        DebuggerManagerThreadImpl.assertIsManagerThread()
        if (debugProcess.xdebugProcess?.session !is XDebugSessionImpl
            || frame is SkipCoroutineStackFrameProxyImpl
            || !AsyncStacksUtils.isAsyncStacksEnabled(debugProcess.xdebugProcess?.session as XDebugSessionImpl)
        ) {
            return null
        }
        // skip -1 line in invokeSuspend and main
        val location = frame.safeLocation()
        if (location != null && location.safeLineNumber() < 0 &&
            (location.safeMethod()?.name() == "main" || location.isInvokeSuspend())) {
            return emptyList()
        }

        val suspendContext = SuspendManagerUtil.getContextForEvaluation(debugProcess.suspendManager) ?: return null

        val isSuspendFrame = extractContinuation(frame) != null

        if (!isSuspendFrame) return null

        // only get the information for the first suspend frame
        if (anySuspendFramesBefore(frame)) {
            return emptyList() // skip
        }

        val stackFrame = CoroutineFrameBuilder.coroutineExitFrame(frame, suspendContext) ?: return null

        if (Registry.`is`("debugger.kotlin.auto.show.coroutines.view")) {
            showOrHideCoroutinePanel(debugProcess, true)
        }

        if (!threadAndContextSupportsEvaluation(suspendContext, frame)) {
            return listOf(stackFrame)
        }
        val frameItemLists = CoroutineFrameBuilder.build(stackFrame, suspendContext, withPreFrames = false)
        return listOf(stackFrame) + frameItemLists.frames.mapNotNull { it.createFrame(debugProcess) }
    }

    private fun anySuspendFramesBefore(frame: StackFrameProxyImpl): Boolean {
        val frames = frame.threadProxy().frames()
        val frameIndex = frames.indexOf(frame)
        return frames.subList(0, frameIndex).any { extractContinuation(it) != null  }
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
        val currentContinuation = extractContinuationOrCompletion(frameProxy) ?: return null
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

    override fun callerLocation(suspendContext: SuspendContextImpl): Location? {
        val frameProxy = suspendContext.getStackFrameProxyImpl() ?: return null
        val continuationObject = extractContinuationOrCompletion(frameProxy) ?: return null
        val executionContext = DefaultExecutionContext(suspendContext, frameProxy)
        val debugMetadata = DebugMetadata.instance(executionContext) ?: return null
        val callerFrame = callMethodFromHelper(CoroutinesDebugHelper::class.java, executionContext, "getCallerFrame", listOf(continuationObject))?: return null
        val stackTraceElement = debugMetadata.getStackTraceElement(callerFrame as ObjectReference, executionContext)?.stackTraceElement() ?: return null
        return stackTraceElement.let { DebuggerUtilsEx.findOrCreateLocation(suspendContext.virtualMachineProxy.virtualMachine, it) }
    }

    private fun SuspendContextImpl.getStackFrameProxyImpl(): StackFrameProxyImpl? =
        activeExecutionStack?.threadProxy?.frame(0) ?: this.frameProxy

    // If the final call within a function body is a suspend call, and it's the only suspend call,
    // then tail call optimization is applied, and no state machine is generated, hence only completion variable is available.
    private fun extractContinuationOrCompletion(frameProxy: StackFrameProxyImpl): ObjectReference? =
        extractContinuation(frameProxy) ?: frameProxy.completionVariableValue()

    /**
     * The coroutine filter which defines a coroutine by the set of ids of coroutines running on the current thread.
     */
    private data class CoroutineIdFilter(val coroutinesRunningOnCurrentThread: Set<Long>) : CoroutineFilter {
        init {
            require(coroutinesRunningOnCurrentThread.isNotEmpty()) { "Coroutines set can not be empty" }
        }
        override fun canRunTo(nextCoroutineFilter: CoroutineFilter): Boolean =
            (nextCoroutineFilter is CoroutineIdFilter && coroutinesRunningOnCurrentThread.intersect(nextCoroutineFilter.coroutinesRunningOnCurrentThread).isNotEmpty())

        override val coroutineFilterName: String get() {
            coroutinesRunningOnCurrentThread.singleOrNull()?.let {
                return KotlinDebuggerCoreBundle.message("stepping.filter.coroutine.name", "#$it")
            }
            val ids = coroutinesRunningOnCurrentThread.toList().sortedBy { it }.joinToString(", ") { "#$it" }
            return KotlinDebuggerCoreBundle.message("stepping.filter.several.coroutines.name", ids)
        }
    }

    /**
     * The coroutine filter which defines a coroutine by the instance of the Continuation corresponding to the root coroutine frame.
     */
    private data class ContinuationObjectFilter(val reference: ObjectReference) : CoroutineFilter {
        override fun canRunTo(nextCoroutineFilter: CoroutineFilter): Boolean =
            this == nextCoroutineFilter

        override val coroutineFilterName: String get() = reference.toString()
    }
}

internal fun callMethodFromHelper(
    helperClass: Class<*>, context: DefaultExecutionContext, methodName: String, args: List<Value?>,
    vararg additionalClassesToLoad: String
): Value? {
    try {
        return DebuggerUtilsImpl.invokeHelperMethod(context.evaluationContext, helperClass, methodName, args, true, *additionalClassesToLoad)
    } catch (e: HelperClassNotAvailableException) {
        fileLogger().warn(e)
    } catch (e: MethodNotFoundException) {
        fileLogger().warn(e)
    } catch (e: Exception) {
        val helperExceptionStackTrace = MethodInvokeUtils.getHelperExceptionStackTrace(context.evaluationContext, e)
        DebuggerUtilsImpl.logError("Exception from helper: ${e.message}", e,
                                   *listOfNotNull(helperExceptionStackTrace).toTypedArray()) // log helper exception if available
    }
    return null
}
