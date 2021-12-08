// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.util.Range
import com.intellij.util.containers.addIfNotNull
import com.sun.jdi.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.util.CodeInsightUtils
import org.jetbrains.kotlin.idea.core.util.getLineNumber
import org.jetbrains.kotlin.idea.debugger.*
import org.jetbrains.kotlin.idea.debugger.stepping.filter.KotlinStepOverParamDefaultImplsMethodFilter
import org.jetbrains.kotlin.idea.debugger.stepping.filter.LocationToken
import org.jetbrains.kotlin.idea.debugger.stepping.filter.StepOverCallerInfo
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
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
    location: Location, sourcePosition: SourcePosition,
    suspendContext: SuspendContextImpl, frameProxy: StackFrameProxyImpl
): KotlinStepAction {
    val stackFrame = frameProxy.safeStackFrame() ?: return KotlinStepAction.JvmStepOver
    val method = location.safeMethod() ?: return KotlinStepAction.JvmStepOver
    val token = LocationToken.from(stackFrame).takeIf { it.lineNumber > 0 } ?: return KotlinStepAction.JvmStepOver

    if (token.inlineVariables.isEmpty() && method.isSyntheticMethodForDefaultParameters()) {
        val psiLineNumber = location.lineNumber() - 1
        val lineNumbers = Range(psiLineNumber, psiLineNumber)
        return KotlinStepAction.StepInto(KotlinStepOverParamDefaultImplsMethodFilter.create(location, lineNumbers))
    }

    val inlinedFunctionArgumentRangesToSkip = sourcePosition.collectInlineFunctionArgumentRangesToSkip()
    val positionManager = suspendContext.debugProcess.positionManager

    val tokensToSkip = mutableSetOf(token)
    for (candidate in method.allLineLocations() ?: emptyList()) {
        val candidateKotlinLineNumber =
            suspendContext.getSourcePositionLine(candidate) ?:
            candidate.safeKotlinPreferredLineNumber()
        val candidateStackFrame = StackFrameForLocation(frameProxy.stackFrame, candidate)
        val candidateToken = LocationToken.from(candidateStackFrame)

        val isAcceptable = candidateToken.lineNumber >= 0
                && candidateToken.lineNumber != token.lineNumber
                && inlinedFunctionArgumentRangesToSkip.none { range -> range.contains(candidateKotlinLineNumber) }
                && candidateToken.inlineVariables.none { it !in token.inlineVariables }
                && !isInlineFunctionFromLibrary(positionManager, candidate, candidateToken)

        if (!isAcceptable) {
            tokensToSkip += candidateToken
        }
    }

    return KotlinStepAction.KotlinStepOver(tokensToSkip, StepOverCallerInfo.from(location))
}

internal fun createKotlinInlineFilter(suspendContext: SuspendContextImpl): KotlinInlineFilter? {
    val location = suspendContext.location ?: return null
    val method = location.safeMethod() ?: return null
    return KotlinInlineFilter(location, method)
}

internal class KotlinInlineFilter(location: Location, method: Method) {
    private val borders = method.getInlineFunctionNamesAndBorders().values.filter { location !in it }

    fun isNestedInline(context: SuspendContextImpl?): Boolean {
        if (context === null) return false
        val candidate = context.location ?: return false
        return borders.any { range -> candidate in range }
    }
}

fun Method.isSyntheticMethodForDefaultParameters(): Boolean {
    return isSynthetic && name().endsWith(JvmAbi.DEFAULT_PARAMS_IMPL_SUFFIX)
}

private fun SuspendContextImpl.getSourcePositionLine(location: Location) =
    debugProcess.positionManager.getSourcePosition(location)?.line

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

private fun SourcePosition.collectInlineFunctionArgumentRangesToSkip() = runReadAction {
    val inlineFunctionCalls = findInlineFunctionCalls(this)
    if (inlineFunctionCalls.isEmpty()) {
        return@runReadAction emptyList()
    }

    val firstCall = inlineFunctionCalls.first()
    val resolutionFacade = KotlinCacheService.getInstance(firstCall.project).getResolutionFacade(inlineFunctionCalls)
    val bindingContext = resolutionFacade.analyze(firstCall, BodyResolveMode.FULL)

    return@runReadAction inlineFunctionCalls.collectInlineFunctionArgumentRangesToSkip(bindingContext)
}

private fun List<KtCallExpression>.collectInlineFunctionArgumentRangesToSkip(bindingContext: BindingContext): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    for (call in this) {
        ranges.addLambdaArgumentRanges(call)
        val callDescriptor = call.getResolvedCall(bindingContext)?.resultingDescriptor ?: continue
        ranges.addDefaultParametersRanges(call.valueArguments.size, callDescriptor)
        /*
            Also add the first line of the calling expression
            to handle cases like this:
                inline fun foo( // Add this line
                    i: Int,
                    j: Int
                ) {
                    ...
                }
        */
        val callElement = callDescriptor.findPsi() as? KtElement ?: continue
        val line = callElement.getLineNumber(start = true)
        ranges.addIfNotNull(line..line)
    }

    return ranges
}

private fun MutableList<IntRange>.addLambdaArgumentRanges(call: KtCallExpression) {
    for (arg in call.valueArguments) {
        val expression = arg.getArgumentExpression()
        val functionExpression = (expression as? KtLambdaExpression)?.functionLiteral ?: expression
        val function = functionExpression as? KtFunction ?: continue
        addIfNotNull(function.getLineRange())
    }
}

private fun MutableList<IntRange>.addDefaultParametersRanges(nonDefaultArgumentsNumber: Int, callDescriptor: CallableDescriptor) {
    val allArguments = callDescriptor.valueParameters
    for (i in nonDefaultArgumentsNumber until allArguments.size) {
        val argument = allArguments[i].findPsi() as? KtElement ?: continue
        addIfNotNull(argument.getLineRange())
    }
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

private fun KtElement.getLineRange(): IntRange? {
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
