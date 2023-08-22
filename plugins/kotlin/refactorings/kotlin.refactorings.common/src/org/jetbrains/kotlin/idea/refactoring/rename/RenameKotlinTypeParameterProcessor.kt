// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.refactoring.conflicts.checkRedeclarationConflicts
import org.jetbrains.kotlin.psi.KtTypeParameter

class RenameKotlinTypeParameterProcessor : RenameKotlinPsiProcessor() {
  override fun canProcessElement(element: PsiElement) = element is KtTypeParameter

  override fun findCollisions(
    element: PsiElement,
    newName: String,
    allRenames: MutableMap<out PsiElement, String>,
    result: MutableList<UsageInfo>
  ) {
    val declaration = element as? KtTypeParameter ?: return
    checkRedeclarationConflicts(declaration, newName, result)
  }
}