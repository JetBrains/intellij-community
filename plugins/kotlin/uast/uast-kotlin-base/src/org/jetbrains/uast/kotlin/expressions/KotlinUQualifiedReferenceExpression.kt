// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveResult
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve
import org.jetbrains.uast.kotlin.internal.multiResolveResults

class KotlinUQualifiedReferenceExpression(
    override val sourcePsi: KtDotQualifiedExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UQualifiedReferenceExpression, DelegatedMultiResolve,
    KotlinUElementWithType, KotlinEvaluatableUElement {
    override val receiver by lz { baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.receiverExpression, this) }
    override val selector by lz { baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.selectorExpression, this) }
    override val accessType = UastQualifiedExpressionAccessType.SIMPLE

    override fun resolve(): PsiElement? = sourcePsi.selectorExpression?.let { baseResolveProviderService.resolveToDeclaration(it) }

    override val resolvedName: String?
        get() = (resolve() as? PsiNamedElement)?.name

    override val referenceNameElement: UElement?
        get() = when (val selector = selector) {
            is UCallExpression -> selector.methodIdentifier
            else -> super.referenceNameElement
        }
}


class KotlinDocUQualifiedReferenceExpression(
    override val sourcePsi: KDocName,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UQualifiedReferenceExpression, UMultiResolvable {

    override val receiver by lz {
        KotlinConverter.convertPsiElement(sourcePsi, givenParent, DEFAULT_EXPRESSION_TYPES_LIST)
                as? UExpression ?: UastEmptyExpression(givenParent)
    }
    override val selector by lz {
        createKDocNameSimpleNameReference(parentKDocName = sourcePsi, givenParent = this) ?: UastEmptyExpression(this)
    }
    override val accessType = UastQualifiedExpressionAccessType.SIMPLE

    override fun resolve(): PsiElement? = sourcePsi.reference?.resolve()

    override val resolvedName: String?
        get() = (resolve() as? PsiNamedElement)?.name

    override fun multiResolve(): Iterable<ResolveResult> = sourcePsi.multiResolveResults().asIterable()
}