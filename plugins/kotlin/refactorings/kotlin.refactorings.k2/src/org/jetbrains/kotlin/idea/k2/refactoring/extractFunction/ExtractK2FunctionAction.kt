// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.extractFunction

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BasePlatformRefactoringAction
import com.intellij.refactoring.actions.ExtractSuperActionBase
import org.jetbrains.kotlin.idea.k2.refactoring.KotlinFirRefactoringSupportProvider
import org.jetbrains.kotlin.psi.KtElement

class ExtractK2FunctionAction : BasePlatformRefactoringAction() {
    override fun getRefactoringHandler(provider: RefactoringSupportProvider): RefactoringActionHandler? =
        if (provider is KotlinFirRefactoringSupportProvider) KotlinFirExtractFunctionHandler(Registry.`is`("k2.extract.function.scope.chooser", true)) else null

    override fun isAvailableInEditorOnly(): Boolean {
        return true
    }

    override fun isEnabledOnElements(elements: Array<out PsiElement>): Boolean =
        elements.all { it is KtElement }

    override fun update(e: AnActionEvent) {
        super.update(e)
        ExtractSuperActionBase.removeFirstWordInMainMenu(this, e)
    }
}