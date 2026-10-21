// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.psi.PsiElement
import com.intellij.psi.util.isAncestor
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbol
import org.jetbrains.kotlin.analysis.api.resolution.simple
import org.jetbrains.kotlin.analysis.api.resolution.single
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolution.KtResolvableCall

internal fun collectSuperCallConflicts(
    declaration: KtDeclaration,
    usages: Array<out UsageInfo>,
    conflicts: MultiMap<PsiElement, String>,
) {
    val superClasses = declaration.collectDescendantsOfType<KtSuperExpression>().mapNotNull { expression ->
        val owner = analyze(expression) {
            expression.getTargetLabel()?.resolveSuccessfulSymbol()?.psi as? KtClassOrObject
        } ?: expression.getStrictParentOfType<KtClassOrObject>() ?: return@mapNotNull null
        if (!declaration.isAncestor(owner)) owner else null
    }.distinct()
    if (superClasses.isEmpty()) return

    for (usage in usages) {
        val expression = usage.element as? KtReferenceExpression ?: continue
        val canKeepSuper = superClasses.all { owner ->
            expression.getStrictParentOfType<KtClassOrObject>() == owner && analyze(expression) {
                (expression as? KtResolvableCall)?.tryResolveCall()?.single?.simple?.dispatchReceiver.isReceiverOf(owner)
            }
        }
        if (!canKeepSuper) {
            conflicts.putValue(expression, KotlinBundle.message("text.inline.super.call.conflict"))
        }
    }
}

context(_: KaSession)
private fun KaReceiverValue?.isReceiverOf(owner: KtClassOrObject): Boolean = when (this) {
    is KaImplicitReceiverValue -> symbol.psi == owner
    is KaExplicitReceiverValue -> {
        val receiver = KtPsiUtil.deparenthesize(expression) as? KtThisExpression
        receiver?.instanceReference?.resolveSuccessfulSymbol()?.psi == owner
    }
    is KaSmartCastedReceiverValue -> original.isReceiverOf(owner)
    null -> false
}
