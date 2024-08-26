// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.toMultiMap
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.internalUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.containingCopyDecl
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.tryFindConflict
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.willBeMoved
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.willNotBeMoved
import org.jetbrains.kotlin.idea.refactoring.getContainer
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClass
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
        kotlinIsVisibleTo(usage)
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

private fun PsiNamedElement.kotlinIsVisibleTo(usage: KtElement) = when {
    !isPhysical && this is KtNamedDeclaration -> analyzeCopy(this, KaDanglingFileResolutionMode.PREFER_SELF) { isVisibleTo(usage) }
    !usage.isPhysical -> analyzeCopy(usage, KaDanglingFileResolutionMode.PREFER_SELF) { isVisibleTo(usage) }
    else -> analyze(usage) { isVisibleTo(usage) }
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
    oldToNewMap: Map<KtNamedDeclaration, KtNamedDeclaration>,
    usages: List<MoveRenameUsageInfo>
): MultiMap<PsiElement, String> {
    return usages
        .filter { usage -> usage.willNotBeMoved(allDeclarationsToMove) && usage.isVisibleBeforeMove() }
        .mapNotNull { usage ->
            tryFindConflict {
                val usageElement = usage.element ?: return@tryFindConflict null
                val referencedDeclaration = usage.upToDateReferencedElement as? KtNamedDeclaration ?: return@tryFindConflict null
                if (referencedDeclaration.isMemberThatCanBeSkipped()) return@tryFindConflict null
                val declarationCopy = containingCopyDecl(referencedDeclaration, oldToNewMap) ?: return@tryFindConflict null
                val isVisible = declarationCopy.isVisibleTo(usageElement)
                if (!isVisible) usageElement.createVisibilityConflict(referencedDeclaration) else null
            }
        }
        .toMultiMap()
}

/**
 * Checks whether checking visibility for a usage to [this] declaration can be skipped.
 * ```
 * open class Parent { fun bar() { } }
 *
 * class Child : Parent {
 *   fun foo() { bar() }
 * }
 * ```
 * In this example, checking whether `bar` is visible after moving `Parent` can only be done when updating the usage in the super type list
 * first.
 * Therefore, we try to skip visibility conflict checking for members
 */
private fun KtNamedDeclaration.isMemberThatCanBeSkipped(): Boolean {
    if (containingClass() == null) return false
    analyze(this) {
        val visibility = symbol.visibility
        if (visibility == KaSymbolVisibility.PUBLIC || visibility == KaSymbolVisibility.PROTECTED) return true
    }
    return false
}

/**
 * Check whether the moved internal usages are still visible towards their physical declaration.
 */
fun checkVisibilityConflictsForInternalUsages(
    allDeclarationsToMove: Iterable<KtNamedDeclaration>,
    fakeTarget: KtFile
): MultiMap<PsiElement, String> {
    return fakeTarget
        .collectDescendantsOfType<KtSimpleNameExpression>()
        .mapNotNull { refExprCopy -> (refExprCopy.internalUsageInfo ?: return@mapNotNull null) to refExprCopy }
        .filter { (usageInfo, _) -> !usageInfo.referencedElement.willBeMoved(allDeclarationsToMove) && usageInfo.isVisibleBeforeMove() }
        .mapNotNull { (usageInfo, refExprCopy) ->
            tryFindConflict {
                val referencedDeclaration = usageInfo.upToDateReferencedElement as? PsiNamedElement ?: return@tryFindConflict null
                val isVisible = referencedDeclaration.isVisibleTo(refExprCopy)
                if (!isVisible) {
                    val usageElement = usageInfo.element as? KtElement ?: return@tryFindConflict null
                    usageElement.createVisibilityConflict(referencedDeclaration)
                } else null
            }
        }.toMultiMap()
}
