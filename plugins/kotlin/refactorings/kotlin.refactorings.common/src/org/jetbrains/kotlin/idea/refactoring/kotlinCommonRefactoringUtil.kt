// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.idea.base.projectStructure.matches

fun PsiElement.canRefactorElement(): Boolean {
  return when {
    !isValid -> false
    this is PsiPackage ->
      directories.any { it.canRefactorElement() }
    this is KtElement || (this is PsiMember && language == JavaLanguage.INSTANCE) || this is PsiDirectory ->
      RootKindFilter.projectSources.copy(includeScriptsOutsideSourceRoots = true).matches(this)
    else -> false
  }
}
