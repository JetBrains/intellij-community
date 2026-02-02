// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.uast.DEFAULT_EXPRESSION_TYPES_LIST
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMultiResolvable
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.internal.getResolveResultVariants

@ApiStatus.Internal
class KotlinUCallableReferenceExpression(
    override val sourcePsi: KtCallableReferenceExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UCallableReferenceExpression, UMultiResolvable, KotlinUElementWithType {

    private val qualifierExpressionPart = UastLazyPart<UExpression?>()
    private val qualifierTypePart = UastLazyPart<PsiType?>()

    override val qualifierExpression: UExpression?
        get() = qualifierExpressionPart.getOrBuild {
            val receiverExpression = sourcePsi.receiverExpression ?: return@getOrBuild null
            baseResolveProviderService.baseKotlinConverter.convertExpression(receiverExpression, this, DEFAULT_EXPRESSION_TYPES_LIST)
        }

    override val qualifierType: PsiType?
        get() = qualifierTypePart.getOrBuild {
            baseResolveProviderService.getDoubleColonReceiverType(sourcePsi, this)
        }

    override val callableName: String
        get() = sourcePsi.callableReference.getReferencedName()

    override val resolvedName: String?
        get() = (resolve() as? PsiNamedElement)?.name

    override fun resolve(): PsiElement? = baseResolveProviderService.resolveToDeclaration(sourcePsi.callableReference)

    override fun multiResolve(): Iterable<ResolveResult> =
        getResolveResultVariants(baseResolveProviderService, sourcePsi.callableReference)
}
