// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.toMultiMap
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.*
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.internalUsageInfo
import org.jetbrains.kotlin.idea.refactoring.getContainer
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
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
    val module = containingModule() ?: return null
    return this to RefactoringBundle.message(
        "0.referenced.in.1.will.not.be.accessible.from.module.2",
        RefactoringUIUtil.getDescription(referencedDeclaration, true),
        RefactoringUIUtil.getDescription(getContainer(), true),
        CommonRefactoringUtil.htmlEmphasize(module.name)
    ).capitalizeAsciiOnly()
}

internal fun checkModuleDependencyConflictsForNonMovedUsages(
    allDeclarationsToMove: Iterable<KtNamedDeclaration>,
    oldToNewMap: Map<KtNamedDeclaration, KtNamedDeclaration>,
    usages: List<MoveRenameUsageInfo>
): MultiMap<PsiElement, String> {
    return usages
        .filter { usage -> usage.willNotBeMoved(allDeclarationsToMove) }
        .mapNotNull { usage ->
            tryFindConflict {
                val usageElement = usage.element ?: return@tryFindConflict null
                val referencedDeclaration = usage.upToDateReferencedElement as? KtNamedDeclaration ?: return@tryFindConflict null
                if (referencedDeclaration.isInternal) return@tryFindConflict null
                val declarationCopy = containingCopyDecl(referencedDeclaration, oldToNewMap) ?: return@tryFindConflict null
                val targetModule = declarationCopy.containingModule() ?: return@tryFindConflict null
                val resolveScope = usageElement.resolveScope
                if (resolveScope.isSearchInModuleContent(targetModule, false)) return@tryFindConflict null
                usageElement.createAccessibilityConflictUnMoved(referencedDeclaration)
            }
        }.toMultiMap()
}

fun checkModuleDependencyConflictsForInternalUsages(
    allDeclarationsToMove: Iterable<KtNamedDeclaration>,
    fakeTarget: KtFile
): MultiMap<PsiElement, String> {
    return fakeTarget
        .collectDescendantsOfType<KtSimpleNameExpression>()
        .mapNotNull { refExprCopy -> (refExprCopy.internalUsageInfo ?: return@mapNotNull null) to refExprCopy }
        .filter { (usageInfo, _) -> !usageInfo.referencedElement.willBeMoved(allDeclarationsToMove) }
        .mapNotNull { (usageInfo, refExprCopy) ->
            tryFindConflict {
                val usageElement = usageInfo.element ?: return@tryFindConflict null
                val referencedDeclaration = usageInfo.upToDateReferencedElement as? PsiNamedElement ?: return@tryFindConflict null
                if (referencedDeclaration is KtNamedDeclaration && referencedDeclaration.isInternal) return@tryFindConflict null
                analyzeCopy(refExprCopy, KaDanglingFileResolutionMode.PREFER_SELF) {
                    if (refExprCopy.mainReference.resolveToSymbol() == null) {
                        val module = refExprCopy.containingModule() ?: return@analyzeCopy null
                        usageElement.createAccessibilityConflictInternal(referencedDeclaration, module)
                    } else null
                }
            }
        }.toMultiMap()
}