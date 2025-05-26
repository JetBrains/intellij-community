// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.pullUp

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringBundle
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

@ApiStatus.Internal
fun checkPullUpConflicts(
  project: Project,
  sourceClass: KtClassOrObject,
  targetClass: PsiNamedElement,
  memberInfos: List<KotlinMemberInfo>,
  onShowConflicts: () -> Unit = {},
  onAccept: () -> Unit
) {
    val conflicts = MultiMap<PsiElement, String>()

    val conflictsCollected = runProcessWithProgressSynchronously(RefactoringBundle.message("detecting.possible.conflicts"), project) {
        runReadAction {
            conflicts.putAllValues(KotlinPullUpConflictSearcher.getInstance().collectConflicts(sourceClass, targetClass, memberInfos))
        }
    }

    if (conflictsCollected) {
        project.checkConflictsInteractively(conflicts, onShowConflicts, onAccept)
    } else {
        onShowConflicts()
    }
}

@ApiStatus.Internal
fun willBeUsedInSourceClass(
    member: PsiElement,
    sourceClass: KtClassOrObject,
    membersToMove: Collection<KtNamedDeclaration>
): Boolean {
    return !ReferencesSearch
        .search(member, LocalSearchScope(sourceClass), false)
        .asIterable()
        .all { it.element.willBeMoved(membersToMove) }
}

@ApiStatus.Internal
fun PsiElement?.willBeMoved(declarationsToMove: Iterable<KtNamedDeclaration>): Boolean {
    return this != null && declarationsToMove.any { it.isAncestor(this, false) }
}

private fun runProcessWithProgressSynchronously(
  progressTitle: @NlsContexts.ProgressTitle String,
  project: Project?,
  process: Runnable,
): Boolean = ProgressManager.getInstance().runProcessWithProgressSynchronously(process, progressTitle, true, project)