// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

fun analyzeConflictsInFile(
    file: KtFile,
    usages: Collection<UsageInfo>,
    moveTarget: KotlinMoveTarget,
    allElementsToMove: Collection<PsiElement>,
    onUsageUpdate: (List<UsageInfo>) -> Unit
): MultiMap<PsiElement, String> {
    val elementsToMove = file.declarations
    if (elementsToMove.isEmpty()) return MultiMap.empty()

    val (internalUsages, externalUsages) = usages.partition { it is KotlinMoveRenameUsage && it.isInternal }
    val internalUsageSet = internalUsages.toMutableSet()
    val externalUsageSet = externalUsages.toMutableSet()

    val moveCheckerInfo = KotlinMoveConflictCheckerInfo(
        file.project,
        elementsToMove,
        moveTarget,
        elementsToMove.first(),
        allElementsToMove
    )
    val conflicts = KotlinMoveConflictCheckerSupport.getInstance().checkAllConflicts(moveCheckerInfo, internalUsageSet, externalUsageSet)

    if (externalUsageSet.size != externalUsages.size || internalUsageSet.size != internalUsages.size) {
        onUsageUpdate((externalUsageSet + internalUsageSet).toList())
    }
    return conflicts
}

class KotlinMoveConflictCheckerInfo(
    val project: Project,
    val elementsToMove: Collection<KtElement>,
    val moveTarget: KotlinMoveTarget,
    val context: KtElement,
    allElementsToMove: Collection<PsiElement>? = null
) {
    val allElementsToMove = allElementsToMove ?: elementsToMove

    fun isToBeMoved(element: PsiElement): Boolean = allElementsToMove.any { it.isAncestor(element, false) }
}

interface KotlinMoveConflictCheckerSupport {
    fun checkAllConflicts(
        moveCheckerInfo: KotlinMoveConflictCheckerInfo,
        internalUsages: MutableSet<UsageInfo>,
        externalUsages: MutableSet<UsageInfo>
    ) = MultiMap<PsiElement, String>().apply {
        putAllValues(checkNameClashes(moveCheckerInfo))
        putAllValues(checkVisibilityInDeclarations(moveCheckerInfo))
        putAllValues(checkInternalMemberUsages(moveCheckerInfo))
        putAllValues(checkSealedClassMove(moveCheckerInfo))
        putAllValues(checkModuleConflictsInDeclarations(moveCheckerInfo, internalUsages))
        putAllValues(checkModuleConflictsInUsages(moveCheckerInfo, externalUsages))
        putAllValues(checkVisibilityInUsages(moveCheckerInfo, externalUsages))
    }

    fun checkNameClashes(moveCheckerInfo: KotlinMoveConflictCheckerInfo): MultiMap<PsiElement, String>

    fun checkVisibilityInDeclarations(moveCheckerInfo: KotlinMoveConflictCheckerInfo): MultiMap<PsiElement, String>

    fun checkVisibilityInUsages(moveCheckerInfo: KotlinMoveConflictCheckerInfo, usages: Collection<UsageInfo>): MultiMap<PsiElement, String>

    fun checkInternalMemberUsages(moveCheckerInfo: KotlinMoveConflictCheckerInfo): MultiMap<PsiElement, String>

    fun checkSealedClassMove(moveCheckerInfo: KotlinMoveConflictCheckerInfo): MultiMap<PsiElement, String>

    fun checkModuleConflictsInDeclarations(
        moveCheckerInfo: KotlinMoveConflictCheckerInfo,
        internalUsages: MutableSet<UsageInfo>
    ): MultiMap<PsiElement, String>

    fun checkModuleConflictsInUsages(
        moveCheckerInfo: KotlinMoveConflictCheckerInfo,
        externalUsages: MutableSet<UsageInfo>
    ): MultiMap<PsiElement, String>

    companion object {
        @JvmStatic
        fun getInstance(): KotlinMoveConflictCheckerSupport = service()
    }
}