// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.parenthesizedWithFormatting
import org.jetbrains.kotlin.nj2k.tree.JKBinaryExpression
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.tree.hasLineBreakAfter

internal class ParenthesizeMultilineBinaryExpressionConversion(override val context: NewJ2kConverterContext) :
    RecursiveApplicableConversionBase(context) {

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
