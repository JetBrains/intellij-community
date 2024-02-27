// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.parenthesizedWithFormatting
import org.jetbrains.kotlin.nj2k.tree.JKBinaryExpression
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.tree.hasLineBreakAfter

class ParenthesizeMultilineBinaryExpressionConversion(context: NewJ2kConverterContext) : RecursiveConversion(context) {
    context(KtAnalysisSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKBinaryExpression) return recurse(element)

        var current = element
        while (current is JKBinaryExpression) {
            if (current.left.hasLineBreakAfter) {
                // parenthesize the outermost binary expression and don't recurse further
                return element.parenthesizedWithFormatting()
            }
            current = current.left
        }

        return recurse(element)
    }
}
