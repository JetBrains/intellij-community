// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.resolve.BindingContext.DOUBLE_COLON_LHS
import org.jetbrains.uast.DEFAULT_EXPRESSION_TYPES_LIST
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

class KotlinUClassLiteralExpression(
        override val sourcePsi: KtClassLiteralExpression,
        givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UClassLiteralExpression, KotlinUElementWithType {
    override val type by lz {
        val ktType = sourcePsi.analyze()[DOUBLE_COLON_LHS, sourcePsi.receiverExpression]?.type ?: return@lz null
        ktType.toPsiType(this, sourcePsi, boxed = true)
    }
    
    override val expression: UExpression?
        get() {
            if (type != null) return null
            val receiverExpression = sourcePsi.receiverExpression ?: return null
            return KotlinConverter.convertExpression(receiverExpression, this, DEFAULT_EXPRESSION_TYPES_LIST)
        }
}