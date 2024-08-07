// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.debugger.PositionManager
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.MethodFilter
import com.intellij.debugger.engine.RequestHint
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.JvmSteppingCommandProvider
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.Range
import com.intellij.xdebugger.XSourcePosition
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.StackFrame
import com.sun.jdi.request.StepRequest
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.debugger.base.util.safeAllLineLocations
import org.jetbrains.kotlin.idea.debugger.base.util.safeLineNumber
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.base.util.safeStackFrame
import org.jetbrains.kotlin.idea.debugger.base.util.safeThreadProxy
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.getBorders
import org.jetbrains.kotlin.idea.debugger.core.findElementAtLine
import org.jetbrains.kotlin.idea.debugger.core.getInlineFunctionAndArgumentVariablesToBordersMap
import org.jetbrains.kotlin.idea.debugger.core.stackFrame.computeKotlinStackFrameInfos
import org.jetbrains.kotlin.idea.debugger.core.stepping.filter.KotlinStepOverFilter
import org.jetbrains.kotlin.idea.debugger.core.stepping.filter.KotlinStepOverParamDefaultImplsMethodFilter
import org.jetbrains.kotlin.idea.debugger.core.stepping.filter.LocationToken
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class KotlinSteppingCommandProvider : JvmSteppingCommandProvider() {
    override fun getStepOverCommand(
        suspendContext: SuspendContextImpl?,
        ignoreBreakpoints: Boolean,
        stepSize: Int
    ): DebugProcessImpl.ResumeCommand? {
        if (suspendContext == null || suspendContext.isResumed) return null
        val sourcePosition = suspendContext.getSourcePosition() ?: return null
        if (sourcePosition.file !is KtFile) return null
        return getStepOverCommand(suspendContext, ignoreBreakpoints, sourcePosition)
    }

    override fun getStepIntoCommand(
        suspendContext: SuspendContextImpl?,
        ignoreFilters: Boolean,
        smartStepFilter: MethodFilter?,
        stepSize: Int
    ): DebugProcessImpl.ResumeCommand? {
        if (suspendContext == null || suspendContext.isResumed) return null
        val sourcePosition = suspendContext.getSourcePosition() ?: return null
        if (sourcePosition.file !is KtFile) return null
        return getStepIntoCommand(suspendContext, ignoreFilters, smartStepFilter)
    }

    override fun getRunToCursorCommand(
        suspendContext: SuspendContextImpl?,
        position: XSourcePosition,
        ignoreBreakpoints: Boolean
    ): DebugProcessImpl.ResumeCommand? {
        if (suspendContext == null || suspendContext.isResumed) return null
        // We may try Run-To-Cursor to/from even Java code inside one coroutine
        if (!Registry.`is`("debugger.filter.breakpoints.by.coroutine.id")) return null

        return DebuggerSteppingHelper.createRunToCursorCommand(suspendContext, position, ignoreBreakpoints)
    }

    fun getStepIntoCommand(
        suspendContext: SuspendContextImpl,
        ignoreFilters: Boolean,
        smartStepFilter: MethodFilter?
    ): DebugProcessImpl.ResumeCommand? {
        return DebuggerSteppingHelper.createStepIntoCommand(suspendContext, ignoreFilters, smartStepFilter)
    }

    fun getStepOverCommand(
        suspendContext: SuspendContextImpl,
        ignoreBreakpoints: Boolean,
        sourcePosition: SourcePosition
    ): DebugProcessImpl.ResumeCommand? {
        return DebuggerSteppingHelper.createStepOverCommand(suspendContext, ignoreBreakpoints, sourcePosition)
    }

    fun getStepOutCommand(suspendContext: SuspendContextImpl, debugContext: DebuggerContextImpl): DebugProcessImpl.ResumeCommand? {
        return getStepOutCommand(suspendContext, debugContext.sourcePosition)
    }

    override fun getStepOutCommand(suspendContext: SuspendContextImpl?, stepSize: Int): DebugProcessImpl.ResumeCommand? {
        if (suspendContext == null || suspendContext.isResumed) return null
        val sourcePosition = suspendContext.debugProcess.debuggerContext.sourcePosition ?: return null
        if (sourcePosition.file !is KtFile) return null
        return getStepOutCommand(suspendContext, sourcePosition)
    }

    private fun getStepOutCommand(suspendContext: SuspendContextImpl, sourcePosition: SourcePosition): DebugProcessImpl.ResumeCommand? {
        if (sourcePosition.line < 0) return null
        return DebuggerSteppingHelper.createStepOutCommand(suspendContext, true)
    }
}

private fun SuspendContextImpl.getSourcePosition(): SourcePosition? =
    debugProcess.debuggerContext.sourcePosition

interface KotlinMethodFilter : MethodFilter {
    fun locationMatches(context: SuspendContextImpl, location: Location): Boolean
}

