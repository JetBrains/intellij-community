// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.equalsExpression
import org.jetbrains.kotlin.nj2k.parenthesize
import org.jetbrains.kotlin.nj2k.symbols.deepestFqName
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.asPrimitiveType
import org.jetbrains.kotlin.nj2k.types.isFloatingPoint

/**
 * Tries to convert `equals` method calls to `==` operator binary expressions, where applicable.
 */
class EqualsOperatorConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKQualifiedExpression) return recurse(element)
        if (element.receiver is JKSuperExpression) return recurse(element)
        val selector = element.selector as? JKCallExpression ?: return recurse(element)
        val arguments = selector.arguments.arguments
        val functionFqName = selector.identifier.deepestFqName()

        val (left, right) = when {
            functionFqName == "java.lang.Object.equals" && arguments.size == 1 ->
                element.receiver to arguments[0].value

            functionFqName == "java.util.Objects.equals" && arguments.size == 2 ->
                arguments[0].value to arguments[1].value

            else -> return recurse(element)
        }

        val leftType = left.calculateType(typeFactory)?.asPrimitiveType()
        val rightType = right.calculateType(typeFactory)?.asPrimitiveType()
        if (leftType?.isFloatingPoint() == true || rightType?.isFloatingPoint() == true) return recurse(element)

        left.detach(left.parent!!)
        right.detach(right.parent!!)
        val result = equalsExpression(left, right, typeFactory).parenthesize().withFormattingFrom(element)
        return recurse(result)
    }
}