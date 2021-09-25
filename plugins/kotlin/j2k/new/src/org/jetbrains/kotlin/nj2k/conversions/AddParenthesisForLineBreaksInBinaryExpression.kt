// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.tree.*

class AddParenthesisForLineBreaksInBinaryExpression(override val context: NewJ2kConverterContext) :
    RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKBinaryExpression) return recurse(element)
        if (element.parent is JKBinaryExpression) return recurse(element)
        if (element.left.hasLeadingLineBreak) {
            return JKParenthesizedExpression(
                JKBinaryExpression(
                    element::left.detached(),
                    element::right.detached(),
                    element.operator
                ).withFormattingFrom(element)
            )
        }
        return recurse(element)
    }
}