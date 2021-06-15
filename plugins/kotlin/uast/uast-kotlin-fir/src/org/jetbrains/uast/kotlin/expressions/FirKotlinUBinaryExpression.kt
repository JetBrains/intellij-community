// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastBinaryOperator

class FirKotlinUBinaryExpression(
    override val sourcePsi: KtBinaryExpression,
    givenParent: UElement?
) : KotlinAbstractUBinaryExpression(sourcePsi, givenParent) {

    override fun handleBitwiseOperators(): UastBinaryOperator {
        val other = UastBinaryOperator.OTHER
        val resolvedOperator = resolveOperator() ?: return other
        return BITWISE_OPERATORS[resolvedOperator.name] ?: other
    }
}