fun getStepOverAction(
    location: Location,
    suspendContext: SuspendContextImpl,
    frameProxy: StackFrameProxyImpl
): KotlinStepAction {
    val srcStackFrame = frameProxy.safeStackFrame() ?: return KotlinStepAction.JvmStepOver
    val srcMethod = location.safeMethod() ?: return KotlinStepAction.JvmStepOver
    val srcLocationToken = LocationToken.from(srcStackFrame).takeIf { it.lineNumber > 0 } ?: return KotlinStepAction.JvmStepOver

    if (srcLocationToken.inlineVariables.isEmpty() && srcMethod.isSyntheticMethodForDefaultParameters()) {
        val psiLineNumber = location.safeLineNumber() - 1
        val startLine = srcMethod.safeAllLineLocations().minOf { it.safeLineNumber() } - 1
        // Consider [start line, current line] as the current scope
        // to prevent jumping back to the first line of a default method
        val lineNumbers = Range(startLine, psiLineNumber)
        return KotlinStepAction.StepInto(KotlinStepOverParamDefaultImplsMethodFilter.create(location, lineNumbers))
    }

    val positionManager = suspendContext.debugProcess.positionManager
    val srcNamedDeclaration = location.getContainingNamedFunction(positionManager)

    val inlinedFunctionArgumentRangesToSkip = srcMethod.getInlineFunctionAndArgumentVariablesToBordersMap()
            .filter { !it.value.contains(location) }
            .values

    val srcStackFrames = srcStackFrame.computeKotlinStackFrameInfos()

    val filter = object : KotlinStepOverFilter(location) {
        override fun isAcceptable(dstLocation: Location, dstLocationToken: LocationToken, dstStackFrame: StackFrame): Boolean {
            fun fellIntoInlineBody() =
                inlinedFunctionArgumentRangesToSkip.any { it.contains(dstLocation) } || dstLocationToken.inlineVariables.any { it !in srcLocationToken.inlineVariables }

            fun isInInlineFunctionBody(): Boolean {
                // Fast path: we came to the different method.
                if (dstLocation.safeMethod() != srcMethod) return false

                // Otherwise, we try to detect if we stepped out to inline function body:
                // lambda body is already left, but inline call is still there.
                // src: a b c \c (a calls b, b calls c passing \c as lambda to it and now we are inside of that lambda \c)
                // dst: a b      -- result = false
                // dst: a b c    -- result = true
                // dst: a b c \c -- result = false
                // dst: a b c d  -- result = true
                // dst: a b d    -- result = false
                val dstStackFrames = dstStackFrame.computeKotlinStackFrameInfos()
                val commonPrefixLen = (srcStackFrames zip dstStackFrames).count { (a, b) -> a.scopeVariable == b.scopeVariable }
                val activeLambdas = mutableListOf<String>()
                for (i in srcStackFrames.size - 1 downTo  commonPrefixLen) {
                    val frame = srcStackFrames[i]
                    val lambda = frame.lambdaDetails
                    if (lambda != null) {
                        activeLambdas += lambda.inlineFunctionName
                    } else {
                        val name = frame.displayName
                        if (name != null && name == activeLambdas.lastOrNull()) {
                            activeLambdas.removeLast()
                        }
                    }
                }
                return activeLambdas.isNotEmpty()
            }

            val differentLine = dstLocationToken.lineNumber.let { it >= 0 && it != srcLocationToken.lineNumber }

            // If enabled, we treat inlined lambda bodies as regular lines of the outer named function.
            // Step over at the inline function call with lambda parameter skips the inlined function body and jumps to the first line of lambda.
            // Step over at the end of the lambda skips remaining inlined function body and steps to the first line after function call.
            val stepThroughLambdas = Registry.`is`("debugger.kotlin.step.through.inline.lambdas")
            val posInTheSameFunction = srcNamedDeclaration == dstLocation.getContainingNamedFunction(positionManager)

            return differentLine
                    // limit stepping "into": either completely prohibit it or allow stepping into lambdas written in the same function
                    && (!fellIntoInlineBody() || (stepThroughLambdas && posInTheSameFunction))
                    // limit stepping "out": prevent stepping into inline function bodies from inlined lambda body
                    // e.g., `fun main { run { println() } }`: step from println should jump to the main function, without stopping at the end of run body
                    && (!stepThroughLambdas || posInTheSameFunction || !isInInlineFunctionBody())
                    && !isInlineFunctionFromLibrary(positionManager, dstLocation, dstLocationToken)
                    && !dstLocation.isOnFunctionDeclaration(positionManager)
        }
    }

    return KotlinStepAction.KotlinStepOver(filter)
}

private fun Location.isOnFunctionDeclaration(positionManager: PositionManager): Boolean  =
    runReadAction {
        val sourcePosition = positionManager.getSourcePosition(this) ?: return@runReadAction false
        val file = sourcePosition.file as? KtFile ?: return@runReadAction false
        val elementAtLine = findElementAtLine(file, sourcePosition.line)
        elementAtLine is KtNamedFunction || elementAtLine?.parentOfType<KtParameterList>() != null
    }

private fun Location.getContainingNamedFunction(positionManager: PositionManager): KtDeclarationWithBody? =
    runReadAction {
        positionManager.getSourcePosition(this)
            ?.elementAt
            ?.parentOfType<KtNamedFunction>(withSelf = true)
    }

