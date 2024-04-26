// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinUArrayAccessExpression(
    override val sourcePsi: KtArrayAccessExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UArrayAccessExpression, KotlinUElementWithType, KotlinEvaluatableUElement {

    private val receiverPart = UastLazyPart<UExpression>()
    private val indicesPart = UastLazyPart<List<UExpression>>()

    override val receiver: UExpression
        get() = receiverPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.arrayExpression, this)
        }

    override val indices: List<UExpression>
        get() = indicesPart.getOrBuild {
            sourcePsi.indexExpressions.map {
                baseResolveProviderService.baseKotlinConverter.convertOrEmpty(it, this)
            }
        }

    override fun resolve(): PsiElement? {
        return baseResolveProviderService.resolveCall(sourcePsi)
    }
}
