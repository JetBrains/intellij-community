// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.debugger.PositionManager
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.MethodFilter
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
import com.sun.jdi.request.StepRequest
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.base.util.safeStackFrame
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.getBorders
import org.jetbrains.kotlin.idea.debugger.core.findElementAtLine
import org.jetbrains.kotlin.idea.debugger.core.getInlineFunctionAndArgumentVariablesToBordersMap
import org.jetbrains.kotlin.idea.debugger.core.stepping.filter.KotlinStepOverFilter
import org.jetbrains.kotlin.idea.debugger.core.stepping.filter.KotlinStepOverParamDefaultImplsMethodFilter
import org.jetbrains.kotlin.idea.debugger.core.stepping.filter.LocationToken
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
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

private operator fun PsiElement?.contains(element: PsiElement): Boolean =
    this?.textRange?.contains(element.textRange) ?: false

interface KotlinMethodFilter : MethodFilter {
    fun locationMatches(context: SuspendContextImpl, location: Location): Boolean
}

fun getStepOverAction(
    location: Location,
    suspendContext: SuspendContextImpl,
    frameProxy: StackFrameProxyImpl
): KotlinStepAction {
    val stackFrame = frameProxy.safeStackFrame() ?: return KotlinStepAction.JvmStepOver
    val method = location.safeMethod() ?: return KotlinStepAction.JvmStepOver
    val token = LocationToken.from(stackFrame).takeIf { it.lineNumber > 0 } ?: return KotlinStepAction.JvmStepOver

    if (token.inlineVariables.isEmpty() && method.isSyntheticMethodForDefaultParameters()) {
        val psiLineNumber = location.lineNumber() - 1
        val lineNumbers = Range(psiLineNumber, psiLineNumber)
        return KotlinStepAction.StepInto(KotlinStepOverParamDefaultImplsMethodFilter.create(location, lineNumbers))
    }

    val inlinedFunctionArgumentRangesToSkip = method.getInlineFunctionAndArgumentVariablesToBordersMap()
            .filter { !it.value.contains(location) }
            .values
    val positionManager = suspendContext.debugProcess.positionManager
    val filter = object : KotlinStepOverFilter(location) {
        override fun isAcceptable(location: Location, locationToken: LocationToken): Boolean =
            locationToken.lineNumber >= 0
                    && locationToken.lineNumber != token.lineNumber
                    && inlinedFunctionArgumentRangesToSkip.none { it.contains(location) }
                    && locationToken.inlineVariables.none { it !in token.inlineVariables }
                    && !isInlineFunctionFromLibrary(positionManager, location, locationToken)
                    && !location.isOnFunctionDeclaration(positionManager)
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
    return isSynthetic && name().endsWith(JvmAbi.DEFAULT_PARAMS_IMPL_SUFFIX)
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
    val stackFrame = frameProxy.safeStackFrame() ?: return KotlinStepAction.StepOut
    val token = LocationToken.from(stackFrame).takeIf { it.lineNumber > 0 } ?: return KotlinStepAction.StepOut
    if (token.inlineVariables.isEmpty()) {
        return KotlinStepAction.StepOut
    }
    val filter = object : KotlinStepOverFilter(location) {
        override fun isAcceptable(location: Location, locationToken: LocationToken): Boolean =
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
