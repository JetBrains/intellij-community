// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.PositionManager
import com.intellij.psi.util.parentOfType
import com.sun.jdi.Location
import org.jetbrains.kotlin.idea.debugger.base.util.safeMethod
import org.jetbrains.kotlin.idea.debugger.core.getInlineFunctionAndArgumentVariablesToBordersMap
import org.jetbrains.kotlin.idea.debugger.core.isInlineFunctionMarkerVariableName
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinSmartStepTargetFiltererAdapter(
    lines: ClosedRange<Int>,
    location: Location,
    private val positionManager: PositionManager,
    private val targetFilterer: KotlinSmartStepTargetFilterer
) : LineMatchingMethodVisitor(lines) {
    private val inlineCalls = extractInlineCalls(location)
    private var inInline = false
    internal var currentOffset: Long = -1

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

        if (!inlineCall.variableName.isInlineFunctionMarkerVariableName) return

        val calledInlineFunction = getCalledInlineFunction(positionManager, inlineCall.startLocation) ?: return
        targetFilterer.visitInlineFunction(calledInlineFunction)
    }

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        if (lineMatches) {
            targetFilterer.visitOrdinaryFunction(owner, name, descriptor)
        }
    }
}

private data class InlineCallInfo(val variableName: String, val bciRange: LongRange, val startLocation: Location)

private fun extractInlineCalls(location: Location): List<InlineCallInfo> = location.safeMethod()
    ?.getInlineFunctionAndArgumentVariablesToBordersMap()
    ?.toList()
    .orEmpty()
    .map { (variable, locationRange) ->
        InlineCallInfo(
            variableName = variable.name(),
            bciRange = locationRange.start.codeIndex()..locationRange.endInclusive.codeIndex(),
            startLocation = locationRange.start
        )
    }
    // Filter already visible variable to support smart-step-into while inside an inline function
    .filterNot { location.codeIndex() in it.bciRange }

private fun getCalledInlineFunction(positionManager: PositionManager, location: Location): KtNamedFunction? =
    positionManager.getSourcePosition(location)?.elementAt?.parentOfType<KtNamedFunction>()
