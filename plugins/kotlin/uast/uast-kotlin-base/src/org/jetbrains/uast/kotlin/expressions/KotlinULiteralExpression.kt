// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.KotlinFakeUElement

@ApiStatus.Internal
class KotlinULiteralExpression(
    override val sourcePsi: KtConstantExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), ULiteralExpression, KotlinUElementWithType, KotlinEvaluatableUElement, KotlinFakeUElement {

    private val valuePart = UastLazyPart<Any?>()

    override val isNull: Boolean
        get() = sourcePsi.unwrapBlockOrParenthesis().node?.elementType == KtNodeTypes.NULL

    override val value: Any?
        get() = valuePart.getOrBuild { evaluate() }

    override fun unwrapToSourcePsi(): List<PsiElement> = listOfNotNull(wrapULiteral(this).sourcePsi)
}
