// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
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
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.internalUsageInfo
import org.jetbrains.kotlin.idea.refactoring.getContainer
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

/**
 * Find all conflicts when moving elements for a multi file move.
 */
internal fun findAllMoveConflicts(
    filesToMove: Set<KtFile>,
    targetPkg: FqName,
    usages: List<MoveRenameUsageInfo>
): MultiMap<PsiElement, String> {
    val filesByDir = filesToMove.groupBy { it.containingDirectory }
    return MultiMap<PsiElement, String>().apply {
        filesByDir.forEach { (dir, files) ->
            if (dir == null) return@forEach
            putAllValues(findAllMoveConflicts(files.toSet(), dir, targetPkg, usages))
        }
    }
}

/**
 * Find all conflicts when moving elements for a multi file move.
 */
internal fun findAllMoveConflicts(
    filesToMove: Set<KtFile>,
    targetDir: PsiDirectory,
    targetPkg: FqName,
    usages: List<MoveRenameUsageInfo>
): MultiMap<PsiElement, String> {
    val allDeclarationsToMove = filesToMove.flatMap { it.declarations }.filterIsInstance<KtNamedDeclaration>().toSet()
    return MultiMap<PsiElement, String>().apply {
        filesToMove.forEach { file ->
            val declarations = file.declarations.filterIsInstance<KtNamedDeclaration>()
            if (declarations.isEmpty()) return@forEach
            val externalUsages = usages.filter { usage -> usage.referencedElement in declarations }
            putAllValues(findAllMoveConflicts(declarations.toSet(), allDeclarationsToMove, targetDir, targetPkg, file.name, externalUsages))
        }
    }
}

/**
 * Find all conflicts when moving elements.
 * @param declarationsToCheck the set of declarations to move, they must all be moved from the same containing file.
 * @param allDeclarationsToMove all declarations that will be moved.
 */
@OptIn(KtAllowAnalysisOnEdt::class)
internal fun findAllMoveConflicts(
    declarationsToCheck: Set<KtNamedDeclaration>,
    allDeclarationsToMove: Set<KtNamedDeclaration>,
    targetDir: PsiDirectory,
    targetPkg: FqName,
    targetFileName: String,
    usages: List<MoveRenameUsageInfo>
): MultiMap<PsiElement, String> = allowAnalysisOnEdt {
    val (fakeTarget, oldToNewMap) = createCopyTarget(declarationsToCheck, targetDir, targetPkg, targetFileName)
    MultiMap<PsiElement, String>().apply {
        putAllValues(checkVisibilityConflictsForInternalUsages(allDeclarationsToMove, fakeTarget))
        putAllValues(checkVisibilityConflictForNonMovedUsages(allDeclarationsToMove, oldToNewMap, usages))
        putAllValues(checkModuleDependencyConflictsForInternalUsages(allDeclarationsToMove, fakeTarget))
        putAllValues(checkModuleDependencyConflictsForNonMovedUsages(allDeclarationsToMove, oldToNewMap, usages))
    }
}

/**
 * Creates a non-physical file that contains the moved elements with all references retargeted.
 * This non-physical file can be used to analyze for conflicts without modifying the file on the disk.
 */
fun createCopyTarget(
    declarationsToMove: Set<KtNamedDeclaration>,
    targetDir: PsiDirectory,
    targetPkg: FqName,
    targetFileName: String
): Pair<KtFile, Map<KtNamedDeclaration, KtNamedDeclaration>> {
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

    val fakeTargetFile = KtPsiFactory.contextual(targetDir).createFile(targetFileName, "package ${targetPkg.quoteIfNeeded()}\n")
    val oldToNewMap = declarationsToMove.moveInto(fakeTargetFile)
    val usageInfos = fakeTargetFile.collectOldToNewUsageInfos(oldToNewMap)
    usageInfos.forEach { (originalUsageInfo, copyUsageInfo) ->
        // Retarget all references to make sure all references are resolvable after moving
        val retargetResult = copyUsageInfo.retarget(copyUsageInfo.referencedElement as PsiNamedElement) as? KtElement ?: return@forEach
        val retargetReference = retargetResult.getCalleeExpressionIfAny() as? KtSimpleNameExpression ?: return@forEach
        // Attach physical usage info to the copied reference.
        // This will make it possible for the conflict checker to check whether a conflict exists before even calling the refactoring.
        retargetReference.internalUsageInfo = originalUsageInfo
    }
    fakeTargetFile.originalFile = declarationsToMove.firstOrNull()?.containingKtFile ?: error("Moved element is not in a Kotlin file")
    return fakeTargetFile to oldToNewMap
}


