// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.search.declarationsSearch.HierarchySearchRequest
import org.jetbrains.kotlin.idea.search.declarationsSearch.searchOverriders
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import java.util.*


internal fun checkFunctionOverriddenInSubclassConflict(
    declarationsToMove: Iterable<KtNamedDeclaration>
): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()

    for (declarationToMove in declarationsToMove) {
        if (declarationToMove.containingClass() == null) continue
        if (HierarchySearchRequest(declarationToMove, declarationToMove.useScope, false).searchOverriders().any()) {
            val description = RefactoringUIUtil.getDescription(declarationToMove, false)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            conflicts.putValue(declarationToMove, KotlinBundle.message("0.is.overridden.by.declaration.s.in.a.subclass", description))
        }
    }

    return conflicts
}