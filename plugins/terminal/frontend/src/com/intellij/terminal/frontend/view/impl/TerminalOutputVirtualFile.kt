package com.intellij.terminal.frontend.view.impl

import com.intellij.psi.AbstractFileViewProvider
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.terminal.block.reworked.lang.TerminalOutputFileType

/**
 * Special virtual file implementation for the Terminal output document.
 * It is required to override [shouldSkipEventSystem] since we have our own custom PSI implementation.
 * Also, we need to set [AbstractFileViewProvider.FREE_THREADED]
 * to make [com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl] create a non-AWT thread document for this file.
 */
internal class TerminalOutputVirtualFile : LightVirtualFile("terminal_output", TerminalOutputFileType, "") {
  init {
    putUserData(AbstractFileViewProvider.FREE_THREADED, true)
  }

  override fun shouldSkipEventSystem(): Boolean {
    return true
  }
}