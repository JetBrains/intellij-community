// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.actions.JvmSmartStepIntoHandler
import com.intellij.debugger.actions.SmartStepTarget
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.MethodFilter
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.jdi.MethodBytecodeUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.parents
import com.intellij.util.Range
import com.intellij.util.containers.OrderedSet
import com.sun.jdi.Location
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.compute
import org.jetbrains.kotlin.idea.base.psi.getTopmostElementAtOffset
import org.jetbrains.kotlin.idea.debugger.KotlinDebuggerSettings
import org.jetbrains.kotlin.idea.debugger.base.util.DexDebugFacility
import org.jetbrains.kotlin.idea.debugger.base.util.safeLocation
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.getContainingBlockOrMethod
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import kotlin.math.max

class KotlinSmartStepIntoHandler : JvmSmartStepIntoHandler() {
    override fun isAvailable(position: SourcePosition?) = position?.file is KtFile

    override fun findStepIntoTargets(position: SourcePosition, session: DebuggerSession) =
        if (KotlinDebuggerSettings.getInstance().alwaysDoSmartStepInto) {
            findSmartStepTargetsAsync(position, session)
        } else {
            super.findStepIntoTargets(position, session)
        }

    override fun findSmartStepTargetsAsync(position: SourcePosition, session: DebuggerSession): Promise<List<SmartStepTarget>> {
        val result = AsyncPromise<List<SmartStepTarget>>()
        val command =
            object : DebuggerCommandImpl(PrioritizedTask.Priority.NORMAL) {
                override fun action() =
                    result.compute { findSmartStepTargetsInReadAction(position, session) }

                override fun commandCancelled() {
                    result.setError("Cancelled")
                }
            }
        val managerThread = session.process.managerThread
        if (DebuggerManagerThreadImpl.isManagerThread()) {
            managerThread.invoke(command)
        } else {
            managerThread.schedule(command)
        }
        return result
    }

    override fun findSmartStepTargets(position: SourcePosition): List<SmartStepTarget> =
        findSmartStepTargetsInReadAction(position, null)

    override fun createMethodFilter(stepTarget: SmartStepTarget?): MethodFilter? =
        when (stepTarget) {
            is KotlinSmartStepTarget -> stepTarget.createMethodFilter()
            else -> super.createMethodFilter(stepTarget)
        }

    private fun findSmartStepTargetsInReadAction(position: SourcePosition, session: DebuggerSession?) =
        ReadAction.nonBlocking<List<SmartStepTarget>> {
            findSmartStepTargets(position, session)
        }.executeSynchronously()

    private fun findSmartStepTargets(position: SourcePosition, session: DebuggerSession?): List<SmartStepTarget> {
        val expression = position.getContainingExpression() ?: return emptyList()
        val lines = expression.getLines()?.coerceAtLeast(position.line) ?: return emptyList()
        var targets = findSmartStepTargets(expression, lines)
        if (session != null) {
            targets = calculateSmartStepTargetsToShow(targets, session.process, lines.toClosedRange())
        }
        return reorderWithSteppingFilters(targets)
    }
}

private fun findSmartStepTargets(element: KtElement, lines: Range<Int>): List<SmartStepTarget> {
    val targets = OrderedSet<SmartStepTarget>()
    val visitor = SmartStepTargetVisitor(lines, targets)
    element.accept(visitor, null)
    return targets.toList()
}

private fun calculateSmartStepTargetsToShow(targets: List<SmartStepTarget>, debugProcess: DebugProcessImpl, lines: ClosedRange<Int>): List<SmartStepTarget> {
    val methodTargets = targets.filterIsInstance<KotlinMethodSmartStepTarget>()
    val notYetExecutedMethodTargets = methodTargets.filterAlreadyExecuted(debugProcess, lines).toHashSet()
    return targets.filter { it !is KotlinMethodSmartStepTarget || it in notYetExecutedMethodTargets }
}

private fun List<KotlinMethodSmartStepTarget>.filterAlreadyExecuted(
    debugProcess: DebugProcessImpl,
    lines: ClosedRange<Int>
): List<KotlinMethodSmartStepTarget> {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    if (DexDebugFacility.isDex(debugProcess) || size <= 1) return this
    val frameProxy = debugProcess.suspendManager.pausedContext?.frameProxy
    val location = frameProxy?.safeLocation() ?: return this
    return filterSmartStepTargets(location, lines, this, debugProcess)
}

/**
 * Retrieves the containing expression of the current source position.
 *
 * It should be suitable for smart step into analysis,
 * meaning that it is expected to include all possible step targets.
 */
private fun SourcePosition.getContainingExpression(): KtElement? {
    val element = elementAt ?: return null
    // Firstly, try to locate an element that starts at the current line
    val result = getTopmostElementAtOffset(element, element.textRange.startOffset) as? KtElement

    val containingBlock = element.getContainingBlockOrMethod() ?: return result
    // Secondly, try to expand to an expression. It is essential when source position
    // is in the middle of the expression, e.g.
    // A()<line break>
    //    <caret>.foo().boo()
    // In this example, `element == .`, `result == null`, `expression == A().foo().boo()`
    val expression = element.parents(true)
        // We must stay inside the current block (and inside the current method).
        .takeWhile { it.getContainingBlockOrMethod() === containingBlock }
        .filterIsInstance<KtExpression>()
        .lastOrNull() ?: return result
    return if (result == null || expression.textRange.contains(result.textRange)) expression else result
}

private fun KtElement.getLines(): Range<Int>? {
    val file = containingKtFile
    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
    val textRange = textRange
    return Range(document.getLineNumber(textRange.startOffset), document.getLineNumber(textRange.endOffset))
}

private fun Range<Int>.coerceAtLeast(value: Int): Range<Int> =
    if (from >= value && to >= value) this else Range(max(value, from), max(value, to))

private fun filterSmartStepTargets(
    location: Location,
    lines: ClosedRange<Int>,
    targets: List<KotlinMethodSmartStepTarget>,
    debugProcess: DebugProcessImpl
): List<KotlinMethodSmartStepTarget> {
    val method = location.safeMethod() ?: return targets
    val targetFilterer = KotlinSmartStepTargetFilterer(targets, debugProcess)
    val targetFiltererAdapter = KotlinSmartStepTargetFiltererAdapter(
        lines, location, debugProcess.positionManager, targetFilterer
    )

    var unvisitedTargets: List<KotlinMethodSmartStepTarget>? = null
    MethodBytecodeUtil.visit(method, Long.MAX_VALUE, object : OpcodeReportingMethodVisitor(targetFiltererAdapter), MethodBytecodeUtil.InstructionOffsetReader {
        private var stopCollectingVisitedTargets = false

        override fun readBytecodeInstructionOffset(offset: Int) {
            if (!stopCollectingVisitedTargets && offset >= location.codeIndex()) {
                unvisitedTargets = targetFilterer.getUnvisitedTargets()
                stopCollectingVisitedTargets = true
            }
        }

        override fun reportOpcode(opcode: Int) {
            ProgressManager.checkCanceled()
            targetFiltererAdapter.reportOpcode(opcode)
        }

        override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
            targetFiltererAdapter.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }
    }, true)

    // We failed to filter out all smart step targets during the traversal of the whole method, so
    // we can't guarantee the correctness of filtering.
    if (targetFilterer.getUnvisitedTargets().isNotEmpty()) {
        return targets
    }
    return unvisitedTargets ?: targets
}

private fun Range<Int>.toClosedRange() = from..to
