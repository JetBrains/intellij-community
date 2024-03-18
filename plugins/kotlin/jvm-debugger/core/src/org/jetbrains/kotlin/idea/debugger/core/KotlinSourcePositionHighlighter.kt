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
import org.jetbrains.kotlin.idea.debugger.base.util.getRangeOfLine
import org.jetbrains.kotlin.idea.debugger.core.stepping.getLineRange
import org.jetbrains.kotlin.psi.KtFunction
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
        val lambda = element.parentOfType<KtFunction>()?.takeIf { it is KtFunctionLiteral || it.name == null } ?: return null
        val lambdaRange = JavaLineBreakpointType.getTextRangeWithoutTrailingComments(lambda)

        if (lambda.isOneLiner()) return lambdaRange

        val lambdaLineRange = lambda.getLineRange() ?: return null
        val lineRange = sourcePosition.file.getRangeOfLine(sourcePosition.line, skipWhitespace = false) ?: return null
        val intersection = lineRange.intersection(lambdaRange)

        // Highlight only part of the line after {
        if (sourcePosition.line == lambdaLineRange.first) {
            return intersection?.grown(1)
        }

        // Highlight only part of the line before }
        if (sourcePosition.line == lambdaLineRange.last
            // If lambda content is the only content on the line, highlight the whole line
            && intersection != lineRange
            // In case closing brace is the only lambda's symbol on the line, it could be hard to find the highlighting, highlight the whole line
            && intersection.length > 1
        ) {
            return intersection
        }

        return null
    }
}
