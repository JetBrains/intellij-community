// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.actions.JvmSmartStepIntoErrorReporter
import com.intellij.debugger.actions.JvmSmartStepIntoHandler
import com.intellij.debugger.actions.JvmSmartStepIntoHandler.SmartStepIntoDetectionStatus
import com.intellij.debugger.actions.SmartStepTarget
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.MethodFilter
import com.intellij.debugger.engine.executeOnDMT
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DexDebugFacility
import com.intellij.debugger.jdi.MethodBytecodeUtil
import com.intellij.debugger.statistics.DebuggerStatistics
import com.intellij.debugger.statistics.Engine
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.parents
import com.intellij.util.Range
import com.intellij.util.containers.OrderedSet
import com.sun.jdi.Location
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.compute
import org.jetbrains.kotlin.idea.base.psi.getTopmostElementAtOffset
import org.jetbrains.kotlin.idea.debugger.KotlinDebuggerSettings
import org.jetbrains.kotlin.idea.debugger.base.util.dumbAction
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.core.DexBytecodeInspector
import org.jetbrains.kotlin.idea.debugger.getContainingBlockOrMethod
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

class KotlinSmartStepIntoHandler : JvmSmartStepIntoHandler() {
    override fun isAvailable(position: SourcePosition?): Boolean = position?.file is KtFile

    override fun findStepIntoTargets(position: SourcePosition, session: DebuggerSession): Promise<List<SmartStepTarget>> =
        if (KotlinDebuggerSettings.getInstance().alwaysDoSmartStepInto) {
            findSmartStepTargetsAsync(position, session)
        } else {
            super.findStepIntoTargets(position, session)
        }

    override fun findSmartStepTargetsAsync(position: SourcePosition, session: DebuggerSession): Promise<List<SmartStepTarget>> {
        val promise = AsyncPromise<List<SmartStepTarget>>()
        val debuggerContext = session.contextManager.context
        executeOnDMT(debuggerContext) {
            promise.compute { findSmartStepTargetsInternal(position, session) }
        }.invokeOnCompletion {
            if (it is CancellationException) {
                promise.setError("Cancelled")
            }
        }
        return promise
    }

    override fun findSmartStepTargets(position: SourcePosition): List<SmartStepTarget> = findSmartStepTargetsSync(position, null)

    @ApiStatus.Internal
    fun findSmartStepTargetsSync(position: SourcePosition, session: DebuggerSession?): List<SmartStepTarget> =
        runBlockingCancellable {
            findSmartStepTargetsInternal(position, session)
        }

    override fun createMethodFilter(stepTarget: SmartStepTarget?): MethodFilter? =
        when (stepTarget) {
            is KotlinSmartStepTarget -> stepTarget.createMethodFilter()
            else -> super.createMethodFilter(stepTarget)
        }

    private suspend fun findSmartStepTargetsInternal(position: SourcePosition, session: DebuggerSession?): List<SmartStepTarget> =
        try {
            findSmartStepTargets(position, session)
        } catch (e: Exception) {
            DebuggerStatistics.logSmartStepIntoTargetsDetection(
                session?.project, Engine.KOTLIN,
                SmartStepIntoDetectionStatus.INTERNAL_ERROR
            )
            throw e
        }

    private suspend fun findSmartStepTargets(position: SourcePosition, session: DebuggerSession?): List<SmartStepTarget> {
        val expression = readAction { position.getContainingExpression() } ?: run {
            DebuggerStatistics.logSmartStepIntoTargetsDetection(
                session?.project, Engine.KOTLIN,
                SmartStepIntoDetectionStatus.INVALID_POSITION
            )
            return emptyList()
        }
        val project = readAction { expression.project }
        val lines = readAction { expression.getLines()?.coerceAtLeast(position.line) } ?: run {
            DebuggerStatistics.logSmartStepIntoTargetsDetection(
                session?.project, Engine.KOTLIN,
                SmartStepIntoDetectionStatus.INVALID_POSITION
            )
            return emptyList()
        }
        var targets = dumbAction(project, fallback = emptyList()) {
            readAction { findSmartStepTargets(expression, lines) }
        }
        if (targets.isEmpty()) return emptyList()
        if (session != null) {
            val currentMethodName = session.process.suspendManager.pausedContext?.frameProxy?.safeLocation()?.safeMethod()?.name()
            // Cannot analyze method calls in the default method body, as they are located in a different method in bytecode
            if (currentMethodName?.endsWith("\$default") == true) {
                DebuggerStatistics.logSmartStepIntoTargetsDetection(session.project, Engine.KOTLIN, SmartStepIntoDetectionStatus.NO_TARGETS)
                return emptyList()
            }
            val context = SmartStepIntoContext(expression, session.process, position, lines.toClosedRange())
            targets = dumbAction(project, fallback = targets) { calculateSmartStepTargetsToShow(targets, context) }
        } else {
            DebuggerStatistics.logSmartStepIntoTargetsDetection(
                position.file.project, Engine.KOTLIN,
                SmartStepIntoDetectionStatus.BYTECODE_NOT_AVAILABLE
            )
        }
        targets = readAction { targets.sortedBy { it.highlightElement?.textOffset ?: 0 } }
        return readAction { reorderWithSteppingFilters(targets) }
    }
}

