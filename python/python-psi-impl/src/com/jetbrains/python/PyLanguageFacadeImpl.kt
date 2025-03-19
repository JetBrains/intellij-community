package com.jetbrains.python

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyPsiFacade

class PyLanguageFacadeImpl : PyLanguageFacade() {
  override fun forLanguage(psiElement: PsiElement): LanguageLevel {
    return PyPsiFacade.getInstance(psiElement.project).getLanguageLevel(psiElement)
  }
}