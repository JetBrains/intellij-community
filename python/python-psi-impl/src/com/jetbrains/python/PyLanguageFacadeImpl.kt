// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyLanguageFacadeImpl : PyLanguageFacadeBase() {
  override fun doGetEffectiveLanguageLevel(project: Project, virtualFile: VirtualFile): LanguageLevel {
    return PythonLanguageLevelPusher.getEffectiveLanguageLevel(project, virtualFile)
  }

  override fun setEffectiveLanguageLevel(virtualFile: VirtualFile, languageLevel: LanguageLevel?) {
    PythonLanguageLevelPusher.specifyFileLanguageLevel(virtualFile, languageLevel)
  }
}