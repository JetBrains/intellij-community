// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.PositionManager
import com.intellij.debugger.jdi.GeneratedLocation
import com.intellij.psi.util.parentOfType
import com.sun.jdi.Location
import org.jetbrains.kotlin.idea.debugger.getInlineFunctionNamesAndBorders
import org.jetbrains.kotlin.idea.debugger.safeMethod
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinSmartStepTargetFiltererAdapter(
    lines: ClosedRange<Int>,
    private val location: Location,
    private val positionManager: PositionManager,
    private val targetFilterer: KotlinSmartStepTargetFilterer
) : LineMatchingMethodVisitor(lines) {
    private val inlineFunctionNamesAndBorders = location.safeMethod()
        ?.getInlineFunctionNamesAndBorders()
        ?.toList()
        .orEmpty()
    private var inInline = false

    public override fun reportOpcode(opcode: Int) {
        if (!lineEverMatched) return

        val inlineCall = inlineFunctionNamesAndBorders.firstOrNull {
            currentLine in it.second.toLineNumberRange()
        }
        if (inlineCall == null) {
            inInline = false
            return
        } else if (!inInline) {
            val name = inlineCall.first.name()
            if (name.startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION)) {
                val calledInlineFunction = positionManager.getCalledInlineFunction(location, currentLine)
                if (calledInlineFunction != null) {
                    targetFilterer.visitInlineFunction(calledInlineFunction)
                }
            }
            inInline = true
        }
    }

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        if (lineMatches) {
            targetFilterer.visitOrdinaryFunction(owner, name, descriptor)
        }
    }
}

private fun PositionManager.getCalledInlineFunction(location: Location, line: Int): KtNamedFunction? {
    val methodName = location.safeMethod()?.name() ?: return null
    val mockLocation = GeneratedLocation(location.declaringType(), methodName, line)
    return getSourcePosition(mockLocation)?.elementAt?.parentOfType()
}

private fun ClosedRange<Location>.toLineNumberRange() =
    start.lineNumber()..endInclusive.lineNumber()
