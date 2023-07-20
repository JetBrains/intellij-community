// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionHighlighter
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.base.psi.isOneLiner
import org.jetbrains.kotlin.idea.debugger.KotlinReentrantSourcePosition
import org.jetbrains.kotlin.idea.debugger.KotlinSourcePositionWithEntireLineHighlighted
import org.jetbrains.kotlin.psi.KtFunctionLiteral

class KotlinSourcePositionHighlighter : SourcePositionHighlighter() {
    override fun getHighlightRange(sourcePosition: SourcePosition?): TextRange? {
        if (sourcePosition == null ||
            sourcePosition is KotlinSourcePositionWithEntireLineHighlighted ||
            sourcePosition is KotlinReentrantSourcePosition) {
            return null
        }

        val element = sourcePosition.elementAt ?: return null

        // Highlight only return keyword in case of conditional return breakpoint.
        if (JavaLineBreakpointType.isReturnKeyword(element) &&
            element === JavaLineBreakpointType.findSingleConditionalReturn(sourcePosition)) {
            return element.textRange
        }

        // Highlight only lambda body in case of lambda breakpoint.
        val lambda = element.parentOfType<KtFunctionLiteral>()
        if (lambda != null && lambda.isOneLiner()) {
            return lambda.textRange
        }

        return null
    }
}