fun findSmartStepTargets(element: KtElement, lines: Range<Int>): List<SmartStepTarget> {
    val targets = OrderedSet<SmartStepTarget>()
    val visitor = SmartStepTargetVisitor(lines, targets)
    element.accept(visitor, null)
    return targets.toList()
}

private suspend fun calculateSmartStepTargetsToShow(targets: List<SmartStepTarget>, context: SmartStepIntoContext): List<SmartStepTarget> {
    val methodTargets = targets.filterIsInstance<KotlinMethodSmartStepTarget>()
    val notYetExecutedMethodTargets = methodTargets.filterAlreadyExecuted(context).toHashSet()
    val targetsToShow = targets.filter { it !is KotlinMethodSmartStepTarget || it in notYetExecutedMethodTargets }
    val removed = methodTargets.toHashSet() - notYetExecutedMethodTargets
    readAction { fixOrdinalsAfterFiltering(targets, removed) }
    return targetsToShow
}

suspend fun List<KotlinMethodSmartStepTarget>.filterAlreadyExecuted(
    context: SmartStepIntoContext,
    specificLocation: Location? = null
): List<KotlinMethodSmartStepTarget> {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    val debugProcess = context.debugProcess
    if (isEmpty()) {
        DebuggerStatistics.logSmartStepIntoTargetsDetection(debugProcess.project, Engine.KOTLIN, SmartStepIntoDetectionStatus.SUCCESS)
        return this
    }
    if (DexDebugFacility.isDex(debugProcess)) {
        return DexBytecodeInspector.EP.extensionList.firstOrNull()?.filterAlreadyExecutedTargets(this, context)
            ?: this
    }
    val frameProxy = debugProcess.suspendManager.pausedContext?.frameProxy
    val location = specificLocation ?: frameProxy?.safeLocation() ?: run {
        DebuggerStatistics.logSmartStepIntoTargetsDetection(
            debugProcess.project, Engine.KOTLIN,
            SmartStepIntoDetectionStatus.BYTECODE_NOT_AVAILABLE
        )
        return this
    }
    return filterSmartStepTargets(location, this, context)
}

private fun fixOrdinalsAfterFiltering(all: List<SmartStepTarget>, removed: Set<KotlinMethodSmartStepTarget>) {
    for (removedTarget in removed) {
        fixOrdinalsAfterTargetRemoval(removedTarget, all)
    }
}

private fun fixOrdinalsAfterTargetRemoval(removedTarget: KotlinMethodSmartStepTarget, all: List<SmartStepTarget>) {
    val removedOrdinal = removedTarget.methodInfo.ordinal
    for (target in all.targetsWithDeclaration(removedTarget.getDeclaration())) {
        if (target.methodInfo.ordinal > removedOrdinal) {
            target.methodInfo.ordinal -= 1
        }
    }
}

/**
 * Retrieves the containing expression of the current source position.
 *
 * It should be suitable for smart step into analysis,
 * meaning that it is expected to include all possible step targets.
 */
