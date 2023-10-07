// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinULabeledExpression(
    override val sourcePsi: KtLabeledExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), ULabeledExpression {

    private val expressionPart = UastLazyPart<UExpression>()

    override val label: String
        get() = sourcePsi.getLabelName().orAnonymous("label")

    override val labelIdentifier: UIdentifier?
        get() = sourcePsi.getTargetLabel()?.let { KotlinUIdentifier(it, this) }

    override val expression: UExpression
        get() = expressionPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.baseExpression, this)
        }

    override fun getExpressionType(): PsiType? {
        return expression.getExpressionType()
    }
}
