// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.mutability

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull

@K1Deprecation
class CallResolver(private val fqNames: Set<FqName>) {
    private val shortNames = fqNames.map { it.shortName().identifier }.toSet()

    private fun CallableDescriptor.isMutatorCall(): Boolean {
        if (fqNameOrNull() in fqNames) return true
        return overriddenDescriptors.any { it.isMutatorCall() }
    }

    fun isNeededCall(expression: KtExpression, resolutionFacade: ResolutionFacade): Boolean {
        val shortName = when (expression) {
            is KtCallExpression -> expression.calleeExpression?.text
            is KtReferenceExpression -> expression.text
            else -> null
        } ?: return false
        if (shortName !in shortNames) return false
        val call = expression.resolveToCall(resolutionFacade) ?: return false
        return call.candidateDescriptor.isMutatorCall()
    }
}