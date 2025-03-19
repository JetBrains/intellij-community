// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.multiResolveResults

@ApiStatus.Internal
class KotlinDocUQualifiedReferenceExpression(
    override val sourcePsi: KDocName,
    private val givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UQualifiedReferenceExpression, UMultiResolvable {

    private val receiverPart = UastLazyPart<UExpression>()
    private val selectorPart = UastLazyPart<UExpression>()

    override val receiver: UExpression
        get() = receiverPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter
                .convertPsiElement(sourcePsi, givenParent, DEFAULT_EXPRESSION_TYPES_LIST) as? UExpression
                ?: UastEmptyExpression(givenParent)
        }

    override val selector: UExpression
        get() = selectorPart.getOrBuild {
            createKDocNameSimpleNameReference(parentKDocName = sourcePsi, givenParent = this) ?: UastEmptyExpression(this)
        }

    override val accessType = UastQualifiedExpressionAccessType.SIMPLE

    override fun resolve(): PsiElement? = sourcePsi.reference?.resolve()

    override val resolvedName: String?
        get() = (resolve() as? PsiNamedElement)?.name

    override fun multiResolve(): Iterable<ResolveResult> = sourcePsi.multiResolveResults().asIterable()
}
