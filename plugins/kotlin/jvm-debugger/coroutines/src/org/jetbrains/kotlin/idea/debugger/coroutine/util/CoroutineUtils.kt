// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.util

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.xdebugger.XSourcePosition
import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.base.util.*
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.data.SuspendExitMode
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

const val CREATION_STACK_TRACE_SEPARATOR = "\b\b\b" // the "\b\b\b" is used as creation stacktrace separator in kotlinx.coroutines
const val CREATION_CLASS_NAME = "_COROUTINE._CREATION"

fun Method.isInvokeSuspend(): Boolean =
    name() == KotlinDebuggerConstants.INVOKE_SUSPEND_METHOD_NAME && signature() == "(Ljava/lang/Object;)Ljava/lang/Object;"

fun Method.isInvoke(): Boolean =
    name() == "invoke" && signature().contains("Ljava/lang/Object;)Ljava/lang/Object;")

fun Method.isSuspendLambda() =
    isInvokeSuspend() && declaringType().isSuspendLambda()

fun Method.hasContinuationParameter() =
    signature().contains("Lkotlin/coroutines/Continuation;)")

fun StackFrameProxyImpl.getSuspendExitMode(): SuspendExitMode =
    safeLocation()?.getSuspendExitMode() ?: SuspendExitMode.NONE

fun Location.getSuspendExitMode(): SuspendExitMode {
    val method = safeMethod() ?: return SuspendExitMode.NONE
    return when {
        method.isSuspendLambda() -> SuspendExitMode.SUSPEND_LAMBDA
        method.hasContinuationParameter() -> SuspendExitMode.SUSPEND_METHOD_PARAMETER
        (method.isInvokeSuspend() || method.isInvoke()) && safeCoroutineExitPointLineNumber() -> SuspendExitMode.SUSPEND_METHOD
        else -> return SuspendExitMode.NONE
    }
}

internal fun extractContinuation(frameProxy: StackFrameProxyImpl): ObjectReference? {
    val suspendExitMode = frameProxy.location().getSuspendExitMode()
    return when (suspendExitMode) {
        SuspendExitMode.SUSPEND_LAMBDA -> {
            frameProxy.thisVariableValue()?.let { return it }
            // Extract the previous stack frame at BaseContinuationImpl#resumeWith where invokeSuspend is invoked
            // and extract `this` reference to the current SuspendLambda there.
            // This is a WA for this problem: IDEA-349851, KT-67136.
            val prevStackFrame = frameProxy.threadProxy().frames().getOrNull(frameProxy.frameIndex + 1)
            if (prevStackFrame == null) {
                logger.thisLogger().error("[coroutine filtering]: Could not extract the previous stack frame for the frame ${frameProxy.stackFrame}:\n" +
                                           "thread = ${frameProxy.threadProxy().name()} \n" +
                                           "frames = ${frameProxy.threadProxy().frames()}")
                return null
            }
            prevStackFrame.thisObject()
        }
        SuspendExitMode.SUSPEND_METHOD_PARAMETER -> frameProxy.continuationVariableValue()
        else -> null
    }
}

fun Location.safeCoroutineExitPointLineNumber() =
  (wrapIllegalArgumentException { DebuggerUtilsEx.getLineNumber(this, false) } ?: -2) == -1

fun Type.isBaseContinuationImpl() =
    isSubtype("kotlin.coroutines.jvm.internal.BaseContinuationImpl")

fun Type.isCoroutineScope() =
    isSubtype("kotlinx.coroutines.CoroutineScope")

fun Type.isSubTypeOrSame(className: String) =
    name() == className || isSubtype(className)

fun ReferenceType.isSuspendLambda() =
    SUSPEND_LAMBDA_CLASSES.any { isSubtype(it) }

fun Location.isInvokeSuspend() =
    safeMethod()?.isInvokeSuspend() ?: false

fun Location.isInvokeSuspendWithNegativeLineNumber() =
    isInvokeSuspend() && safeLineNumber() < 0

fun StackFrameProxyImpl.variableValue(variableName: String): ObjectReference? {
    val continuationVariable = safeVisibleVariableByName(variableName) ?: return null
    return getValue(continuationVariable) as? ObjectReference ?: return null
}

fun StackFrameProxyImpl.completionVariableValue(): ObjectReference? =
    variableValue("\$completion")

fun StackFrameProxyImpl.continuationVariableValue(): ObjectReference? =
    variableValue("\$continuation")

fun StackFrameProxyImpl.thisVariableValue(): ObjectReference? =
    this.thisObject()

private fun Method.isGetCoroutineSuspended() =
    signature() == "()Ljava/lang/Object;" && name() == "getCOROUTINE_SUSPENDED" && declaringType().name() == "kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsKt"

fun hasGetCoroutineSuspended(frames: List<StackFrameProxyImpl>) =
    frames.indexOfFirst { it.safeLocation()?.safeMethod()?.isGetCoroutineSuspended() == true }

fun StackTraceElement.isCreationSeparatorFrame() =
    className.startsWith(CREATION_STACK_TRACE_SEPARATOR) ||
    className == CREATION_CLASS_NAME

fun Location.findPosition(debugProcess: DebugProcessImpl): XSourcePosition? = ReadAction.nonBlocking<SourcePosition> {
    debugProcess.positionManager.getSourcePosition(this)
}.executeSynchronously()?.toXSourcePosition()

fun SourcePosition?.toXSourcePosition(): XSourcePosition? = ReadAction.nonBlocking<XSourcePosition> {
    DebuggerUtilsEx.toXSourcePosition(this@toXSourcePosition)
}.executeSynchronously()

fun SuspendContextImpl.executionContext(): DefaultExecutionContext {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return DefaultExecutionContext(this, this.frameProxy)
}

fun ThreadReferenceProxyImpl.supportsEvaluation(): Boolean =
    threadReference?.isSuspended ?: false

private fun SuspendContextImpl.supportsEvaluation() =
    debugProcess.isEvaluationPossible(this) || isUnitTestMode()

fun threadAndContextSupportsEvaluation(suspendContext: SuspendContextImpl, frameProxy: StackFrameProxyImpl?): Boolean {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    return suspendContext.supportsEvaluation() && frameProxy?.threadProxy()?.supportsEvaluation() ?: false
}

fun Location.sameLineAndMethod(location: Location?): Boolean =
    location != null && location.safeMethod() == safeMethod() && location.safeLineNumber() == safeLineNumber()

fun Location.isFilterFromTop(location: Location?): Boolean =
    isInvokeSuspendWithNegativeLineNumber() || sameLineAndMethod(location) || location?.safeMethod() == safeMethod()

fun Location.isFilterFromBottom(location: Location?): Boolean =
    sameLineAndMethod(location)
