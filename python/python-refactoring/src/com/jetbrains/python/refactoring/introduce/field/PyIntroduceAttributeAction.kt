// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.introduce.field

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BasePlatformRefactoringAction
import com.intellij.refactoring.actions.ExtractSuperActionBase
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.refactoring.PyRefactoringProvider

/**
 * Python flavor of the platform "Introduce Field" action. In Python class members are attributes, so the action is
 * labeled "Introduce Attribute" (see [PyRefactoringProvider] which no longer exposes the platform introduce-field handler).
 */
class PyIntroduceAttributeAction : BasePlatformRefactoringAction() {
  override fun getRefactoringHandler(provider: RefactoringSupportProvider): RefactoringActionHandler? =
    if (provider is PyRefactoringProvider) PyIntroduceFieldHandler() else null

  override fun isAvailableInEditorOnly(): Boolean = true

  override fun isEnabledOnElements(elements: Array<out PsiElement>): Boolean =
    elements.all { it is PyElement }

  override fun update(e: AnActionEvent) {
    super.update(e)
    ExtractSuperActionBase.removeFirstWordInMainMenu(this, e)
  }
}