private fun PsiElement?.willBeMoved(declarationsToMove: Set<KtNamedDeclaration>): Boolean {
    return this != null && declarationsToMove.any { it.isAncestor(this, false) }
}

private fun MoveRenameUsageInfo.willNotBeMoved(declarationsToMove: Set<KtNamedDeclaration>): Boolean {
    return this !is K2MoveRenameUsageInfo || !element.willBeMoved(declarationsToMove)
}

private fun PsiElement.createVisibilityConflict(referencedDeclaration: PsiElement): Pair<PsiElement, String> {
    return this to KotlinBundle.message(
        "text.0.uses.1.which.will.be.inaccessible.after.move",
        RefactoringUIUtil.getDescription(getContainer(), false),
        RefactoringUIUtil.getDescription(referencedDeclaration, false)
    ).capitalizeAsciiOnly()
}

/**
 * Gets containing module of a [PsiElement] even if this [PsiElement] lives inside a copy target.
 */
private fun PsiElement.containingModule(): Module? {
    val containingFile = containingFile
    return if (containingFile is KtFile) {
        val targetDir = containingFile.analysisContext // target dir of copy file created
        if (targetDir != null) {
            ModuleUtilCore.findModuleForPsiElement(targetDir) // copy usage
        } else ModuleUtilCore.findModuleForPsiElement(this) // real usage
    } else ModuleUtilCore.findModuleForPsiElement(this) // real usage
}

/**
 * If visibility isn't there before the refactoring starts, we don't report it as a conflict.
 */
private fun MoveRenameUsageInfo.isVisibleBeforeMove(): Boolean {
    val declaration = upToDateReferencedElement as? PsiNamedElement ?: return false
    return declaration.isVisibleTo(element ?: return false)
}

private fun PsiNamedElement.isVisibleTo(usage: PsiElement): Boolean {
    return if (this is KtNamedDeclaration && usage is KtElement) {
        kotlinIsVisibleTo(usage)
    } else {
        lightIsVisibleTo(usage)
    }
}

context(KtAnalysisSession)
private fun KtNamedDeclaration.isVisibleTo(usage: PsiElement): Boolean {
    val file = (usage.containingFile as? KtFile)?.getFileSymbol() ?: return false
    val symbol = getSymbol()
    if (symbol !is KtSymbolWithVisibility) return false
    return isVisible(symbol, file, position = usage)
}