internal fun createKotlinInlineFilter(suspendContext: SuspendContextImpl): KotlinInlineFilter? {
    val location = suspendContext.location ?: return null
    val method = location.safeMethod() ?: return null
    return KotlinInlineFilter(location, method)
}

internal class KotlinInlineFilter(location: Location, method: Method) {
    private val borders =
        method.getInlineFunctionAndArgumentVariablesToBordersMap()
            .values.filter { location !in it }

    fun isNestedInline(context: SuspendContextImpl?): Boolean {
        if (context === null) return false
        val candidate = context.location ?: return false
        return borders.any { range -> candidate in range }
    }
}

fun Method.isSyntheticMethodForDefaultParameters(): Boolean {
    if (!isSynthetic) return false
    val name = name()
    if (name.endsWith(JvmAbi.DEFAULT_PARAMS_IMPL_SUFFIX)) return true
    if (name != "<init>") return false
    val arguments = argumentTypeNames()
    val size = arguments.size
    // at least 1 param, 1 int flag, and 1 marker
    if (size < 3) return false
    // We should check not only the marker parameter, as it is present also
    // for object constructor and sealed class constructor
    return arguments[size - 2] == "int" && arguments[size - 1] == "kotlin.jvm.internal.DefaultConstructorMarker"
}

private fun isInlineFunctionFromLibrary(positionManager: PositionManager, location: Location, token: LocationToken): Boolean {
    if (token.inlineVariables.isEmpty()) {
        return false
    }

    val debuggerSettings = DebuggerSettings.getInstance()
    if (!debuggerSettings.TRACING_FILTERS_ENABLED) {
        return false
    }

    tailrec fun getDeclarationName(element: PsiElement?): FqName? {
        val declaration = element?.getNonStrictParentOfType<KtDeclaration>() ?: return null
        declaration.kotlinFqName?.let { return it }
        return getDeclarationName(declaration.parent)
    }

    val fqn = runReadAction {
        val element = positionManager.getSourcePosition(location)?.elementAt
        getDeclarationName(element)?.takeIf { !it.isRoot }?.asString()
    } ?: return false

    for (filter in debuggerSettings.steppingFilters) {
        if (filter.isEnabled && filter.matches(fqn)) {
            return true
        }
    }

    return false
}

fun PsiElement.getLineRange(): IntRange? {
    val startLineNumber = getLineNumber(true)
    val endLineNumber = getLineNumber(false)
    if (startLineNumber > endLineNumber) {
        return null
    }

    return startLineNumber..endLineNumber
}

fun getStepOutAction(location: Location, frameProxy: StackFrameProxyImpl): KotlinStepAction {
    val stackFrame = frameProxy.safeStackFrame() ?: return KotlinStepAction.StepOut()
    val token = LocationToken.from(stackFrame).takeIf { it.lineNumber > 0 } ?: return KotlinStepAction.StepOut()
    if (token.inlineVariables.isEmpty()) {
        createStepOutMethodWithDefaultArgsActionIfNeeded(frameProxy)?.let { return it }
        return KotlinStepAction.StepOut()
    }
    val filter = object : KotlinStepOverFilter(location) {
        override fun isAcceptable(location: Location, locationToken: LocationToken, stackFrame: StackFrame): Boolean =
            locationToken.lineNumber >= 0
                    && token.inlineVariables.any { it !in locationToken.inlineVariables }
    }

    // Consider stepping with minimal step size if we are inside an inline lambda to handle one line lambdas.
    val stepSize =
        if (token.isInsideOneLineInlineLambda())
            StepRequest.STEP_MIN
        else
            StepRequest.STEP_LINE
    return KotlinStepAction.KotlinStepOver(filter, stepSize)
}

/**
 * Steps out from the `$default` method also
 */
private fun createStepOutMethodWithDefaultArgsActionIfNeeded(frameProxy: StackFrameProxyImpl): KotlinStepAction.StepOut? {
    val frames = frameProxy.safeThreadProxy()?.frames() ?: return null
    if (frames.size <= 1) return null
    val previousLocation = frames.getOrNull(1)?.safeStackFrame()?.location() ?: return null
    val parentMethod = previousLocation.safeMethod() ?: return null
    if (!parentMethod.isSyntheticMethodForDefaultParameters()) return null

    val parentLines = parentMethod.allLineLocations().map { it.safeLineNumber() - 1 }
    val parentRange = Range(parentLines.min(), parentLines.max())
    return KotlinStepAction.StepOut(object : MethodFilter {
        override fun getCallingExpressionLines() = parentRange
        override fun onReached(context: SuspendContextImpl?, hint: RequestHint?) = StepRequest.STEP_OUT
        override fun locationMatches(process: DebugProcessImpl?, location: Location?): Boolean =
            location?.safeMethod() == parentMethod
    })
}

private fun LocationToken.isInsideOneLineInlineLambda(): Boolean {
    for (variable in inlineVariables) {
        val borders = variable.getBorders() ?: continue
        if (variable.name().startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT) &&
            borders.start.lineNumber() == borders.endInclusive.lineNumber())
        {
            return true
        }
    }
    return false
}
