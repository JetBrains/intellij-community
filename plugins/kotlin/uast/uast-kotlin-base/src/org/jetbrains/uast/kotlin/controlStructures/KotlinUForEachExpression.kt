// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter
import org.jetbrains.uast.psi.UastPsiParameterNotResolved

@ApiStatus.Internal
class KotlinUForEachExpression(
    override val sourcePsi: KtForExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UForEachExpression {

    private val iteratedValuePart = UastLazyPart<UExpression>()
    private val bodyPart = UastLazyPart<UExpression>()
    private val variablePart = UastLazyPart<KotlinUParameter>()

    override val iteratedValue: UExpression
        get() = iteratedValuePart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.loopRange, this)
        }

    override val body: UExpression
        get() = bodyPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.body, this)
        }

    override val variable: KotlinUParameter
        get() = variablePart.getOrBuild {
            val parameter = sourcePsi.loopParameter?.let { UastKotlinPsiParameter.create(it, sourcePsi, this, 0) }
                ?: UastPsiParameterNotResolved(sourcePsi, KotlinLanguage.INSTANCE)
            KotlinUParameter(parameter, sourcePsi, this)
        }

    override val parameter: UParameter
        get() = variable

    override val forIdentifier: UIdentifier
        get() = KotlinUIdentifier(null, this)
}
