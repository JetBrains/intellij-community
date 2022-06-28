// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtWhenCondition
import org.jetbrains.kotlin.psi.KtWhenConditionInRange
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinCustomUBinaryExpression(
    override val sourcePsi: KtWhenCondition,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UBinaryExpression {
    override lateinit var leftOperand: UExpression

    override lateinit var operator: UastBinaryOperator

    override lateinit var rightOperand: UExpression

    override val operatorIdentifier: UIdentifier?
        get() = null

    override fun resolveOperator(): PsiMethod? {
        if (sourcePsi is KtWhenConditionInRange) {
            return baseResolveProviderService.resolveCall(sourcePsi.operationReference)
        }
        return null
    }
}
