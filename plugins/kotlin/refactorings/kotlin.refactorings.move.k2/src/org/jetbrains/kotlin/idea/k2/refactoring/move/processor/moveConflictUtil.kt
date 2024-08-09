// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.base.projectStructure.productionOrTestSourceModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKaModule
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.codeinsight.utils.toVisibility
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.internalUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.updatableUsageInfo
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault

/**
 * Find all conflicts when moving elements for a multi file move.
 */
internal fun findAllMoveConflicts(
    filesToMove: Iterable<KtFile>,
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
    filesToMove: Iterable<KtFile>,
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
internal fun findAllMoveConflicts(
    declarationsToCheck: Iterable<KtNamedDeclaration>,
    allDeclarationsToMove: Iterable<KtNamedDeclaration>,
    targetDir: PsiDirectory,
    targetPkg: FqName,
    targetFileName: String,
    usages: List<MoveRenameUsageInfo>
): MultiMap<PsiElement, String> {
    val targetModule = targetDir.containingModule()?.productionOrTestSourceModuleInfo?.toKaModule() ?: return MultiMap.empty()
    val (fakeTarget, oldToNewMap) = createCopyTarget(declarationsToCheck, targetDir, targetPkg, targetFileName)
    return MultiMap<PsiElement, String>().apply {
        putAllValues(checkMoveExpectedDeclarationIntoPlatformCode(declarationsToCheck, targetModule))
        putAllValues(checkMoveActualDeclarationIntoCommonModule(declarationsToCheck, targetModule))
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
    declarationsToMove: Iterable<KtNamedDeclaration>,
    targetDir: PsiDirectory,
    targetPkg: FqName,
    targetFileName: String
): Pair<KtFile, Map<KtNamedDeclaration, KtNamedDeclaration>> {
    /** Collects physical to non-physical usage-infos. */
    fun KtFile.collectOldToNewUsageInfos(oldToNewMap: Map<KtNamedDeclaration, KtNamedDeclaration>): List<Pair<K2MoveRenameUsageInfo, K2MoveRenameUsageInfo>> {
        return collectDescendantsOfType<KtReferenceExpression>().mapNotNull { element ->
            val usageInfo = element.internalUsageInfo
            val referencedElement = (usageInfo as? K2MoveRenameUsageInfo.Source)?.referencedElement ?: return@mapNotNull null
            val newReferencedElement = oldToNewMap[referencedElement] ?: referencedElement
            if (!newReferencedElement.isValid || newReferencedElement !is PsiNamedElement) return@mapNotNull null
            usageInfo to usageInfo.refresh(element, newReferencedElement)
        }
    }

    val fakeTargetFile = KtPsiFactory.contextual(targetDir).createFile(targetFileName, "package ${targetPkg.quoteIfNeeded()}\n")
    val oldToNewMap = declarationsToMove.moveInto(fakeTargetFile)
    val usageInfos = fakeTargetFile.collectOldToNewUsageInfos(oldToNewMap)
    usageInfos.forEach { (originalUsageInfo, copyUsageInfo) ->
        if ((originalUsageInfo.element as KtElement).updatableUsageInfo == null) return@forEach // if not updatable, skip
        // Retarget all references to make sure all references are resolvable after moving
        val retargetResult = copyUsageInfo.retarget(copyUsageInfo.referencedElement as PsiNamedElement) as? KtElement ?: return@forEach
        val retargetReference = retargetResult.getQualifiedElementSelector() as? KtReferenceExpression ?: return@forEach
        // Attach physical usage info to the copied reference.
        // This will make it possible for the conflict checker to check whether a conflict exists before even calling the refactoring.
        retargetReference.updatableUsageInfo = originalUsageInfo
    }
    fakeTargetFile.originalFile = declarationsToMove.firstOrNull()?.containingKtFile ?: error("Moved element is not in a Kotlin file")
    return fakeTargetFile to oldToNewMap
}


internal fun PsiElement?.willBeMoved(declarationsToMove: Iterable<KtNamedDeclaration>): Boolean {
    return this != null && declarationsToMove.any { it.isAncestor(this, false) }
}

internal fun MoveRenameUsageInfo.willNotBeMoved(declarationsToMove: Iterable<KtNamedDeclaration>): Boolean {
    return this !is K2MoveRenameUsageInfo || !element.willBeMoved(declarationsToMove)
}

internal val KtNamedDeclaration.isInternal get() = visibilityModifierTypeOrDefault().toVisibility() == Visibilities.Internal

/**
 * Gets containing module of a [PsiElement] even if this [PsiElement] lives inside a copy target.
 */
internal fun PsiElement.containingModule(): Module? {
    val containingFile = containingFile
    return if (containingFile is KtFile) {
        val targetDir = containingFile.analysisContext // target dir of copy file created
        if (targetDir != null) {
            ModuleUtilCore.findModuleForPsiElement(targetDir) // copy usage
        } else ModuleUtilCore.findModuleForPsiElement(this) // real usage
    } else ModuleUtilCore.findModuleForPsiElement(this) // real usage
}

internal fun tryFindConflict(findConflict: () -> Pair<PsiElement, String>?): Pair<PsiElement, String>? {
    return try {
        findConflict()
    } catch (e: Exception) {
        if (e is ControlFlowException) throw e
        fileLogger().error(e)
        null
    }
}

internal fun containingCopyDecl(
    declaration: KtNamedDeclaration,
    oldToNewMap: Map<KtNamedDeclaration, KtNamedDeclaration>
): KtNamedDeclaration? {
    return oldToNewMap[declaration] ?: containingCopyDecl(declaration.parentOfType<KtNamedDeclaration>() ?: return null, oldToNewMap)
}

