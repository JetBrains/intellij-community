// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.rename

import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameInputValidator
import com.intellij.util.ProcessingContext

class PropertyKeyRenameInputValidator: RenameInputValidator {
  private val myPattern: ElementPattern<out PsiElement?> = PlatformPatterns.psiElement(PropertyKeyImpl::class.java)

  override fun getPattern(): ElementPattern<out PsiElement?> = myPattern

  override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext): Boolean = !newName.contains(' ')
}