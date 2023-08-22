// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.ResolveResult
import com.intellij.psi.infos.CandidateInfo
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService

fun getResolveResultVariants(
    baseKotlinUastResolveProviderService: BaseKotlinUastResolveProviderService,
    ktExpression: KtExpression?
): Iterable<ResolveResult> {
    ktExpression ?: return emptyList()

    val referenceVariants = baseKotlinUastResolveProviderService.getReferenceVariants(ktExpression, ktExpression.name ?: ktExpression.text)

    return referenceVariants.mapNotNull { CandidateInfo(it, PsiSubstitutor.EMPTY) }.asIterable()
}

fun KtElement.multiResolveResults(): Sequence<ResolveResult> =
    references.asSequence().flatMap { ref ->
        when (ref) {
            is PsiPolyVariantReference -> ref.multiResolve(false).asSequence()
            else -> (ref.resolve()?.let { sequenceOf(CandidateInfo(it, PsiSubstitutor.EMPTY)) }).orEmpty()
        }
    }

class TypedResolveResult<T : PsiElement>(element: T) : CandidateInfo(element, PsiSubstitutor.EMPTY) {
    @Suppress("UNCHECKED_CAST")
    override fun getElement(): T = super.getElement() as T
}
