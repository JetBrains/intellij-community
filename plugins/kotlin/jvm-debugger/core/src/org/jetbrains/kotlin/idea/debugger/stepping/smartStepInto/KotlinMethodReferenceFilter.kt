// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.BreakpointStepMethodFilter
import com.intellij.util.Range
import org.jetbrains.kotlin.psi.KtDeclarationWithBody

class KotlinMethodReferenceFilter(
    declaration: KtDeclarationWithBody?,
    lines: Range<Int>?,
    methodInfo: CallableMemberInfo
) : KotlinMethodFilter(declaration, lines, methodInfo), BreakpointStepMethodFilter {
    private val breakpointPosition: SourcePosition?
    private val lastStatementLine: Int
    init {
        val (firstPosition, lastPosition) =
            if (declaration != null)
                findFirstAndLastStatementPositions(declaration)
            else
                Pair(null, null)
        breakpointPosition = firstPosition
        lastStatementLine = lastPosition?.line ?: -1
    }

    override fun getBreakpointPosition() = breakpointPosition

    override fun getLastStatementLine() = lastStatementLine
}
