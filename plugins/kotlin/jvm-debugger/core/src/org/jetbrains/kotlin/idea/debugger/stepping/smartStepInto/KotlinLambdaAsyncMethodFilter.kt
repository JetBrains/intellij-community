// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.DebugProcessImpl.StepOverCommand
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.ui.breakpoints.StepIntoBreakpoint
import com.intellij.debugger.ui.breakpoints.SteppingBreakpoint
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.util.Range
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.request.StepRequest
import org.jetbrains.kotlin.idea.debugger.base.util.DexDebugFacility
import org.jetbrains.kotlin.idea.debugger.base.util.safeAllLineLocations
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.base.util.safeThisObject
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.isGeneratedIrBackendLambdaMethodName
import org.jetbrains.kotlin.idea.debugger.core.isInvokeSuspendMethod

class KotlinLambdaAsyncMethodFilter(
    element: PsiElement?,
    callingExpressionLines: Range<Int>?,
    private val lambdaInfo: KotlinLambdaInfo,
    private val lambdaFilter: KotlinLambdaMethodFilter
) : MethodFilter {
    private var visitedLocations = 0
    private val methodFilter = if (element is PsiMethod) {
        BasicStepMethodFilter(element, lambdaInfo.callerMethodInfo.ordinal, callingExpressionLines)
    } else {
        KotlinMethodFilter(element, callingExpressionLines, lambdaInfo.callerMethodInfo)
    }

    override fun getCallingExpressionLines() = methodFilter.callingExpressionLines
    override fun locationMatches(process: DebugProcessImpl, location: Location): Boolean {
        return locationMatches(process, location, null)
    }

    override fun locationMatches(process: DebugProcessImpl, location: Location?, frameProxy: StackFrameProxyImpl?): Boolean {
        if (frameProxy == null || !methodFilter.locationMatches(process, location, frameProxy)) {
            return false
        }
        visitedLocations++

        val lambdaReference = frameProxy.getLambdaReference() ?: return false
        val lambdaMethod = lambdaReference.referenceType().getLambdaMethod() ?: return false
        val locationInLambda = lambdaMethod.safeAllLineLocations().firstOrNull()

        // We failed to get the location inside lambda (it can happen for SAM conversions in IR backend).
        // So we fall back to ordinal check.
        if (isInvokeSuspendMethod(lambdaMethod) || locationInLambda == null) {
            return visitedLocations == 1 + lambdaInfo.callerMethodInfo.ordinal
        }
        return lambdaFilter.locationMatches(process, locationInLambda)
    }

    private val isCallerMethodSuspend
        get() = lambdaInfo.callerMethodInfo.isSuspend

    private fun ReferenceType.getLambdaMethod(): Method? =
        methods().firstOrNull { it.isPublic && !it.isBridge }

    private val isAsyncSuspendLambda: Boolean
        get() = lambdaInfo.isSuspend && !isCallerMethodSuspend

    private val isSameCoroutineSuspendLambda: Boolean
        get() = lambdaInfo.isSuspend && isCallerMethodSuspend

    override fun onReached(context: SuspendContextImpl, hint: RequestHint?): Int {
        try {
            val breakpoint = createBreakpoint(context)
            if (breakpoint != null) {
                if (isSameCoroutineSuspendLambda) {
                    val filterThread = context.debugProcess.requestsManager.filterThread
                    if (filterThread != null) {
                        val breakpointManager = DebuggerManagerEx.getInstanceEx(context.debugProcess.project).breakpointManager
                        breakpointManager.applyThreadFilter(context.debugProcess, filterThread)
                    }
                }
                DebugProcessImpl.prepareAndSetSteppingBreakpoint(context, breakpoint, hint, !isCallerMethodSuspend)
                return RequestHint.RESUME
            }
        } catch (ignored: EvaluateException) {
        }
        return RequestHint.STOP
    }

    private fun createBreakpoint(context: SuspendContextImpl): SteppingBreakpoint? {
        val lambdaReference = context.frameProxy?.getLambdaReference() ?: return null
        if (isAsyncSuspendLambda) {
            val lambdaMethod = lambdaReference.referenceType().methods().single { it.name() == CREATE }
            return AsyncSuspendLambdaBreakpoint(context, lambdaReference, lambdaMethod)
        } else {
            val position = lambdaFilter.breakpointPosition ?: return null
            return KotlinLambdaInstanceBreakpoint(
                context,
                position,
                lambdaReference
            )
        }
    }

    private fun StackFrameProxyImpl.getLambdaReference(): ObjectReference? {
        if (DexDebugFacility.isDex(virtualMachine.virtualMachine)) {
            // We could fetch the lambda reference from the caller function arguments
            // using `argumentValues.getOrNull(lambdaInfo.parameterIndex)`. However, this call
            // results in an exception when debugging on Android. Instead, we can fetch the lambda
            // reference from visible variables. When the current function is called, the debugger
            // should be located on the first available line number of a function that calls the
            // lambda we are looking for. It means that the only visible variables are arguments
            // of this function.
            val lambdaArgumentVariable = visibleVariables().getOrNull(lambdaInfo.parameterIndex) ?: return null
            return getValue(lambdaArgumentVariable) as? ObjectReference
        }
        return argumentValues.getOrNull(lambdaInfo.parameterIndex) as? ObjectReference
    }

    private inner class AsyncSuspendLambdaBreakpoint(
        private val context: SuspendContextImpl,
        private val lambdaReference: ObjectReference,
        lambdaMethod: Method
    ) : StepIntoMethodBreakpoint(lambdaMethod.declaringType().name(), lambdaMethod.name(), lambdaMethod.signature(), context.debugProcess.project) {
        override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent): Boolean {
            super.processLocatableEvent(action, event).also { stopped ->
                if (!stopped) return false
                context.debugProcess.requestsManager.deleteRequest(this)
            }
            scheduleStepsToInvokeSuspend(action.suspendContext!!).prepareSteppingRequestsAndHints(action.suspendContext!!)
            return false
        }

        private fun scheduleStepsToInvokeSuspend(it: SuspendContextImpl): StepOverCommand =
            with(it.debugProcess) {
                object : DebugProcessImpl.StepOverCommand(it, false, null, StepRequest.STEP_MIN) {
                    override fun getHint(
                        suspendContext: SuspendContextImpl,
                        stepThread: ThreadReferenceProxyImpl,
                        parentHint: RequestHint?
                    ): RequestHint {
                        val hint: RequestHint =
                            object : RequestHint(stepThread, suspendContext, StepRequest.STEP_MIN, StepRequest.STEP_OVER, myMethodFilter, parentHint) {
                                override fun getNextStepDepth(context: SuspendContextImpl): Int {
                                    val location = context.location
                                    if (location != null && location.method().name() == CREATE) return StepRequest.STEP_OUT
                                    if (location != null && isInvokeSuspendMethod(location.method()) && location.method().declaringType().name() == lambdaReference.referenceType().name()) {
                                        return STOP
                                    }
                                    return StepRequest.STEP_INTO
                                }
                            }
                        hint.isIgnoreFilters = suspendContext.debugProcess.session.shouldIgnoreSteppingFilters()
                        return hint
                    }
                }
            }

        override fun evaluateCondition(context: EvaluationContextImpl, event: LocatableEvent): Boolean {
            if (!super.evaluateCondition(context, event)) return false
            return lambdaReference.checkLambdaBreakpoint(context, event.location())
        }
    }

    private inner class KotlinLambdaInstanceBreakpoint(
        private val context: SuspendContextImpl,
        pos: SourcePosition,
        private val lambdaReference: ObjectReference
    ) : StepIntoBreakpoint(context.debugProcess.project, pos, lambdaFilter) {

        override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean {
            thisLogger().debug("Hit the KotlinLambdaInstanceBreakpoint at ${context.location}")
            return super.processLocatableEvent(action, event).also { stopped ->
                if (stopped) context.debugProcess.requestsManager.deleteRequest(this)
            }
        }

        override fun evaluateCondition(context: EvaluationContextImpl, event: LocatableEvent): Boolean {
            if (!super.evaluateCondition(context, event)) return false
            return lambdaReference.checkLambdaBreakpoint(context, event.location())
        }
    }

    private fun isTargetLambdaName(name: String): Boolean {
        if (isAsyncSuspendLambda) return name == CREATE
        return lambdaFilter.isTargetLambdaName(name)
    }
    
    private fun ObjectReference.checkLambdaBreakpoint(context: EvaluationContextImpl, location: Location): Boolean {
        val thread = context.suspendContext.thread ?: return false
        val methodName = location.safeMethod()?.name() ?: return false
        if (!isTargetLambdaName(methodName)) {
            return false
        }

        val frameIndex = if (methodName.isGeneratedIrBackendLambdaMethodName()) 1 else 0
        return isTargetLambda(thread, frameIndex)
                // On ART SAM converted lambdas get an additional stack frame from r8 in their stack trace
                || DexDebugFacility.isDex(context.debugProcess) && isTargetLambda(thread, 2)
                // For lambdas passed to Java functions, the lambda could be additionally wrapped for type compatibility.
                // One of the previous frames (heuristically 3rd frame) should contain the original lambda.
                || isTargetLambda(thread, 3)
    }

    private fun ObjectReference.isTargetLambda(thread: ThreadReferenceProxyImpl, frameIndex: Int): Boolean {
        if (thread.frameCount() <= frameIndex) return false
        if (isSameCoroutineSuspendLambda) {
            // For suspend lambdas check its type + coroutine filter at the breakpoint
            return checkLambdaType(thread, frameIndex)
        }
        // For lambdas passed to Java functions, the lambda could be additionally wrapped for type compatibility.
        // One of the previous frames (heuristically 3rd frame) should contain the original lambda.
        return this.checkLambdaId(thread, frameIndex) || this.checkLambdaId(thread, 3)
    }

    private fun ObjectReference.checkLambdaId(thread: ThreadReferenceProxyImpl, frameIndex: Int): Boolean {
        val lambdaReference = thread.frame(frameIndex).safeThisObject()
        return lambdaReference != null && lambdaReference.uniqueID() == this.uniqueID()
    }

    private fun ObjectReference.checkLambdaType(thread: ThreadReferenceProxyImpl, frameIndex: Int): Boolean {
        val lambdaReference = thread.frame(frameIndex).safeThisObject()
        return lambdaReference != null && lambdaReference.referenceType() == this.referenceType()
    }

    companion object {
        private const val CREATE = "create"
    }
}