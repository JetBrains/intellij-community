package com.jetbrains.python

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.jetbrains.python.ast.PyAstFile
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class PyLanguageFacadeBase : PyLanguageFacade() {
  final override fun getEffectiveLanguageLevel(psiElement: PsiElement): LanguageLevel {
    if (psiElement is PsiDirectory) {
      return getEffectiveLanguageLevel(psiElement.getProject(), psiElement.getVirtualFile())
    }

    val containingFile = psiElement.getContainingFile()
    if (containingFile is PyAstFile) {
      return containingFile.languageLevel
    }

    return LanguageLevel.getDefault()
  }

  final override fun getEffectiveLanguageLevel(project: Project, virtualFile: VirtualFile): LanguageLevel {
    var file = virtualFile
    if (file is VirtualFileWindow) {
      file = file.getDelegate()
    }
    file = BackedVirtualFile.getOriginFileIfBacked(file)
    return doGetEffectiveLanguageLevel(project, file)
  }

  protected abstract fun doGetEffectiveLanguageLevel(project: Project, virtualFile: VirtualFile): LanguageLevel
}
