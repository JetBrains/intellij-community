// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProductionOrTest
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.utils.toVisibility
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import org.jetbrains.kotlin.types.Variance

/**
 * Find all conflicts when moving elements for a multi-file move.
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
 * Find all conflicts when moving elements for a multi-file move.
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
            putAllValues(findAllMoveConflicts(declarations.toSet(), allDeclarationsToMove, targetDir, targetPkg, externalUsages))
        }
    }
}

/**
 * Find all conflicts when moving elements.
 * @param topLevelDeclarationsToMove the set of declarations to move, they must all be moved from the same containing file.
 * @param allDeclarationsToMove all declarations that will be moved.
 */
internal fun findAllMoveConflicts(
    topLevelDeclarationsToMove: Collection<KtNamedDeclaration>,
    allDeclarationsToMove: Collection<KtNamedDeclaration>,
    targetDir: PsiDirectory,
    targetPkg: FqName,
    usages: List<MoveRenameUsageInfo>
): MultiMap<PsiElement, String> {
    val targetIdeaModule = targetDir.module ?: return MultiMap.empty()
    val targetKaModule = targetIdeaModule.toKaSourceModuleForProductionOrTest() ?: return MultiMap.empty()
    return MultiMap<PsiElement, String>().apply {
        putAllValues(checkMoveExpectedDeclarationIntoPlatformCode(topLevelDeclarationsToMove, targetKaModule))
        putAllValues(checkMoveActualDeclarationIntoCommonModule(topLevelDeclarationsToMove, targetKaModule))
        putAllValues(checkVisibilityConflictsForInternalUsages(topLevelDeclarationsToMove, allDeclarationsToMove, targetPkg, targetDir))
        putAllValues(checkVisibilityConflictForNonMovedUsages(allDeclarationsToMove, usages, targetDir))
        putAllValues(checkModuleDependencyConflictsForInternalUsages(topLevelDeclarationsToMove, allDeclarationsToMove, targetDir))
        putAllValues(checkModuleDependencyConflictsForNonMovedUsages(allDeclarationsToMove, usages, targetDir))
        putAllValues(checkSealedClassesConflict(allDeclarationsToMove, targetPkg, targetKaModule, targetIdeaModule))
        putAllValues(checkNameClashConflicts(allDeclarationsToMove, targetPkg, targetKaModule))
    }
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
@NlsSafe
internal fun KaSymbol.renderForConflict(): String {
    return when (this) {
        is KaClassSymbol -> {
            val keywords = when (classKind) {
                KaClassKind.CLASS -> listOf(KtTokens.CLASS_KEYWORD)
                KaClassKind.ENUM_CLASS -> listOf(KtTokens.ENUM_KEYWORD, KtTokens.CLASS_KEYWORD)
                KaClassKind.ANNOTATION_CLASS -> listOf(KtTokens.ANNOTATION_KEYWORD, KtTokens.CLASS_KEYWORD)
                KaClassKind.OBJECT -> listOf(KtTokens.OBJECT_KEYWORD)
                KaClassKind.COMPANION_OBJECT -> listOf(KtTokens.COMPANION_KEYWORD, KtTokens.OBJECT_KEYWORD)
                KaClassKind.INTERFACE -> listOf(KtTokens.INTERFACE_KEYWORD)
                KaClassKind.ANONYMOUS_OBJECT -> emptyList()
            }
            @NlsSafe val text = keywords.joinToString(" ", postfix = " ") + defaultType.render(position = Variance.INVARIANT)
            text
        }
        is KaFunctionSymbol -> {
            KotlinBundle.message("text.function.in.ticks.0", render(KaDeclarationRendererForSource.WITH_SHORT_NAMES))
        }
        is KaPropertySymbol -> {
            KotlinBundle.message("text.property.in.ticks.0", render(KaDeclarationRendererForSource.WITH_SHORT_NAMES))
        }
        is KaPackageSymbol -> {
            @NlsSafe val text = fqName.asString()
            text
        }
        else -> {
            ""
        }
    }
}


internal fun PsiElement?.willBeMoved(declarationsToMove: Iterable<KtNamedDeclaration>): Boolean {
    return this != null && declarationsToMove.any { it.isAncestor(this, false) }
}

internal fun MoveRenameUsageInfo.willNotBeMoved(declarationsToMove: Iterable<KtNamedDeclaration>): Boolean {
    return this !is K2MoveRenameUsageInfo || !element.willBeMoved(declarationsToMove)
}

internal val KtNamedDeclaration.isInternal get() = visibilityModifierTypeOrDefault().toVisibility() == Visibilities.Internal

internal fun tryFindConflict(findConflict: () -> Pair<PsiElement, String>?): Pair<PsiElement, String>? {
    return try {
        findConflict()
    } catch (e: Exception) {
        if (e is ControlFlowException) throw e
        fileLogger().error(e)
        null
    }
}
