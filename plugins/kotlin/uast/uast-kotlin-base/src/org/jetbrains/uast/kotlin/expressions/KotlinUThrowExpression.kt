// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinUThrowExpression(
    override val sourcePsi: KtThrowExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UThrowExpression, KotlinUElementWithType {

    private val thrownExpressionPart = UastLazyPart<UExpression>()

    override val thrownExpression: UExpression
        get() = thrownExpressionPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.thrownExpression, this)
        }
}
