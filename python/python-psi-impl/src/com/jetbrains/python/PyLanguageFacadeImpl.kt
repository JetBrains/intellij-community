package com.jetbrains.python

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher

class PyLanguageFacadeImpl : PyLanguageFacadeBase() {
  override fun doGetEffectiveLanguageLevel(project: Project, virtualFile: VirtualFile): LanguageLevel {
    return PythonLanguageLevelPusher.getEffectiveLanguageLevel(project, virtualFile)
  }

  override fun setEffectiveLanguageLevel(virtualFile: VirtualFile, languageLevel: LanguageLevel?) {
    PythonLanguageLevelPusher.specifyFileLanguageLevel(virtualFile, languageLevel)
  }
}