fun SourcePosition.getContainingExpression(): KtElement? {
    val element = elementAt ?: return null
    // Firstly, try to locate an element that starts at the current line
    val topmostElement = getTopmostElementAtOffset(element, element.textRange.startOffset) as? KtElement

    val containingBlock = element.getContainingBlockOrMethod() ?: return null
    // Secondly, try to expand to an expression. It is essential when source position
    // is in the middle of the expression, e.g.
    // A()<line break>
    //    <caret>.foo().boo()
    // In this example, `element == .`, `result == null`, `expression == A().foo().boo()`
    val expression = element.parents(true)
        // We must stay inside the current block (and inside the current method).
        .takeWhile { it.getContainingBlockOrMethod() === containingBlock }
        .filterIsInstance<KtExpression>()
        .lastOrNull()
    val result = when {
        expression == null -> topmostElement
        topmostElement == null -> expression
        expression.textRange.contains(topmostElement.textRange) -> expression
        else -> topmostElement
    } ?: return null

    // Cannot analyze code out of the containing block,
    // this may lead to suggestions out of the current execution scope.
    if (result in containingBlock.parents(withSelf = true)) {
        // If this is a function without body, analyze only the top call
        if (result is KtNamedFunction && !result.hasBlockBody()) {
            return result.bodyExpression
        }
        return null
    } else {
        return result
    }
}

private fun KtElement.getLines(): Range<Int>? {
    val file = containingKtFile
    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
    val textRange = textRange
    return Range(document.getLineNumber(textRange.startOffset), document.getLineNumber(textRange.endOffset))
}

private fun Range<Int>.coerceAtLeast(value: Int): Range<Int> =
    if (from >= value && to >= value) this else Range(max(value, from), max(value, to))

private suspend fun filterSmartStepTargets(
    location: Location,
    targets: List<KotlinMethodSmartStepTarget>,
    context: SmartStepIntoContext,
): List<KotlinMethodSmartStepTarget> {
    val (expression, debugProcess, position, lines) = context
    val method = location.safeMethod() ?: run {
        DebuggerStatistics.logSmartStepIntoTargetsDetection(
            debugProcess.project, Engine.KOTLIN,
            SmartStepIntoDetectionStatus.BYTECODE_NOT_AVAILABLE
        )
        return targets
    }
    val targetFiltererAdapter = KotlinSmartStepTargetFiltererAdapter(lines, location)

    MethodBytecodeUtil.visit(method, Long.MAX_VALUE, SmartStepTargetsBytecodeVisitor(targetFiltererAdapter), true)

    val targetFilterer = KotlinSmartStepTargetFilterer(targets, debugProcess)
    var (unvisitedTargets, unvisitedAtTheEnd) = targetFiltererAdapter.visitTrace(targetFilterer, debugProcess.positionManager)
    val eliminatedEqualsCalls = unvisitedAtTheEnd.filter { it.methodInfo.isEqualsNullCall }
    if (eliminatedEqualsCalls.isNotEmpty()) {
        unvisitedAtTheEnd = unvisitedAtTheEnd - eliminatedEqualsCalls
        unvisitedTargets = unvisitedTargets - eliminatedEqualsCalls
    }
    if (unvisitedAtTheEnd.isNotEmpty()) {
        val targetStrings = unvisitedAtTheEnd.map { "Target(name=${it.methodInfo.name}, info=${it.methodInfo})" }
        val session = debugProcess.session
        if (Registry.`is`("debugger.kotlin.report.smart.step.into.targets.detection.failure")) {
            JvmSmartStepIntoErrorReporter.report(expression, session, position, "Failed to locate target calls: $targetStrings")
        }
        DebuggerStatistics.logSmartStepIntoTargetsDetection(session?.project, Engine.KOTLIN, SmartStepIntoDetectionStatus.TARGETS_MISMATCH)
        // We failed to filter out all smart step targets during the traversal of the whole method, so
        // we can't guarantee the correctness of filtering.
        return targets
    } else {
        DebuggerStatistics.logSmartStepIntoTargetsDetection(debugProcess.project, Engine.KOTLIN, SmartStepIntoDetectionStatus.SUCCESS)
    }
    return unvisitedTargets
}

private class SmartStepTargetsBytecodeVisitor(private val targetFiltererAdapter: KotlinSmartStepTargetFiltererAdapter) :
    OpcodeReportingMethodVisitor(targetFiltererAdapter), MethodBytecodeUtil.InstructionOffsetReader {

    override fun readBytecodeInstructionOffset(offset: Int) {
        targetFiltererAdapter.currentOffset = offset.toLong()
    }

    override fun reportOpcode(opcode: Int) {
        ProgressManager.checkCanceled()
        targetFiltererAdapter.reportOpcode(opcode)
    }

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        targetFiltererAdapter.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }
}

private fun Range<Int>.toClosedRange() = from..to

data class SmartStepIntoContext(
    val expression: KtElement,
    val debugProcess: DebugProcessImpl,
    val position: SourcePosition,
    val lines: ClosedRange<Int>,
)
