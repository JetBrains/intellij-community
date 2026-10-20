package com.jetbrains.python

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
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
    /**
     * The registered facade, or a default facade when no Python PSI is loaded.
     * JetBrains Light before the smart mode is such a process.
     */
    @JvmStatic
    val INSTANCE: PyLanguageFacade
      get() = ApplicationManager.getApplication().serviceOrNull<PyLanguageFacade>() ?: PyDefaultLanguageFacade
  }
}

private object PyDefaultLanguageFacade : PyLanguageFacade() {
  override fun getEffectiveLanguageLevel(psiElement: PsiElement): LanguageLevel = LanguageLevel.getDefault()

  override fun getEffectiveLanguageLevel(project: Project, virtualFile: VirtualFile): LanguageLevel = LanguageLevel.getDefault()

  override fun setEffectiveLanguageLevel(virtualFile: VirtualFile, languageLevel: LanguageLevel?) {
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
