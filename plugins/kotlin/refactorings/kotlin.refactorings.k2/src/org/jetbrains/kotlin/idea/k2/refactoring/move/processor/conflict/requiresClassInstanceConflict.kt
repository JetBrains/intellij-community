// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo
import org.jetbrains.kotlin.idea.refactoring.pullUp.willBeMoved
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration

internal fun checkRequiresClassInstanceConflict(
    usages: List<MoveRenameUsageInfo>,
    allDeclarationsToMove: Collection<KtNamedDeclaration>,
    target: K2MoveTargetDescriptor.Declaration<*>? = null
): MultiMap<PsiElement, String> {
    if (target !is K2MoveTargetDescriptor.ClassOrObject) return MultiMap.empty()
    val targetClass = target.getTarget() as? KtClass ?: return MultiMap.empty()

    val conflicts = MultiMap<PsiElement, String>()
    for (usage in usages.filterIsInstance<K2MoveRenameUsageInfo>()) {
        val referencedElement = usage.upToDateReferencedElement as? KtNamedDeclaration ?: continue
        if (referencedElement is KtClass) continue
        val usageElement = usage.element ?: continue
        if (isClassAvailableAsImplicitReceiver(referencedElement, usageElement, targetClass)) continue
        if (!referencedElement.willBeMoved(allDeclarationsToMove)) continue

        val context = usageElement.getUsageContext()
        val message = KotlinBundle.message(
            "0.in.1.will.require.class.instance",
            usageElement.text,
            RefactoringUIUtil.getDescription(context, false)
        )
        conflicts.putValue(usageElement, message)
    }
    return conflicts
}

private fun isClassAvailableAsImplicitReceiver(
    referencedElement: KtNamedDeclaration,
    usageElement: PsiElement,
    targetClass: KtClass,
): Boolean {
    return usageElement is KtElement && analyze(usageElement) {
        val targetClassId = targetClass.classSymbol?.classId ?: return@analyze false
        if (isExtensionForTargetClass(referencedElement, targetClassId)) return@analyze true
        collectImplicitReceiverTypes(usageElement).any { implicitReceiverType ->
            implicitReceiverType.isSubtypeOf(targetClassId)
        }
    }
}

private fun KaSession.isExtensionForTargetClass(
    referencedElement: KtNamedDeclaration,
    targetClassId: ClassId
): Boolean {
    if (referencedElement is KtCallableDeclaration && referencedElement.receiverTypeReference != null) {
        val receiverClassId = (referencedElement.symbol as? KaCallableSymbol)?.receiverType?.expandedSymbol?.classId
        if (receiverClassId == targetClassId) return true
    }
    return false
}
