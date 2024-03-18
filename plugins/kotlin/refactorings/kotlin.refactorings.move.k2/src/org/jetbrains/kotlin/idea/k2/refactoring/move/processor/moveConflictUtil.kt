// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.toMultiMap
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.project.structure.DanglingFileResolutionMode
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.internalUsageInfo
import org.jetbrains.kotlin.idea.refactoring.getContainer
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

/**
 * Find all conflicts when moving elements as described by [descriptor].
 */
@OptIn(KtAllowAnalysisOnEdt::class)
internal fun findAllMoveConflicts(
    descriptor: K2MoveDescriptor.Declarations,
    usages: List<MoveRenameUsageInfo>
): MultiMap<PsiElement, String> = allowAnalysisOnEdt {
    val (fakeTarget, oldToNewMap) = descriptor.createCopyTarget()
    MultiMap<PsiElement, String>().apply {
        putAllValues(checkVisibilityConflictsForInternalUsages(descriptor, fakeTarget))
        putAllValues(checkVisibilityConflictForNonMovedUsages(descriptor, oldToNewMap, usages))
    }
}

/**
 * Creates a non-physical file that contains the moved elements with all references retargeted.
 * This non-physical file can be used to analyze for conflicts without modifying the file on the disk.
 */
private fun K2MoveDescriptor.Declarations.createCopyTarget(): Pair<KtFile, Map<KtNamedDeclaration, KtNamedDeclaration>> {
    /** Collects physical to non-physical usage-infos. */
    fun KtFile.collectOldToNewUsageInfos(oldToNewMap: Map<KtNamedDeclaration, KtNamedDeclaration>): List<Pair<K2MoveRenameUsageInfo, K2MoveRenameUsageInfo>> {
        return collectDescendantsOfType<KtSimpleNameExpression>().mapNotNull { refExpr ->
            val usageInfo = refExpr.internalUsageInfo
            val referencedElement = (usageInfo as? K2MoveRenameUsageInfo.Source)?.referencedElement ?: return@mapNotNull null
            val newReferencedElement = oldToNewMap[referencedElement] ?: referencedElement
            if (!newReferencedElement.isValid || newReferencedElement !is PsiNamedElement) return@mapNotNull null
            usageInfo to usageInfo.refresh(refExpr, newReferencedElement)
        }
    }

    val fakeTargetFile = KtPsiFactory.contextual(target.baseDirectory)
        .createFile(target.fileName, "package ${target.pkgName.quoteIfNeeded()}\n")
    val oldToNewMap = source.moveInto(fakeTargetFile)
    val usageInfos = fakeTargetFile.collectOldToNewUsageInfos(oldToNewMap)
    usageInfos.forEach { (originalUsageInfo, copyUsageInfo) ->
        // Retarget all references to make sure all references are resolvable after moving
        val retargetResult = copyUsageInfo.retarget(copyUsageInfo.referencedElement as PsiNamedElement) as? KtElement ?: return@forEach
        val retargetReference = retargetResult.getCalleeExpressionIfAny() as? KtSimpleNameExpression ?: return@forEach
        // Attach physical usage info to the copied reference.
        // This will make it possible for the conflict checker to check whether a conflict exists before even calling the refactoring.
        retargetReference.internalUsageInfo = originalUsageInfo
    }
    fakeTargetFile.originalFile = source.elements.firstOrNull()?.containingKtFile ?: error("Moved element is not in a Kotlin file")
    return fakeTargetFile to oldToNewMap
}


private fun PsiElement?.willBeMoved(source: K2MoveSourceDescriptor<*>): Boolean {
    return this != null && source.elements.any { it.isAncestor(this, false) }
}

private fun MoveRenameUsageInfo.willNotBeMoved(source: K2MoveSourceDescriptor<*>): Boolean {
    return this !is K2MoveRenameUsageInfo || !element.willBeMoved(source)
}

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
    val declaration = upToDateReferencedElement as? KtNamedDeclaration ?: return false
    return declaration.kotlinIsVisibleTo(element ?: return false)
}

private fun KtNamedDeclaration.kotlinIsVisibleTo(usage: PsiElement): Boolean {
    return if (usage is KtElement) {
        when {
            !isPhysical -> analyzeCopy(this, DanglingFileResolutionMode.PREFER_SELF) { kotlinIsVisibleTo(usage) }
            !usage.isPhysical -> analyzeCopy(usage, DanglingFileResolutionMode.PREFER_SELF) { kotlinIsVisibleTo(usage) }
            else -> analyze(this) { kotlinIsVisibleTo(usage) }
        }
    } else {
        lightIsVisibleTo(usage)
    }
}

context(KtAnalysisSession)
private fun KtNamedDeclaration.kotlinIsVisibleTo(usage: PsiElement): Boolean {
    val file = (usage.containingFile as? KtFile)?.getFileSymbol() ?: return false
    val symbol = getSymbol()
    if (symbol !is KtSymbolWithVisibility) return false
    return isVisible(symbol, file, position = usage)
}

private fun KtNamedDeclaration.lightIsVisibleTo(usage: PsiElement): Boolean {
    return toLightElements().all { lightDecl ->
        if (lightDecl !is PsiMember) return@all false
        JavaResolveUtil.isAccessible(lightDecl, lightDecl.containingClass, lightDecl.modifierList, usage, null, null)
    }
}

/**
 * Check whether the moved external usages are still visible towards their non-physical declaration.
 */
private fun checkVisibilityConflictForNonMovedUsages(
    descriptor: K2MoveDescriptor,
    oldToNewMap: Map<KtNamedDeclaration, KtNamedDeclaration>,
    usages: List<MoveRenameUsageInfo>
): MultiMap<PsiElement, String> {
    return usages
        .filter { usage -> usage.willNotBeMoved(descriptor.source) && usage.isVisibleBeforeMove() }
        .mapNotNull { usage ->
            val usageElement = usage.element ?: return@mapNotNull null
            val referencedDeclaration = usage.upToDateReferencedElement as? KtNamedDeclaration ?: return@mapNotNull null
            val declarationCopy = oldToNewMap[referencedDeclaration] ?: return@mapNotNull null
            val isVisible = declarationCopy.kotlinIsVisibleTo(usageElement)
            if (!isVisible) usageElement.createVisibilityConflict(referencedDeclaration) else null
        }.toMultiMap()
}

/**
 * Check whether the moved internal usages are still visible towards their physical declaration.
 */
private fun checkVisibilityConflictsForInternalUsages(
    descriptor: K2MoveDescriptor,
    fakeTarget: KtFile
): MultiMap<PsiElement, String> {
    return fakeTarget
        .collectDescendantsOfType<KtSimpleNameExpression>()
        .mapNotNull { refExprCopy -> (refExprCopy.internalUsageInfo ?: return@mapNotNull null) to refExprCopy }
        .filter { (usageInfo, _) -> !usageInfo.referencedElement.willBeMoved(descriptor.source) && usageInfo.isVisibleBeforeMove() }
        .mapNotNull { (usageInfo, refExprCopy) ->
            val usageElement = usageInfo.element as? KtElement ?: return@mapNotNull null
            val referencedDeclaration = usageInfo.upToDateReferencedElement as? KtNamedDeclaration ?: return@mapNotNull null
            val isVisible = referencedDeclaration.kotlinIsVisibleTo(refExprCopy)
            if (!isVisible) usageElement.createVisibilityConflict(referencedDeclaration) else null
        }.toMultiMap()
}

