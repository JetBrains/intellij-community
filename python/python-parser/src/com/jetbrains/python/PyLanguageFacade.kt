package com.jetbrains.python

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.LanguageLevel

abstract class PyLanguageFacade {
  abstract fun forLanguage(psiElement: PsiElement): LanguageLevel

  companion object {
    val INSTANCE: PyLanguageFacade
      get() =  ApplicationManager.getApplication().service<PyLanguageFacade>()
  }
}