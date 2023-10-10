// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.*
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

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

fun KtClass.isOpen(): Boolean = hasModifier(KtTokens.OPEN_KEYWORD) || this.isAbstract() || this.isInterfaceClass() || this.isSealed()

fun PsiElement.isInterfaceClass(): Boolean = when (this) {
    is KtClass -> isInterface()
    is PsiClass -> isInterface
    is KtPsiClassWrapper -> psiClass.isInterface
    else -> false
}

fun KtDeclaration.isAbstract(): Boolean = when {
    hasModifier(KtTokens.ABSTRACT_KEYWORD) -> true
    containingClassOrObject?.isInterfaceClass() != true -> false
    this is KtProperty -> initializer == null && delegate == null && accessors.isEmpty()
    this is KtNamedFunction -> !hasBody()
    else -> false
}

