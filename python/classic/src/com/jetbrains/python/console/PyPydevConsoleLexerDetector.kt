// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.highlighting.PyConsoleLexerDetector

/**
 * Detects a file that belongs to the pydev console.
 */
internal class PyPydevConsoleLexerDetector : PyConsoleLexerDetector {
  override fun isConsoleFile(project: Project, file: VirtualFile): Boolean {
    if (file is VirtualFileWindow) return false
    return runReadActionBlocking {
      val psiFile = PsiManager.getInstance(project).findFile(file)
      psiFile != null && PythonRuntimeService.getInstance().isInPydevConsole(psiFile)
    }
  }
}
