// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinUParenthesizedExpression(
    override val sourcePsi: KtParenthesizedExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UParenthesizedExpression, KotlinUElementWithType {

    private val expressionPart = UastLazyPart<UExpression>()

    override val expression: UExpression
        get() = expressionPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.expression, this)
        }
}
