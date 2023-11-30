// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinUWhileExpression(
    override val sourcePsi: KtWhileExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UWhileExpression {
    private val conditionPart = UastLazyPart<UExpression>()
    private val bodyPart = UastLazyPart<UExpression>()

    override val condition: UExpression
        get() = conditionPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.condition, this)
        }

    override val body: UExpression
        get() = bodyPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.body, this)
        }

    override val whileIdentifier: UIdentifier
        get() = KotlinUIdentifier(null, this)
}
