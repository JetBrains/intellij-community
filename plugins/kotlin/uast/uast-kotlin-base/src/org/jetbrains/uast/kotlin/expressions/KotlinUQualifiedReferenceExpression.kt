// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve

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
