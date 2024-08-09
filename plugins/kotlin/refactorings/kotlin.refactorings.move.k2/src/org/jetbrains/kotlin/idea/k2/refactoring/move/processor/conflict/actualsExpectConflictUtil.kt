// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor.conflict

import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.psi.isEffectivelyActual
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.psi.KtNamedDeclaration

internal fun checkMoveExpectedDeclarationIntoPlatformCode(
    declarationsToMove: Iterable<KtNamedDeclaration>,
    targetModule: KaModule
): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()
    if (targetModule.targetPlatform.isCommon()) return conflicts
    declarationsToMove.forEach { declaration ->
        if (declaration.isExpectDeclaration()) {
            val descr = RefactoringUIUtil.getDescription(declaration, false)
            conflicts.putValue(declaration, KotlinBundle.message("text.expected.moved.to.platform.modules.target", descr))
        }
    }
    return conflicts
}

internal fun checkMoveActualDeclarationIntoCommonModule(
    declarationsToMove: Iterable<KtNamedDeclaration>,
    targetModule: KaModule
): MultiMap<PsiElement, String> {
    val conflicts = MultiMap<PsiElement, String>()
    if (!targetModule.targetPlatform.isCommon()) return conflicts
    declarationsToMove.forEach { declaration ->
        if (declaration.isEffectivelyActual(false)) {
            val descr = RefactoringUIUtil.getDescription(declaration, false)
            conflicts.putValue(declaration, KotlinBundle.message("text.actual.moved.to.common.modules.target", descr))
        }
    }
    return conflicts
}