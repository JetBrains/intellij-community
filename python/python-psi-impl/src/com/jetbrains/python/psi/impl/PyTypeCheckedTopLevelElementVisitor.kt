package com.jetbrains.python.psi.impl

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyComprehensionElement
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class PyTypeCheckedTopLevelElementVisitor(languageLevel: LanguageLevel) : PyTypeCheckedElementVisitor(languageLevel) {
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

  override fun visitPyComprehensionElement(node: PyComprehensionElement) {
    if (PyUtil.isOwnScopeComprehension(node)) {
      checkAddElement(node)
    }
    else {
      super.visitPyComprehensionElement(node)
    }
  }

  protected abstract fun checkAddElement(node: PsiElement?)
}