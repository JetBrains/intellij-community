// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.ResolveResult
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMultiResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.kotlin.internal.multiResolveResults

class KotlinStringUSimpleReferenceExpression(
    override val identifier: String,
    givenParent: UElement?,
    override val sourcePsi: PsiElement? = null,
    private val referenceAnchor: KtElement? = null
) : KotlinAbstractUExpression(givenParent), USimpleNameReferenceExpression, UMultiResolvable {
    override val psi: PsiElement?
        get() = null

    private val resolved by lz { referenceAnchor?.references?.singleOrNull()?.resolve() }

    override fun resolve() = resolved

    override val resolvedName: String
        get() = (resolved as? PsiNamedElement)?.name ?: identifier

    override fun multiResolve(): Iterable<ResolveResult> = referenceAnchor?.multiResolveResults().orEmpty().asIterable()
}

fun createKDocNameSimpleNameReference(parentKDocName: KDocName, givenParent: UElement?): USimpleNameReferenceExpression? =
    parentKDocName.lastChild?.let { psiIdentifier ->
        parentKDocName.getQualifiedName().lastOrNull()?.let { qualifierText ->
            KotlinStringUSimpleReferenceExpression(qualifierText, givenParent, psiIdentifier, parentKDocName)
        }
    }
