// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinUIfExpression(
    override val sourcePsi: KtIfExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UIfExpression, KotlinUElementWithType, KotlinEvaluatableUElement {

    private val conditionPart = UastLazyPart<UExpression>()
    private val thenExpressionPart = UastLazyPart<UExpression?>()
    private val elseExpressionPart = UastLazyPart<UExpression?>()

    override val condition: UExpression
        get() = conditionPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.condition, this)
        }

    override val thenExpression: UExpression?
        get() = thenExpressionPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrNull(sourcePsi.then, this)
        }

    override val elseExpression: UExpression?
        get() = elseExpressionPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrNull(sourcePsi.`else`, this)
        }

    override val isTernary = false

    override val ifIdentifier: UIdentifier
        get() = UIdentifier(null, this)

    override val elseIdentifier: UIdentifier?
        get() = null
}
