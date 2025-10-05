// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.toMultiMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.isInternal
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.tryFindConflict
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo.Companion.internalUsageElements
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.usages.K2MoveRenameUsageInfo.Companion.internalUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.willNotBeMoved
import org.jetbrains.kotlin.idea.refactoring.getContainer
import org.jetbrains.kotlin.idea.refactoring.pullUp.willBeMoved
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.contains
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

private fun PsiElement.createAccessibilityConflictInternal(
    referencedDeclaration: PsiElement,
    targetModule: Module
): Pair<PsiElement, String> {
    return this to RefactoringBundle.message(
        "0.referenced.in.1.will.not.be.accessible.in.module.2",
        RefactoringUIUtil.getDescription(referencedDeclaration, true),
        RefactoringUIUtil.getDescription(getContainer(), true),
        CommonRefactoringUtil.htmlEmphasize(targetModule.name)
    ).capitalizeAsciiOnly()
}

private fun PsiElement.createAccessibilityConflictUnMoved(referencedDeclaration: PsiElement): Pair<PsiElement, String>? {
    return this to RefactoringBundle.message(
        "0.referenced.in.1.will.not.be.accessible.from.module.2",
        RefactoringUIUtil.getDescription(referencedDeclaration, true),
        RefactoringUIUtil.getDescription(getContainer(), true),
        CommonRefactoringUtil.htmlEmphasize(module?.name ?: return null)
    ).capitalizeAsciiOnly()
}

internal fun checkModuleDependencyConflictsForNonMovedUsages(
    allDeclarationsToMove: Iterable<KtNamedDeclaration>,
    usages: List<MoveRenameUsageInfo>,
    targetModule: PsiDirectory
): MultiMap<PsiElement, String> {
    return usages
        .filter { usageInfo -> usageInfo.willNotBeMoved(allDeclarationsToMove) }
        .mapNotNull { usageInfo ->
            tryFindConflict {
                val usageElement = usageInfo.element ?: return@tryFindConflict null
                val referencedDeclaration = usageInfo.upToDateReferencedElement as? KtNamedDeclaration ?: return@tryFindConflict null
                if (referencedDeclaration.isInternal) return@tryFindConflict null
                val resolveScope = usageElement.resolveScope
                if (resolveScope.isSearchInModuleContent(targetModule.module ?: return@tryFindConflict null, false)) return@tryFindConflict null
                usageElement.createAccessibilityConflictUnMoved(referencedDeclaration)
            }
        }.toMultiMap()
}

@ApiStatus.Internal
fun checkModuleDependencyConflictsForInternalUsages(
    topLevelMovedDeclarations: Iterable<KtNamedDeclaration>,
    allDeclarationsToMove: Iterable<KtNamedDeclaration>,
    target: PsiDirectory
): MultiMap<PsiElement, String> {
    return topLevelMovedDeclarations.flatMap { containing ->
        containing.internalUsageElements()
            .mapNotNull { refExpr -> refExpr.internalUsageInfo ?: return@mapNotNull null }
            .filter { usageInfo -> !usageInfo.referencedElement.willBeMoved(allDeclarationsToMove) }
            .mapNotNull { usageInfo ->
                tryFindConflict {
                    val usageElement = usageInfo.element ?: return@tryFindConflict null
                    val referencedDeclaration = usageInfo.upToDateReferencedElement as? KtNamedDeclaration ?: return@tryFindConflict null
                    if (referencedDeclaration.isInternal) return@tryFindConflict null
                    if (target.resolveScope.contains(referencedDeclaration.containingFile.virtualFile)) return@tryFindConflict null
                    val module = target.module ?: return@tryFindConflict null
                    if (ModuleType.isInternal(module)) return@tryFindConflict null
                    usageElement.createAccessibilityConflictInternal(referencedDeclaration, module)
                }
            }
    }.toMultiMap()
}