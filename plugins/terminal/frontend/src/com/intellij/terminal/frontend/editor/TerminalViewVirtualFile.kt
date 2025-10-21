package com.intellij.terminal.frontend.editor

import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.testFramework.LightVirtualFile

internal class TerminalViewVirtualFile(
  val terminalView: TerminalView,
) : LightVirtualFile(terminalView.title.buildTitle()) {
  init {
    fileType = TerminalViewFileType
    isWritable = true // to be able to rename the file
  }
}