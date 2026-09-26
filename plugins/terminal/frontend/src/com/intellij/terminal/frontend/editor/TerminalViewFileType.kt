package com.intellij.terminal.frontend.editor

import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.terminal.TerminalIcons
import javax.swing.Icon

internal object TerminalViewFileType : FakeFileType() {
  override fun getName(): @NonNls String {
    return "Terminal View"
  }

  override fun getDescription(): @NlsContexts.Label String {
    return "$name Fake File Type" //NON-NLS
  }

  override fun getIcon(): Icon {
    return TerminalIcons.OpenTerminal_13x13
  }

  override fun isMyFileType(file: VirtualFile): Boolean {
    return file is TerminalViewVirtualFile
  }
}