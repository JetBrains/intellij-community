package com.jetbrains.python

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher

class PyLanguageFacadeImpl : PyLanguageFacade() {
  override fun forLanguage(psiElement: PsiElement): LanguageLevel {
    if (psiElement is PsiDirectory) {
      return PythonLanguageLevelPusher.getLanguageLevelForVirtualFile(psiElement.getProject(), psiElement.getVirtualFile())
    }

    val containingFile = psiElement.getContainingFile()
    if (containingFile is PyFile) {
      return containingFile.getLanguageLevel()
    }

    return LanguageLevel.getDefault()
  }
}