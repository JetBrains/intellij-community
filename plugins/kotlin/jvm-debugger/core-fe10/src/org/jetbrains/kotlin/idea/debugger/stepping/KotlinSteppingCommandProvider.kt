// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.stepping

import com.intellij.debugger.PositionManager
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.MethodFilter
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.JvmSteppingCommandProvider
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.Range
import com.sun.jdi.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.base.utils.fqname.getKotlinFqName
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.util.CodeInsightUtils
import org.jetbrains.kotlin.idea.core.util.getLineNumber
import org.jetbrains.kotlin.idea.debugger.*
import org.jetbrains.kotlin.idea.debugger.stepping.filter.KotlinStepOverParamDefaultImplsMethodFilter
import org.jetbrains.kotlin.idea.debugger.stepping.filter.LocationToken
import org.jetbrains.kotlin.idea.debugger.stepping.filter.StepOverCallerInfo
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import kotlin.math.max
import kotlin.math.min

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

    @TestOnly
    fun getStepIntoCommand(
        suspendContext: SuspendContextImpl,
        ignoreFilters: Boolean,
        smartStepFilter: MethodFilter?
    ): DebugProcessImpl.ResumeCommand? {
        return DebuggerSteppingHelper.createStepIntoCommand(suspendContext, ignoreFilters, smartStepFilter)
    }

    @TestOnly
    fun getStepOverCommand(
        suspendContext: SuspendContextImpl,
        ignoreBreakpoints: Boolean,
        sourcePosition: SourcePosition
    ): DebugProcessImpl.ResumeCommand? {
        return DebuggerSteppingHelper.createStepOverCommand(suspendContext, ignoreBreakpoints, sourcePosition)
    }

    @TestOnly
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

private operator fun PsiElement?.contains(element: PsiElement): Boolean {
    return this?.textRange?.contains(element.textRange) ?: false
}

private fun findInlineFunctionCalls(sourcePosition: SourcePosition): List<KtCallExpression> {
    fun isInlineCall(expr: KtCallExpression): Boolean {
        val resolvedCall = expr.resolveToCall() ?: return false
        return InlineUtil.isInline(resolvedCall.resultingDescriptor)
    }

    return findCallsOnPosition(sourcePosition, ::isInlineCall)
}

private fun findCallsOnPosition(sourcePosition: SourcePosition, filter: (KtCallExpression) -> Boolean): List<KtCallExpression> {
    val file = sourcePosition.file as? KtFile ?: return emptyList()
    val lineNumber = sourcePosition.line

    val lineElement = findElementAtLine(file, lineNumber)

    if (lineElement !is KtElement) {
        if (lineElement != null) {
            val call = findCallByEndToken(lineElement)
            if (call != null && filter(call)) {
                return listOf(call)
            }
        }

        return emptyList()
    }

    val start = lineElement.startOffset
    val end = lineElement.endOffset

    val allFilteredCalls = CodeInsightUtils.findElementsOfClassInRange(file, start, end, KtExpression::class.java)
        .map { KtPsiUtil.getParentCallIfPresent(it as KtExpression) }
        .filterIsInstance<KtCallExpression>()
        .filter { filter(it) }
        .toSet()

    // It is necessary to check range because of multiline assign
    var linesRange = lineNumber..lineNumber
    return allFilteredCalls.filter {
        val shouldInclude = it.getLineNumber() in linesRange
        if (shouldInclude) {
            linesRange = min(linesRange.first, it.getLineNumber())..max(linesRange.last, it.getLineNumber(false))
        }
        shouldInclude
    }
}

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

    val inlinedFunctionArgumentRangesToSkip =
        method.getInlineFunctionAndArgumentVariablesToBordersMap()
            .filter { !it.value.contains(location) }
            .values
    val positionManager = suspendContext.debugProcess.positionManager
    val tokensToSkip = mutableSetOf(token)
    for (candidate in method.allLineLocations() ?: emptyList()) {
        val candidateStackFrame = StackFrameForLocation(frameProxy.stackFrame, candidate)
        val candidateToken = LocationToken.from(candidateStackFrame)

        val isAcceptable = candidateToken.lineNumber >= 0
                && candidateToken.lineNumber != token.lineNumber
                && inlinedFunctionArgumentRangesToSkip.none { it.contains(candidate) }
                && candidateToken.inlineVariables.none { it !in token.inlineVariables }
                && !isInlineFunctionFromLibrary(positionManager, candidate, candidateToken)
                && !candidate.isOnFunctionDeclaration(positionManager)

        if (!isAcceptable) {
            tokensToSkip += candidateToken
        }
    }

    return KotlinStepAction.KotlinStepOver(tokensToSkip, StepOverCallerInfo.from(location))
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
        declaration.getKotlinFqName()?.let { return it }
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

private class StackFrameForLocation(private val original: StackFrame, private val location: Location) : StackFrame by original {
    override fun location() = location

    override fun visibleVariables(): List<LocalVariable> {
        return location.method()?.variables()?.filter { it.isVisible(this) } ?: throw AbsentInformationException()
    }

    override fun visibleVariableByName(name: String?): LocalVariable {
        return location.method()?.variablesByName(name)?.firstOrNull { it.isVisible(this) } ?: throw AbsentInformationException()
    }
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
    val method = location.safeMethod() ?: return KotlinStepAction.StepOut
    val token = LocationToken.from(stackFrame).takeIf { it.lineNumber > 0 } ?: return KotlinStepAction.StepOut
    if (token.inlineVariables.isEmpty()) {
        return KotlinStepAction.StepOut
    }

    val tokensToSkip = mutableSetOf(token)

    for (candidate in method.allLineLocations() ?: emptyList()) {
        val candidateStackFrame = StackFrameForLocation(frameProxy.stackFrame, candidate)
        val candidateToken = LocationToken.from(candidateStackFrame)

        val isAcceptable = candidateToken.lineNumber >= 0
                && candidateToken.lineNumber != token.lineNumber
                && token.inlineVariables.any { it !in candidateToken.inlineVariables }

        if (!isAcceptable) {
            tokensToSkip += candidateToken
        }
    }

    return KotlinStepAction.KotlinStepOver(tokensToSkip, StepOverCallerInfo.from(location))
}
