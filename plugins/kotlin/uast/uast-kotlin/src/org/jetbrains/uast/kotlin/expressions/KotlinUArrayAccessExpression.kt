// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiType
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UElement

class KotlinUArrayAccessExpression(
        override val sourcePsi: KtArrayAccessExpression,
        givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UArrayAccessExpression, KotlinUElementWithType, KotlinEvaluatableUElement {
    override val receiver by lz { KotlinConverter.convertOrEmpty(sourcePsi.arrayExpression, this) }
    override val indices by lz { sourcePsi.indexExpressions.map { KotlinConverter.convertOrEmpty(it, this) } }

    override fun getExpressionType(): PsiType? {
        super<KotlinUElementWithType>.getExpressionType()?.let { return it }

        // for unknown reason in assigment position there is no `EXPRESSION_TYPE_INFO` so we getting it from the array type
        val arrayExpression = sourcePsi.arrayExpression ?: return null
        val arrayType = arrayExpression.analyze()[BindingContext.EXPRESSION_TYPE_INFO, arrayExpression]?.type ?: return null
        return arrayType.arguments.firstOrNull()?.type?.toPsiType(this, arrayExpression, false )
    }

}