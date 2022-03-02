// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.uast.*

class KotlinCustomUBinaryExpression(
    override val psi: PsiElement,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UBinaryExpression {
    override lateinit var leftOperand: UExpression

    override lateinit var operator: UastBinaryOperator

    override lateinit var rightOperand: UExpression

    override val operatorIdentifier: UIdentifier?
        get() = null

    override fun resolveOperator() = null
}
