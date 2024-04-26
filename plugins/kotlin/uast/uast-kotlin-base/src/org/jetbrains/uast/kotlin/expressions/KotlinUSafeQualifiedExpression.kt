// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.getResolveResultVariants

@ApiStatus.Internal
class KotlinUSafeQualifiedExpression(
    override val sourcePsi: KtSafeQualifiedExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UQualifiedReferenceExpression, UMultiResolvable,
    KotlinUElementWithType, KotlinEvaluatableUElement {

    private val receiverPart = UastLazyPart<UExpression>()
    private val selectorPart = UastLazyPart<UExpression>()

    override val receiver: UExpression
        get() = receiverPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(
                sourcePsi.receiverExpression,
                this
            )
        }

    override val selector: UExpression
        get() = selectorPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(
                sourcePsi.selectorExpression,
                this
            )
        }

    override val accessType = KotlinQualifiedExpressionAccessTypes.SAFE

    override val resolvedName: String?
        get() = (resolve() as? PsiNamedElement)?.name

    override fun resolve(): PsiElement? = sourcePsi.selectorExpression?.let { baseResolveProviderService.resolveToDeclaration(it) }

    override fun multiResolve(): Iterable<ResolveResult> =
        getResolveResultVariants(baseResolveProviderService, sourcePsi.selectorExpression)
}
