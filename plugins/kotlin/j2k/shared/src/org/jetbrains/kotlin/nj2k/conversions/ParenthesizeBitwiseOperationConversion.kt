// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.parenthesizedWithFormatting
import org.jetbrains.kotlin.nj2k.tree.JKBinaryExpression
import org.jetbrains.kotlin.nj2k.tree.JKKtWordOperatorToken
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement

class ParenthesizeBitwiseOperationConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKBinaryExpression) return recurse(element)
        val parent = element.parent as? JKBinaryExpression ?: return recurse(element)
        val operator = element.operator.token
        val parentOperator = parent.operator.token
        if (operator == parentOperator) return recurse(element)
        if (operator !is JKKtWordOperatorToken && parentOperator !is JKKtWordOperatorToken) return recurse(element)
        return recurse(element.parenthesizedWithFormatting())
    }
}
