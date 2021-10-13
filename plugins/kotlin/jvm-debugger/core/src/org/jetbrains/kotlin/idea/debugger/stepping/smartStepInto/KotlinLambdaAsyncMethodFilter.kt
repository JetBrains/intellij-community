// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.RequestHint
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.ui.breakpoints.StepIntoBreakpoint
import com.intellij.openapi.project.Project
import com.intellij.util.Range
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.event.LocatableEvent
import org.jetbrains.kotlin.idea.debugger.DebuggerUtils.isGeneratedIrBackendLambdaMethodName
import org.jetbrains.kotlin.idea.debugger.safeAllLineLocations
import org.jetbrains.kotlin.idea.debugger.safeMethod
import org.jetbrains.kotlin.idea.debugger.safeThisObject
import org.jetbrains.kotlin.psi.KtDeclaration

class KotlinLambdaAsyncMethodFilter(
    declaration: KtDeclaration?,
    callingExpressionLines: Range<Int>?,
    private val lambdaInfo: KotlinLambdaInfo,
    private val lambdaFilter: KotlinLambdaMethodFilter
) : KotlinMethodFilter(declaration, callingExpressionLines, lambdaInfo.callerMethodInfo) {
    private var visitedLocations = 0

    override fun locationMatches(process: DebugProcessImpl, location: Location?, frameProxy: StackFrameProxyImpl?): Boolean {
        if (frameProxy == null || !super.locationMatches(process, location, frameProxy)) {
            return false
        }
        visitedLocations++

        val lambdaReference = frameProxy.getLambdaReference() ?: return false
        val lambdaMethod = lambdaReference.referenceType().getLambdaMethod() ?: return false
        val locationInLambda = lambdaMethod.safeAllLineLocations().firstOrNull()

        // We failed to get the location inside lambda (it can happen for SAM conversions in IR backend).
        // So we fall back to ordinal check.
        if (locationInLambda == null) {
            return visitedLocations == lambdaInfo.callerMethodOrdinal
        }
        return lambdaFilter.locationMatches(process, locationInLambda)
    }

    private fun ReferenceType.getLambdaMethod(): Method? =
        methods().firstOrNull { it.isPublic && !it.isBridge }

    override fun onReached(context: SuspendContextImpl, hint: RequestHint?): Int {
        try {
            val breakpoint = createBreakpoint(context)
            if (breakpoint != null) {
                DebugProcessImpl.prepareAndSetSteppingBreakpoint(context, breakpoint, hint, true)
                return RequestHint.RESUME
            }
        } catch (ignored: EvaluateException) {
        }
        return RequestHint.STOP
    }

    private fun createBreakpoint(context: SuspendContextImpl): KotlinLambdaInstanceBreakpoint? {
        val lambdaReference = context.frameProxy?.getLambdaReference() ?: return null
        val position = lambdaFilter.breakpointPosition ?: return null
        return KotlinLambdaInstanceBreakpoint(
            context.debugProcess.project,
            position,
            lambdaReference.uniqueID(),
            lambdaFilter
        )
    }

    private fun StackFrameProxyImpl.getLambdaReference(): ObjectReference? =
        argumentValues.getOrNull(lambdaInfo.parameterIndex) as? ObjectReference

    private class KotlinLambdaInstanceBreakpoint(
        project: Project,
        pos: SourcePosition,
        private val lambdaId: Long,
        private val lambdaFilter: KotlinLambdaMethodFilter
    ) : StepIntoBreakpoint(project, pos, lambdaFilter) {
        override fun evaluateCondition(context: EvaluationContextImpl, event: LocatableEvent): Boolean {
            if (!super.evaluateCondition(context, event)) {
                return false
            }

            val thread = context.suspendContext.thread ?: return false
            val methodName = event.location().safeMethod()?.name() ?: return false
            if (!lambdaFilter.isTargetLambdaName(methodName)) {
                return false
            }

            val frameIndex = if (methodName.isGeneratedIrBackendLambdaMethodName()) 1 else 0
            val lambdaReference = thread.frame(frameIndex).safeThisObject()
            return lambdaReference != null && lambdaReference.uniqueID() == lambdaId
        }
    }
}
