// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class PyVersionAwareTopLevelElementVisitor(languageLevel: LanguageLevel) : PyVersionAwareElementVisitor(languageLevel) {
  override fun visitPyElement(node: PyElement) {
    super.visitPyElement(node)
    checkAddElement(node)
  }

  override fun visitPyClass(node: PyClass) {
    checkAddElement(node) // do not recurse into classes
  }

  override fun visitPyFunction(node: PyFunction) {
    checkAddElement(node) // do not recurse into functions
  }

  protected abstract fun checkAddElement(node: PsiElement?)
}
