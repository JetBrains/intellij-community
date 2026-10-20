// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Reports `super` expressions for move to companions.
 *
 * No conflict is reported when `super` refers to another class or object (e.g., anonymous object expression in the moved declaration).
 */
internal fun checkSuperReferenceConflict(
    declarationsToMove: Iterable<KtNamedDeclaration>,
    target: K2MoveTargetDescriptor.Declaration<*>?
): MultiMap<PsiElement, String> {
    if (target !is K2MoveTargetDescriptor.CompanionObject &&
        target !is K2MoveTargetDescriptor.CompanionBlock &&
        target !is K2MoveTargetDescriptor.CompanionExtension
    ) return MultiMap.empty()

    val conflicts = MultiMap<PsiElement, String>()
    for (declarationToMove in declarationsToMove) {
        val containingClass = declarationToMove.containingClassOrObject ?: continue
        declarationToMove.forEachDescendantOfType<KtSuperExpression> { superExpression ->
            if (superExpression.findTargetClass() != containingClass) return@forEachDescendantOfType
            val access = superExpression.getQualifiedExpressionForReceiver() ?: superExpression
            conflicts.putValue(
                access,
                KotlinBundle.message("text.super.member.access.will.not.be.processed.0", access.text)
            )
        }
    }
    return conflicts
}

private fun KtSuperExpression.findTargetClass(): KtClassOrObject? {
    val label = getLabelName()
    return generateSequence(getStrictParentOfType<KtClassOrObject>()) { it.getStrictParentOfType<KtClassOrObject>() }
        .firstOrNull { label == null || it.name == label }
}
