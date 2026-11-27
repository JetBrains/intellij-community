// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.safeDelete

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.isSubtypeOf
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.conflicts.registerRetargetJobOnPotentialCandidates
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtParameter

internal fun collectParameterConflicts(
    parameter: KtParameter,
    deletedElements: Collection<PsiElement>,
    conflicts: MultiMap<PsiElement, @Nls String>,
) {
    val parameterName = parameter.name ?: return
    val owner = parameter.ownerDeclaration as? KtCallableDeclaration ?: return
    if (deletedElements.any { PsiTreeUtil.isAncestor(it, owner, false) }) return
    val name = owner.name ?: return
    val candidates = linkedSetOf<PsiElement>(owner)
    analyze(owner) {
        val initialSymbol = owner.symbol as? KaCallableSymbol ?: return@analyze
        val parameterSymbol = parameter.symbol
        registerRetargetJobOnPotentialCandidates(owner, name, {
            it is KaCallableSymbol && it.isAssignable(initialSymbol, parameterSymbol)
        }) {
            it.psi?.unwrapped?.let(candidates::add)
        }
    }
    if (candidates.size == 1) return

    conflicts.putValue(
        candidates.first { it != owner }, KotlinBundle.message("kotlin.refactoring.safeDelete.parameter.conflicts", parameterName)
    )
}

context(_: KaSession)
private fun KaCallableSymbol.isAssignable(initialSymbol: KaCallableSymbol, parameterSymbol: KaVariableSymbol): Boolean {

    fun signature(symbol: KaCallableSymbol): Triple<List<KaType>, List<KaType>, List<KaType>> {
        val initialValueParameters = ((symbol as? KaFunctionSymbol)?.valueParameters ?: emptyList())
            .filter { it != parameterSymbol }.map { it.returnType }
        val initialContextParameters = symbol.contextParameters
            .filter { it != parameterSymbol }.map { it.returnType }
        val initialReceiverParameter = listOfNotNull(symbol.receiverParameter?.takeUnless { it == parameterSymbol }?.returnType)
        return Triple(initialValueParameters, initialContextParameters, initialReceiverParameter)
    }

    val initialSignature = signature(initialSymbol)
    val signature = signature(this)

    var assignability: Boolean? = null
    fun compareTypes(left: List<KaType>, right: List<KaType>): Boolean {
        if (left.size != right.size) return false
        left.zip(right).forEach { (current, initial) ->
            val bool = if (initial.isSubtypeOf(current)) true else if (current.isSubtypeOf(initial)) false else return false
            if (assignability != null) {
                if (bool != assignability) return false
            } else {
                assignability = bool
            }
        }
        return true
    }
    if (!compareTypes(signature.first, initialSignature.first)) return false
    if (!compareTypes(signature.second, initialSignature.second)) return false
    if (!compareTypes(signature.third, initialSignature.third)) return false

    return true
}