private fun KtNamedDeclaration.kotlinIsVisibleTo(usage: KtElement) = when {
    !isPhysical -> analyzeCopy(this, DanglingFileResolutionMode.PREFER_SELF) { isVisibleTo(usage) }
    !usage.isPhysical -> analyzeCopy(usage, DanglingFileResolutionMode.PREFER_SELF) { isVisibleTo(usage) }
    else -> analyze(this) { isVisibleTo(usage) }
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
private fun checkVisibilityConflictForNonMovedUsages(
    allDeclarationsToMove: Set<KtNamedDeclaration>,
    oldToNewMap: Map<KtNamedDeclaration, KtNamedDeclaration>,
    usages: List<MoveRenameUsageInfo>
): MultiMap<PsiElement, String> {
    return usages
        .filter { usage -> usage.willNotBeMoved(allDeclarationsToMove) && usage.isVisibleBeforeMove() }
        .mapNotNull { usage ->
            val usageElement = usage.element ?: return@mapNotNull null
            val referencedDeclaration = usage.upToDateReferencedElement as? KtNamedDeclaration ?: return@mapNotNull null
            val declarationCopy = containingCopyDecl(referencedDeclaration, oldToNewMap) ?: return@mapNotNull null
            val isVisible = declarationCopy.isVisibleTo(usageElement)
            if (!isVisible) usageElement.createVisibilityConflict(referencedDeclaration) else null
        }.toMultiMap()
}

/**
 * Check whether the moved internal usages are still visible towards their physical declaration.
 */
fun checkVisibilityConflictsForInternalUsages(
    allDeclarationsToMove: Set<KtNamedDeclaration>,
    fakeTarget: KtFile
): MultiMap<PsiElement, String> {
    return fakeTarget
        .collectDescendantsOfType<KtSimpleNameExpression>()
        .mapNotNull { refExprCopy -> (refExprCopy.internalUsageInfo ?: return@mapNotNull null) to refExprCopy }
        .filter { (usageInfo, _) -> !usageInfo.referencedElement.willBeMoved(allDeclarationsToMove) && usageInfo.isVisibleBeforeMove() }
        .mapNotNull { (usageInfo, refExprCopy) ->
            val referencedDeclaration = usageInfo.upToDateReferencedElement as? PsiNamedElement ?: return@mapNotNull null
            val isVisible = referencedDeclaration.isVisibleTo(refExprCopy)
            if (!isVisible) {
                val usageElement = usageInfo.element as? KtElement ?: return@mapNotNull null
                usageElement.createVisibilityConflict(referencedDeclaration)
            } else null
        }.toMultiMap()
}


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

private fun containingCopyDecl(
    declaration: KtNamedDeclaration,
    oldToNewMap: Map<KtNamedDeclaration, KtNamedDeclaration>
): KtNamedDeclaration? {
    return oldToNewMap[declaration] ?: containingCopyDecl(declaration.parentOfType<KtNamedDeclaration>() ?: return null, oldToNewMap)
}

private fun checkModuleDependencyConflictsForNonMovedUsages(
    allDeclarationsToMove: Set<KtNamedDeclaration>,
    oldToNewMap: Map<KtNamedDeclaration, KtNamedDeclaration>,
    usages: List<MoveRenameUsageInfo>
): MultiMap<PsiElement, String> {
    return usages
        .filter { usage -> usage.willNotBeMoved(allDeclarationsToMove) }
        .mapNotNull { usage ->
            val usageElement = usage.element ?: return@mapNotNull null
            val referencedDeclaration = usage.upToDateReferencedElement as? KtNamedDeclaration ?: return@mapNotNull null
            val declarationCopy = containingCopyDecl(referencedDeclaration, oldToNewMap) ?: return@mapNotNull null
            val targetModule = declarationCopy.containingModule() ?: return@mapNotNull null
            val resolveScope = usageElement.resolveScope
            if (resolveScope.isSearchInModuleContent(targetModule, false)) return@mapNotNull null
            usageElement.createAccessibilityConflictUnMoved(referencedDeclaration)
        }.toMultiMap()
}

fun checkModuleDependencyConflictsForInternalUsages(
    allDeclarationsToMove: Set<KtNamedDeclaration>,
    fakeTarget: KtFile
): MultiMap<PsiElement, String> {
    return fakeTarget
        .collectDescendantsOfType<KtSimpleNameExpression>()
        .mapNotNull { refExprCopy -> (refExprCopy.internalUsageInfo ?: return@mapNotNull null) to refExprCopy }
        .filter { (usageInfo, _) -> !usageInfo.referencedElement.willBeMoved(allDeclarationsToMove) }
        .mapNotNull { (usageInfo, refExprCopy) ->
            val usageElement = usageInfo.element ?: return@mapNotNull null
            val referencedDeclaration = usageInfo.upToDateReferencedElement as? PsiNamedElement ?: return@mapNotNull null
            analyzeCopy(refExprCopy, DanglingFileResolutionMode.PREFER_SELF) {
                if (refExprCopy.mainReference.resolveToSymbol() == null) {
                    val module = refExprCopy.containingModule() ?: return@analyzeCopy null
                    usageElement.createAccessibilityConflictInternal(referencedDeclaration, module)
                } else null
            }
        }.toMultiMap()
}