// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.toMultiMap
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.core.getFqNameByDirectory
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.internalUsageElements
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.internalUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.tryFindConflict
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.willBeMoved
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.willNotBeMoved
import org.jetbrains.kotlin.idea.refactoring.getContainer
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

private fun PsiElement.createVisibilityConflict(referencedDeclaration: PsiElement): Pair<PsiElement, String> {
    return this to KotlinBundle.message(
        "text.0.uses.1.which.will.be.inaccessible.after.move",
        RefactoringUIUtil.getDescription(getContainer(), false),
        RefactoringUIUtil.getDescription(referencedDeclaration, false)
    ).capitalizeAsciiOnly()
}

/**
 * If visibility isn't there before the refactoring starts, we don't report it as a conflict.
 */
private fun MoveRenameUsageInfo.isVisibleBeforeMove(): Boolean {
    return try {
        val declaration = upToDateReferencedElement as? PsiNamedElement ?: return false
        declaration.isVisibleTo(element ?: return false)
    } catch (e: Exception) {
        if (e is ControlFlowException) throw e
        fileLogger().error(e)
        return true
    }
}

private fun PsiNamedElement.isVisibleTo(usage: PsiElement): Boolean {
    return if (usage is KtElement) {
        analyze(usage) { isVisibleTo(usage) }
    } else {
        lightIsVisibleTo(usage)
    }
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun PsiNamedElement.isVisibleTo(usage: KtElement): Boolean {
    val file = (usage.containingFile as? KtFile)?.symbol ?: return false
    val symbol = if (this is KtNamedDeclaration) {
        symbol
    } else {
        if (this !is PsiMember) return false // get Java symbol through resolve because it is not possible through getSymbol
        usage.mainReference?.resolveToSymbol() as? KaDeclarationSymbol ?: return false
    }
    return isVisible(symbol, file, position = usage)
}

private fun PsiNamedElement.lightIsVisibleTo(usage: PsiElement): Boolean {
    val declarations = if (this is KtNamedDeclaration) toLightElements() else listOf(this)
    return declarations.all { lightDecl ->
        if (lightDecl !is PsiMember) return@all false
        JavaResolveUtil.isAccessible(lightDecl, lightDecl.containingClass, lightDecl.modifierList, usage, null, null)
    }
}

/**
 * Check whether the moved external usages are still visible towards their non-physical declaration.
 */
internal fun checkVisibilityConflictForNonMovedUsages(
    allDeclarationsToMove: Iterable<KtNamedDeclaration>,
    usages: List<MoveRenameUsageInfo>,
    targetDir: PsiDirectory
): MultiMap<PsiElement, String> {
    return usages
        .filter { usageInfo -> usageInfo.willNotBeMoved(allDeclarationsToMove) && usageInfo.isVisibleBeforeMove() }
        .mapNotNull { usageInfo ->
            tryFindConflict {
                val usageElement = usageInfo.element ?: return@tryFindConflict null
                val referencedDeclaration = usageInfo.upToDateReferencedElement as? KtNamedDeclaration ?: return@tryFindConflict null
                analyze(referencedDeclaration) {
                    val isVisible = when (referencedDeclaration.symbol.visibility) {
                        KaSymbolVisibility.PRIVATE -> false
                        KaSymbolVisibility.INTERNAL -> usageElement.module == targetDir.module
                        else -> true
                    }
                    if (!isVisible) usageElement.createVisibilityConflict(referencedDeclaration) else null
                }
            }
        }
        .toMultiMap()
}

/**
 * Check whether the moved internal usages are still visible towards their physical declaration.
 */
fun checkVisibilityConflictsForInternalUsages(
    topLevelDeclarationsToMove: Collection<KtNamedDeclaration>,
    allDeclarationsToMove: Collection<KtNamedDeclaration>,
    targetPkg: FqName,
    targetDir: PsiDirectory
): MultiMap<PsiElement, String> {
    return topLevelDeclarationsToMove
        .flatMap { it.internalUsageElements() }
        .mapNotNull { refExpr -> (refExpr.internalUsageInfo ?: return@mapNotNull null) }
        .filter { usageInfo -> !usageInfo.referencedElement.willBeMoved(allDeclarationsToMove) && usageInfo.isVisibleBeforeMove() }
        .mapNotNull { usageInfo ->
            tryFindConflict {
                val usageElement = usageInfo.element as? KtElement ?: return@tryFindConflict null
                val referencedDeclaration = usageInfo.upToDateReferencedElement as? PsiNamedElement ?: return@tryFindConflict null
                val isVisible = if (referencedDeclaration is KtNamedDeclaration) {
                    analyze(referencedDeclaration) {
                        when (referencedDeclaration.symbol.visibility) {
                            KaSymbolVisibility.PRIVATE -> false
                            KaSymbolVisibility.INTERNAL -> referencedDeclaration.module == targetDir.module
                            else -> true
                        }
                    }
                } else if (referencedDeclaration is PsiModifierListOwner) {
                    val modifierList = referencedDeclaration.modifierList ?: return@tryFindConflict null
                    when (PsiUtil.getAccessLevel(modifierList)) {
                        PsiUtil.ACCESS_LEVEL_PROTECTED, PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL -> {
                            if (referencedDeclaration is PsiMethod && referencedDeclaration.isConstructor) {
                                true // if a constructor is protected, it's accessible outside the package
                            } else {
                                val declFqn = (referencedDeclaration as PsiModifierListOwner).containingFile.getFqNameByDirectory()
                                declFqn == targetPkg
                            }
                        }
                        else -> true
                    }
                } else true
                if (!isVisible) usageElement.createVisibilityConflict(referencedDeclaration) else null
            }
        }.toMultiMap()
}