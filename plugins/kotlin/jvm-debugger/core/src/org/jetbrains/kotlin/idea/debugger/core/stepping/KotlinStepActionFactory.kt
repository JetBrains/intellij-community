// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.statistics.Engine
import com.intellij.openapi.diagnostic.Logger
import com.sun.jdi.request.StepRequest
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerCoreBundle.message
import org.jetbrains.kotlin.idea.debugger.core.StackFrameInterceptor
import org.jetbrains.kotlin.idea.debugger.core.getLocationOfCoroutineSuspendReturn
import org.jetbrains.kotlin.idea.debugger.core.isInSuspendMethod
import org.jetbrains.kotlin.idea.debugger.core.stepping.CoroutineJobInfo.Companion.extractJobInfo

object KotlinStepActionFactory {
    private val LOG = Logger.getInstance(KotlinStepActionFactory::class.java)

    fun createKotlinStepOverCommand(
        debugProcess: DebugProcessImpl,
        suspendContext: SuspendContextImpl?,
        ignoreBreakpoints: Boolean,
        methodFilter: KotlinMethodFilter,
        stepSize: Int
    ): DebugProcessImpl.StepOverCommand {
        return with(debugProcess) {
            object : DebugProcessImpl.StepOverCommand(suspendContext, ignoreBreakpoints, methodFilter, stepSize) {
                override fun getStatusText(): String = message("stepping.over.inline")

                override fun getThreadFilterFromContext(suspendContext: SuspendContextImpl): LightOrRealThreadInfo? {
                    return getThreadFilterFromContextForStepping(suspendContext) ?: super.getThreadFilterFromContext(suspendContext)
                }

                override fun contextAction(suspendContext: SuspendContextImpl) {
                    if (suspendContext.location?.let { isInSuspendMethod(it) } == true) {
                        applyThreadFilter(getThreadFilterFromContext(suspendContext))
                        CoroutineBreakpointFacility.installResumeBreakpointInCurrentMethod(suspendContext)
                    }
                    super.contextAction(suspendContext)
                }

                override fun getHint(
                    suspendContext: SuspendContextImpl,
                    stepThread: ThreadReferenceProxyImpl,
                    parentHint: RequestHint?
                ): RequestHint {
                    val hint = KotlinStepOverRequestHint(stepThread, suspendContext, methodFilter, parentHint, stepSize)
                    hint.isResetIgnoreFilters = !debugProcess.session.shouldIgnoreSteppingFilters()
                    hint.isRestoreBreakpoints = ignoreBreakpoints
                    try {
                        debugProcess.session.setIgnoreStepFiltersFlag(stepThread.frameCount())
                    } catch (e: EvaluateException) {
                        LOG.info(e)
                    }
                    return hint
                }

                override fun getEngine() = Engine.KOTLIN
            }
        }
    }

    fun createKotlinStepIntoCommand(
        debugProcess: DebugProcessImpl,
        suspendContext: SuspendContextImpl?,
        ignoreBreakpoints: Boolean,
        methodFilter: MethodFilter?
    ): DebugProcessImpl.StepIntoCommand {
        return with(debugProcess) {
            object : DebugProcessImpl.StepIntoCommand(suspendContext, ignoreBreakpoints, methodFilter, StepRequest.STEP_LINE) {
                override fun getHint(
                    suspendContext: SuspendContextImpl,
                    stepThread: ThreadReferenceProxyImpl,
                    parentHint: RequestHint?
                ): RequestHint {
                    val hint = KotlinStepIntoRequestHint(stepThread, suspendContext, methodFilter, parentHint)
                    hint.isResetIgnoreFilters = myMethodFilter != null && !debugProcess.session.shouldIgnoreSteppingFilters()
                    return hint
                }

                override fun getThreadFilterFromContext(suspendContext: SuspendContextImpl): LightOrRealThreadInfo? {
                    return getThreadFilterFromContextForStepping(suspendContext) ?: super.getThreadFilterFromContext(suspendContext)
                }

                override fun getEngine() = Engine.KOTLIN
            }
        }
    }

    fun createStepIntoCommand(
        debugProcess: DebugProcessImpl,
        suspendContext: SuspendContextImpl?,
        ignoreFilters: Boolean,
        methodFilter: MethodFilter?,
        stepSize: Int
    ): DebugProcessImpl.StepIntoCommand {
        return with(debugProcess) {
            object : DebugProcessImpl.StepIntoCommand(suspendContext, ignoreFilters, methodFilter, stepSize) {
                override fun getHint(
                    suspendContext: SuspendContextImpl,
                    stepThread: ThreadReferenceProxyImpl,
                    parentHint: RequestHint?
                ): RequestHint {
                    val hint: RequestHint =
                        KotlinRequestHint(stepThread, suspendContext, stepSize, StepRequest.STEP_INTO, methodFilter, parentHint)
                    hint.isResetIgnoreFilters = myMethodFilter != null && !debugProcess.session.shouldIgnoreSteppingFilters()
                    return hint
                }

                override fun getEngine() = Engine.KOTLIN
            }
        }
    }

    fun createStepOutCommand(
        debugProcess: DebugProcessImpl,
        suspendContext: SuspendContextImpl?,
        filter: MethodFilter? = null,
    ): DebugProcessImpl.StepOutCommand {
        return with(debugProcess) {
            object : DebugProcessImpl.StepOutCommand(suspendContext, StepRequest.STEP_LINE, filter) {
                override fun contextAction(suspendContext: SuspendContextImpl) {
                    if (suspendContext.location?.let { isInSuspendMethod(it) } == true) {
                        applyThreadFilter(getThreadFilterFromContext(suspendContext))
                        CoroutineBreakpointFacility.installResumeBreakpointInCallerMethod(suspendContext)
                    }
                    super.contextAction(suspendContext)
                }

                override fun getHint(
                    suspendContext: SuspendContextImpl,
                    stepThread: ThreadReferenceProxyImpl,
                    parentHint: RequestHint?
                ): RequestHint {
                    val resumeLocation = StackFrameInterceptor.instance?.callerLocation(suspendContext)
                    val suspendReturnLocation = if (suspendContext.location?.let { isInSuspendMethod(it) } == true) {
                        getLocationOfCoroutineSuspendReturn(resumeLocation)
                    } else null
                    val hint: RequestHint =
                        KotlinStepOutRequestHint(suspendReturnLocation, stepThread, suspendContext, StepRequest.STEP_MIN, StepRequest.STEP_OUT, filter, parentHint)
                    hint.isIgnoreFilters = debugProcess.session.shouldIgnoreSteppingFilters()
                    return hint
                }

                override fun getThreadFilterFromContext(suspendContext: SuspendContextImpl): LightOrRealThreadInfo? {
                    return getThreadFilterFromContextForStepping(suspendContext) ?: super.getThreadFilterFromContext(suspendContext)
                }

                override fun getEngine() = Engine.KOTLIN
            }
        }
    }

    private fun getThreadFilterFromContextForStepping(suspendContext: SuspendContextImpl): LightOrRealThreadInfo? {
        // for now use coroutine filtering only in suspend functions
        val location = suspendContext.location
        if (location != null && isInSuspendMethod(location)) {
            return extractJobInfo(suspendContext)
        }
        return null
    }
}
