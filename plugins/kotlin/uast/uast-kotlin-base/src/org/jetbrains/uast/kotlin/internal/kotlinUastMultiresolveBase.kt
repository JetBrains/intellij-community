package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.ResolveResult
import com.intellij.psi.infos.CandidateInfo
import org.jetbrains.kotlin.psi.KtElement

fun KtElement.multiResolveResults(): Sequence<ResolveResult> =
    references.asSequence().flatMap { ref ->
        when (ref) {
            is PsiPolyVariantReference -> ref.multiResolve(false).asSequence()
            else -> (ref.resolve()?.let { sequenceOf(CandidateInfo(it, PsiSubstitutor.EMPTY)) }).orEmpty()
        }
    }
