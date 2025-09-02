package com.intellij.terminal.frontend

import com.intellij.psi.AbstractFileViewProvider
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.terminal.block.reworked.lang.TerminalOutputFileType

/**
 * Special virtual file implementation for the Terminal output document.
 * It is required to override [shouldSkipEventSystem] and return true from it.
 * So, [com.intellij.psi.PsiDocumentManager] won't try to update the PSI of our document on every change.
 * And we will be able to do it manually in a more performant way. See [updatePsiOnOutputModelChange].
 */
internal class TerminalOutputVirtualFile : LightVirtualFile("terminal_output", TerminalOutputFileType, "") {
  init {
    putUserData(AbstractFileViewProvider.FREE_THREADED, true)
  }

  override fun shouldSkipEventSystem(): Boolean {
    return true
  }
}