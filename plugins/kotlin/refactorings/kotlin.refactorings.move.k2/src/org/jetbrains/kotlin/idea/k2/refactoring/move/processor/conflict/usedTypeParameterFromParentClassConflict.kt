// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor.ClassBody
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClass

internal fun checkUsedTypeParameterFromParentClassConflict(
    declarationsToMove: Iterable<KtNamedDeclaration>,
    target: K2MoveTargetDescriptor.Declaration<*>?
): MultiMap<PsiElement, String> {
    if (target !is ClassBody) return MultiMap.empty()
    val conflicts = MultiMap<PsiElement, String>()

    for (declarationToMove in declarationsToMove) {
        val containingClass = declarationToMove.containingClass() ?: continue
        analyze(declarationToMove) {
            val hasTypeParameterReference = declarationToMove.collectDescendantsOfType<KtTypeReference> { reference ->
                val typeParameterSymbol = (reference.type as? KaTypeParameterType)?.symbol
                typeParameterSymbol?.containingSymbol?.psi == containingClass
            }.isNotEmpty()

            if (hasTypeParameterReference) {
                val descr = RefactoringUIUtil.getDescription(declarationToMove, false)
                conflicts.putValue(declarationToMove, KotlinBundle.message("0.references.type.parameters.of.the.containing.class", descr))
            }
        }
    }
    return conflicts
}