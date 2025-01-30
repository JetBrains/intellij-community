// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.willBeMoved
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClass

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
        val refElement = usage.element ?: continue
        if ((refElement as? KtElement)?.containingClass() == targetClass) continue
        if (!referencedElement.willBeMoved(allDeclarationsToMove)) continue

        val context = refElement.getUsageContext()
        val message = KotlinBundle.message(
            "0.in.1.will.require.class.instance",
            refElement.text,
            RefactoringUIUtil.getDescription(context, false)
        )
        conflicts.putValue(refElement, message)
    }
    return conflicts
}