// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.internal.KotlinFakeUElement
import org.jetbrains.uast.wrapULiteral

@ApiStatus.Internal
class KotlinULiteralExpression(
    override val sourcePsi: KtConstantExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), ULiteralExpression, KotlinUElementWithType, KotlinEvaluatableUElement, KotlinFakeUElement {

    private val valuePart = UastLazyPart<Any?>()

    override val isNull: Boolean
        get() = sourcePsi.unwrapBlockOrParenthesis().node?.elementType == KtNodeTypes.NULL

    override fun getExpressionType(): PsiType? {
        // `null` of `kotlin.Nothing?` type would be mapped to `java.lang.Void` without this shortcut.
        return if (isNull)
            PsiTypes.nullType()
        else
            super<KotlinUElementWithType>.getExpressionType()
    }

    override val value: Any?
        get() = valuePart.getOrBuild { evaluate() }

    override fun unwrapToSourcePsi(): List<PsiElement> = listOfNotNull(wrapULiteral(this).sourcePsi)
}
