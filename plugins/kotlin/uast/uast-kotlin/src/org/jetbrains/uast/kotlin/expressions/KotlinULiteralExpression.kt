// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.kotlin.internal.KotlinFakeUElement
import org.jetbrains.uast.wrapULiteral

class KotlinULiteralExpression(
    override val sourcePsi: KtConstantExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), ULiteralExpression, KotlinUElementWithType, KotlinEvaluatableUElement, KotlinFakeUElement {
    override val isNull: Boolean
        get() = sourcePsi.unwrapBlockOrParenthesis().node?.elementType == KtNodeTypes.NULL

    override val value by lz { evaluate() }

    override fun unwrapToSourcePsi(): List<PsiElement> = listOfNotNull(wrapULiteral(this).sourcePsi)
}

class KotlinStringULiteralExpression(
    override val sourcePsi: PsiElement,
    givenParent: UElement?,
    val text: String
) : KotlinAbstractUExpression(givenParent), ULiteralExpression, KotlinUElementWithType, KotlinFakeUElement {
    constructor(psi: PsiElement, uastParent: UElement?)
            : this(psi, uastParent, if (psi is KtEscapeStringTemplateEntry) psi.unescapedValue else psi.text)

    override val value: String
        get() = text

    override fun evaluate() = value

    override fun getExpressionType(): PsiType? = PsiType.getJavaLangString(sourcePsi.manager, sourcePsi.resolveScope)

    override fun unwrapToSourcePsi(): List<PsiElement> = listOfNotNull(wrapULiteral(this).sourcePsi)
}
