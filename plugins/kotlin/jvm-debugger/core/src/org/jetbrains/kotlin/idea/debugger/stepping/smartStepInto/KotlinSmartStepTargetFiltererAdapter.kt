// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.PositionManager
import com.intellij.openapi.application.readAction
import com.intellij.psi.util.parentOfType
import com.sun.jdi.Location
import org.jetbrains.kotlin.idea.debugger.base.util.safeGetSourcePosition
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.core.getInlineFunctionAndArgumentVariablesToBordersMap
import org.jetbrains.kotlin.idea.debugger.core.isInlineFunctionMarkerVariableName
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class KotlinSmartStepTargetFiltererAdapter(
    lines: ClosedRange<Int>,
    location: Location,
) : LineMatchingMethodVisitor(lines) {
    private val inlineCalls = extractInlineCalls(location)
    private val targetOffset = location.codeIndex()
    private var inInline = false
    internal var currentOffset: Long = -1

    private val visitedTrace = mutableListOf<BytecodeTraceElement>()
    private val unvisitedTrace = mutableListOf<BytecodeTraceElement>()

    private fun add(e: BytecodeTraceElement) {
        if (currentOffset < targetOffset) {
            visitedTrace += e
        } else {
            unvisitedTrace += e
        }
    }

    public override fun reportOpcode(opcode: Int) {
        if (!lineEverMatched) return
        val inlineCall = inlineCalls.firstOrNull { currentOffset in it.bciRange }
        if (inlineCall == null) {
            inInline = false
            return
        }

        // Track only the inline calls on the same inlining level.
        // We aim to check only the calls on the current line, so the calls inside inline functions should not be tracked here.
        if (inInline) return
        inInline = true

        if (inlineCall.isInlineFun) {
            add(BytecodeTraceElement.InlineCall(inlineCall))
        } else {
            add(BytecodeTraceElement.InlineInvoke())
        }
    }

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        if (lineMatches) {
            add(BytecodeTraceElement.MethodCall(owner, name, descriptor))
        }
    }

    suspend fun visitTrace(
        targetFilterer: KotlinSmartStepTargetFilterer,
        positionManager: PositionManager
    ): Pair<List<KotlinMethodSmartStepTarget>, List<KotlinMethodSmartStepTarget>> {
        for (element in visitedTrace) {
            visitTraceElement(element, targetFilterer, positionManager)
        }
        val unvisitedTargets = targetFilterer.getUnvisitedTargets()
        for (element in unvisitedTrace) {
            visitTraceElement(element, targetFilterer, positionManager)
        }
        val unvisitedAtTheEnd = targetFilterer.getUnvisitedTargets()
        return unvisitedTargets to unvisitedAtTheEnd
    }

    private suspend fun visitTraceElement(
        element: BytecodeTraceElement,
        targetFilterer: KotlinSmartStepTargetFilterer,
        positionManager: PositionManager
    ) {
        when (element) {
            is BytecodeTraceElement.InlineCall -> {
                val calledInlineFunction = getCalledInlineFunction(positionManager, element.callInfo.startLocation) ?: return
                targetFilterer.visitInlineFunction(calledInlineFunction)
            }

            is BytecodeTraceElement.InlineInvoke -> {
                targetFilterer.visitInlineInvokeCall()
            }

            is BytecodeTraceElement.MethodCall -> {
                targetFilterer.visitOrdinaryFunction(element.owner, element.name, element.descriptor)
            }
        }
    }
}

private sealed class BytecodeTraceElement {
    data class InlineCall(val callInfo: InlineCallInfo) : BytecodeTraceElement()

    /**
     * Should be only one invoke smart step target.
     * @see [KotlinMethodSmartStepTarget.equals]
     */
    class InlineInvoke : BytecodeTraceElement()
    data class MethodCall(val owner: String, val name: String, val descriptor: String) : BytecodeTraceElement()
}

internal data class InlineCallInfo(val isInlineFun: Boolean, val bciRange: LongRange, val startLocation: Location)

private fun extractInlineCalls(location: Location): List<InlineCallInfo> = location.safeMethod()
    ?.getInlineFunctionAndArgumentVariablesToBordersMap()
    ?.toList()
    .orEmpty()
    .map { (variable, locationRange) ->
        InlineCallInfo(
            isInlineFun = variable.name().isInlineFunctionMarkerVariableName,
            bciRange = locationRange.start.codeIndex()..locationRange.endInclusive.codeIndex(),
            startLocation = locationRange.start
        )
    }
    // Filter already visible variable to support smart-step-into while inside an inline function
    .filterNot { location.codeIndex() in it.bciRange }

private suspend fun getCalledInlineFunction(positionManager: PositionManager, location: Location): KtNamedFunction? {
    val sourcePosition = positionManager.safeGetSourcePosition(location) ?: return null
    return readAction { sourcePosition.elementAt?.parentOfType<KtNamedFunction>() }
}
