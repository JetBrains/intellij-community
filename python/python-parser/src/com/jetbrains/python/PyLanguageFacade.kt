package com.jetbrains.python

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class PyLanguageFacade {
  abstract fun getEffectiveLanguageLevel(psiElement: PsiElement): LanguageLevel
  abstract fun getEffectiveLanguageLevel(project: Project, virtualFile: VirtualFile): LanguageLevel
  abstract fun setEffectiveLanguageLevel(virtualFile: VirtualFile, languageLevel: LanguageLevel?)

  companion object {
    @JvmStatic
    val INSTANCE: PyLanguageFacade
      get() = ApplicationManager.getApplication().service<PyLanguageFacade>()
  }
}

@ApiStatus.Internal
fun getEffectiveLanguageLevel(file: PsiFile): LanguageLevel {
  var curFile = file
  while (curFile != curFile.getOriginalFile()) {
    curFile = curFile.getOriginalFile()
  }
  val virtualFile = curFile.getVirtualFile() ?: curFile.getViewProvider().getVirtualFile()
  return PyLanguageFacade.INSTANCE.getEffectiveLanguageLevel(curFile.project, virtualFile)
}
