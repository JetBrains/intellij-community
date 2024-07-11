// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.fileLogger
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
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaSymbolWithVisibility
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.codeinsight.utils.toVisibility
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveRenameUsageInfo.Companion.internalUsageInfo
import org.jetbrains.kotlin.idea.refactoring.getContainer
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

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
    val (fakeTarget, oldToNewMap) = createCopyTarget(declarationsToCheck, targetDir, targetPkg, targetFileName)
    return MultiMap<PsiElement, String>().apply {
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
        if (!originalUsageInfo.isUpdatable(oldToNewMap.values.toList())) {
            (copyUsageInfo.reference?.element as? KtReferenceExpression)?.internalUsageInfo = originalUsageInfo
            return@forEach
        }

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


private fun PsiElement?.willBeMoved(declarationsToMove: Iterable<KtNamedDeclaration>): Boolean {
    return this != null && declarationsToMove.any { it.isAncestor(this, false) }
}

private fun MoveRenameUsageInfo.willNotBeMoved(declarationsToMove: Iterable<KtNamedDeclaration>): Boolean {
    return this !is K2MoveRenameUsageInfo || !element.willBeMoved(declarationsToMove)
}

private val KtNamedDeclaration.isInternal get() = visibilityModifierTypeOrDefault().toVisibility() == Visibilities.Internal

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
    return if (this is KtNamedDeclaration && usage is KtElement) {
        kotlinIsVisibleTo(usage)
    } else {
        lightIsVisibleTo(usage)
    }
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun KtNamedDeclaration.isVisibleTo(usage: PsiElement): Boolean {
    val file = (usage.containingFile as? KtFile)?.symbol ?: return false
    val symbol = symbol
    if (symbol !is KaSymbolWithVisibility) return false
    return isVisible(symbol, file, position = usage)
}

private fun KtNamedDeclaration.kotlinIsVisibleTo(usage: KtElement) = when {
    !isPhysical -> analyzeCopy(this, KaDanglingFileResolutionMode.PREFER_SELF) { isVisibleTo(usage) }
    !usage.isPhysical -> analyzeCopy(usage, KaDanglingFileResolutionMode.PREFER_SELF) { isVisibleTo(usage) }
    else -> analyze(this) { isVisibleTo(usage) }
}

private fun PsiNamedElement.lightIsVisibleTo(usage: PsiElement): Boolean {
    val declarations = if (this is KtNamedDeclaration) toLightElements() else listOf(this)
    return declarations.all { lightDecl ->
        if (lightDecl !is PsiMember) return@all false
        JavaResolveUtil.isAccessible(lightDecl, lightDecl.containingClass, lightDecl.modifierList, usage, null, null)
    }
}

private fun tryFindConflict(findConflict: () -> Pair<PsiElement, String>?): Pair<PsiElement, String>? {
    return try {
        findConflict()
    } catch (e: Exception) {
        if (e is ControlFlowException) throw e
        fileLogger().error(e)
        null
    }
}

/**
 * Check whether the moved external usages are still visible towards their non-physical declaration.
 */
private fun checkVisibilityConflictForNonMovedUsages(
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
fun KtNamedDeclaration.isMemberThatCanBeSkipped(): Boolean {
    if (containingClass() == null) return false
    analyze(this) {
        val symbol = symbol as? KaSymbolWithVisibility ?: return false
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